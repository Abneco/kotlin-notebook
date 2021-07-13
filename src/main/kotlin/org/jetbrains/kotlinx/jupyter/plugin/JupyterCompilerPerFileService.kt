package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.nullize
import com.jetbrains.rd.util.string.printToString
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.FallbackLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.SharedMagicsHandler
import org.jetbrains.kotlinx.jupyter.plugin.util.logList
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterSource
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
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
) {
    private val log = Logger.getInstance(this::class.java)

    private val project = projectService.project
    private val compileLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(1)
    private val scriptingSettings = KotlinScriptingSettings.getInstance(project)
    private val nbInjectionHosts = mutableListOf<NotebookCellInjectionHost>()

    private val classesDir: Path by lazy {
        val tempDir = Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
        tempDir.toFile().deleteOnExit()
        tempDir
    }

    private val deserializer = CompiledScriptsSerializer()

    private val librariesProcessor = LibrariesProcessorImpl(FallbackLibraryResolver, null)
    private val infoSwitcher = ResolutionInfoSwitcher.noop(EmptyResolutionInfoProvider)

    private val magicsProcessor = MagicsProcessor(
        handler = SharedMagicsHandler(librariesProcessor, infoSwitcher),
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
        compileLock.write {
            log.warn("Getting implicits list")
            implicitsList
        }
    }

    fun handleBeforeCompiling(
        sourceCode: SourceCode,
        config: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val sourceText = readAction { sourceCode.text }
        log.warn("Before-compiling callback for script: $sourceText")
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
        return compileLock.read {
            action(nbInjectionHosts)
        }
    }

    fun updateInjectionHosts(updateAction: (MutableList<NotebookCellInjectionHost>) -> Unit) {
        compileLock.write {
            updateAction(nbInjectionHosts)
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata
    ) {
        compileLock.write {
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

                val injectedManager = InjectedLanguageManager.getInstance(project)

                nbInjectionHosts.forEach { host ->
                    val injectedFiles = ReadAction.compute<InjectedElementsList?, Error> {
                        injectedManager.getInjectedPsiFiles(host)
                    } ?: return@forEach

                    val injectedKtScripts = injectedFiles.mapNotNull {
                        it.first as? KtFile
                    }

                    log.logList("KT files injected", injectedKtScripts)

                    for (psi in injectedKtScripts) {
                        val definition = psi.findScriptDefinition() ?: continue
                        scriptingSettings.setAutoReloadConfigurations(definition, true)
                    }
                }
            } catch (e: Exception) {
                log.error(e.printToString())
            }
        }
    }

    private fun getCellCode(cell: PsiElement): String {
        val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
        val source = sourceElement?.text.orEmpty()
        return source.trimStart()
    }

    fun codeRanges(cell: JupyterPsiCell): CellRanges? {
        val code = getCellCode(cell)
        if (looksLikeReplCommand(code)) return null

        val text = cell.text
        val magicIntervals = magicsProcessor.magicsIntervals(text)

        fun Sequence<CodeInterval>.toRanges() = mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }.nullize()

        val codeRanges = magicsProcessor.codeIntervals(text, magicIntervals).toRanges()
        val magicRanges = magicIntervals.toRanges()

        return CellRanges(codeRanges, magicRanges)
    }

    data class CellRanges(val codeRanges: List<TextRange>?, val magicRanges: List<TextRange>?)
}
