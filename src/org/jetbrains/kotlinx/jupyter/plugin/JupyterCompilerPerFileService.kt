package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.nullize
import com.intellij.util.io.delete
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterSource
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
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
    @Suppress("unused") private val virtualFile: VirtualFile,
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

    private val currentClasspath: MutableList<File> by lazy {
        projectService.initialClasspath.toMutableList()
    }

    private val additionalDefaultImports: MutableList<String> = mutableListOf<String>().apply {
        addAll(defaultGlobalImports)
    }

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterScriptClassGetter {
        LOG.warn("Getting implicits list")
        implicitsList
    }

    init {
        Disposer.register(projectService, this)
    }

    fun handleBeforeCompiling(
        sourceCode: SourceCode,
        config: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val sourceText = runReadAction { sourceCode.text }
        LOG.warn("Before-compiling callback for script: $sourceText")
        val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
        return ScriptCompilationConfiguration(withNewClasspath) {
            hostConfiguration.update {
                it.with {
                    getScriptingClass(classGetter)
                }
            }
            implicitReceivers(implicitsList)
            defaultImports(additionalDefaultImports)
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
                currentClasspath.add(lineClassesDirAsFile)

                currentClasspath.addAll(snippetMetadata.newClasspath.map(::File))
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
    }

    data class CellRanges(val codeRanges: List<TextRange>?, val magicRanges: List<TextRange>?)

    companion object {
        private val LOG = Logger.getInstance(JupyterCompilerPerFileService::class.java)
    }
}
