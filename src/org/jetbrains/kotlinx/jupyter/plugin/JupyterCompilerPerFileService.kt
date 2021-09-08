package org.jetbrains.kotlinx.jupyter.plugin

import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.nullize
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKotlinPluginScriptClassGetter
import org.jetbrains.kotlinx.jupyter.plugin.util.allJarsFromDir
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterSource
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.withUpdatedClasspath

/**
 * This service is created for every Kotlin notebook file
 * and provides a scripting support for injected Kotlin snippets
 * including magics handling, storing dependencies and a list
 * of compiled scripts.
 *
 * @property virtualFile File with Kotlin notebook
 * @property projectService Project service that owns this sub-service
 */
class JupyterCompilerPerFileService(
    @Suppress("unused") private val virtualFile: NotebookVirtualFile,
    private val projectService: JupyterCompilerService,
) : Disposable {
    private val compileLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(1)
    private val nbInjectionHosts: MutableList<NotebookCellInjectionHost> = ContainerUtil.createConcurrentList() // LoggingList()

    private val classesDir: Path by lazy {
        Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
    }

    private val deserializer = CompiledScriptsSerializer()

    private val magicsProcessor = MagicsProcessor(
        handler = NoopMagicsHandler,
        parseOutCellMarker = true
    )

    private val classpathLock = ReentrantReadWriteLock()
    private val currentClasspath: MutableList<File> by lazy {
        projectService.initialClasspath.toMutableList()
    }

    private val additionalDefaultImports: MutableList<String> = mutableListOf<String>().apply {
        addAll(defaultGlobalImports)
    }

    private var kernelJarsAdded: Boolean = false

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterKotlinPluginScriptClassGetter(ScriptTemplateWithDisplayHelpers::class) {
        LOG.warn("Getting implicits list")
        implicitsList
    }

    private val coroutineScope = CoroutineScope(Job())

    init {
        updateClasspathWithExternalDependencies()
        Disposer.register(projectService, this)
    }

    private fun updateClasspathWithExternalDependencies() {
        updateClasspathWithKernelJars()

        coroutineScope.async {
            updateClasspathWithProjectArtifacts()
        }
    }

    private fun updateClasspathWithKernelJars() {
        compileLock.write {
            if (kernelJarsAdded) return
            val session = try {
                JupyterRuntimeService.getInstance(projectService.project).getOrCreateSession(virtualFile)
            } catch (e: Throwable) {
                // TODO: show error for user with asking for configuring Python interpreter for the module
                LOG.warn("Cannot create Jupyter session for Kotlin notebook", e)
                return
            }
            session.detectKotlinKernelJarsDir()?.let { jarsDir ->
                addToClasspath(jarsDir.allJarsFromDir())
                kernelJarsAdded = true
            }
        }
    }

    private suspend fun updateClasspathWithProjectArtifacts() {
        val buildService = JupyterKotlinProjectArtifactsService.getInstance(projectService.project)
        val artifacts =
            buildService.getProjectBuildResult()
                ?: buildService.buildProject()
        addToClasspath(artifacts.map { File(it) })
    }

    fun handleBeforeCompiling(
        sourceCode: SourceCode,
        config: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val sourceText = runReadAction { sourceCode.text }
        LOG.warn("Before-compiling callback for script: $sourceText")
        updateClasspathWithExternalDependencies()
        val withNewClasspath = classpathLock.readLock().withLock {
            config.withUpdatedClasspath(currentClasspath)
        }
        return ScriptCompilationConfiguration(withNewClasspath) {
            hostConfiguration.update {
                it.with {
                    getScriptingClass(classGetter)
                }
            }
            implicitReceivers(implicitsList)
            defaultImports(additionalDefaultImports)
            ide.dependenciesSources(JvmDependency(projectService.project.allSourceRoots()))
        }
    }

    fun <T> withInjectionHosts(action: (List<NotebookCellInjectionHost>) -> T): T {
        return action(nbInjectionHosts)
    }

    fun updateInjectionHosts(updateAction: (MutableList<NotebookCellInjectionHost>) -> Unit) {
        updateAction(nbInjectionHosts)
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata
    ) {
        compileLock.writeLock().withLock {
            try {
                val lineClassesDir = classesDir.resolve("line_${directoryCounter.incrementAndGet()}")
                val lineClassesDirAsFile = lineClassesDir.toFile()
                lineClassesDirAsFile.mkdirs()
                addToClasspath(lineClassesDirAsFile)
                addToClasspath(snippetMetadata.newClasspath.map(::File))
                additionalDefaultImports.addAll(snippetMetadata.newImports)

                val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir)
                val classLoader = URLClassLoader(
                    arrayOf(lineClassesDir.toUri().toURL()),
                    (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
                )
                kClassNames.forEach {
                    val kClass = classLoader.loadClass(it).kotlin
                    implicitsList.addClass(kClass)
                }
            } catch (e: Exception) {
                LOG.error(e)
            }
        }
    }

    private fun getCellCode(cell: PsiElement): String {
        val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
        val source = sourceElement?.text.orEmpty()
        return source.trimStart()
    }

    private fun addToClasspath(file: File) {
        classpathLock.writeLock().withLock {
            currentClasspath.add(file)
        }
    }

    private fun addToClasspath(files: Collection<File>) {
        classpathLock.writeLock().withLock {
            currentClasspath.addAll(files)
        }
    }

    fun codeRanges(cell: JupyterPsiCell): CellRanges {
        val code = getCellCode(cell)
        if (looksLikeReplCommand(code)) return CellRanges(null, listOf(TextRange(0, cell.textLength)))

        val text = cell.text
        val magicIntervals = magicsProcessor.magicsIntervals(text)

        fun Sequence<CodeInterval>.toRanges() = mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }.nullize()

        val codeRanges = magicsProcessor.codeIntervals(text, magicIntervals).toRanges()
        val magicRanges = magicIntervals.toRanges()

        return CellRanges(codeRanges, magicRanges)
    }

    override fun dispose() {
        nbInjectionHosts.clear()
        classesDir.delete(true)
        coroutineScope.cancel()
    }

    data class CellRanges(val codeRanges: List<TextRange>?, val magicRanges: List<TextRange>?)

    companion object {
        private val LOG = Logger.getInstance(JupyterCompilerPerFileService::class.java)

        private fun JupyterNotebookSession.detectKotlinKernelJarsDir(): File? {
            val specs = jupyterServer.client.getKernelSpecs()
            val kotlinSpec = specs.firstOrNull { it.displayName == "Kotlin" } ?: return null
            val command = kotlinSpec.metadata?.get("jar_path_detect_command") as? ArrayNode ?: return null
            val commandArgs: List<String> = mutableListOf<String>().apply {
                command.elements().forEachRemaining {
                    add(it.asText())
                }
            }

            val p: Process = Runtime.getRuntime().exec(commandArgs.toTypedArray())
            val exitCode = p.waitFor()
            if (exitCode != 0) {
                val errorOutput = String(p.errorStream.readAllBytes(), StandardCharsets.UTF_8)
                LOG.error("Unable to detect kernel JARs location")
                LOG.error(errorOutput)
                return null
            }

            val processOutput = p.inputStream.readAllBytes()
            val filePath = String(processOutput, StandardCharsets.UTF_8).trim()
            return File(filePath)
        }
    }
}
