package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.string.printToString
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.FallbackLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.SharedMagicsHandler
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.withUpdatedClasspath

typealias InjectedElementsList = List<Pair<PsiElement, TextRange>>

class JupyterCompilerPerFileService(
    private val project: Project,
    @Suppress("unused") private val virtualFile: VirtualFile,
    private val projectService: JupyterCompilerService,
) {
    private val log = Logger.getInstance(this::class.java)
    val compileLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(1)
    private val scriptingSettings = KotlinScriptingSettings.getInstance(project)
    val nbInjectionHosts = mutableListOf<NotebookCellInjectionHost>()

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

    private val implicitsList = KotlinImplicitsList()
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
        log.warn("Before-compiling callback for script: ${sourceCode.text}")
        val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
        return ScriptCompilationConfiguration(withNewClasspath) {
            hostConfiguration.update {
                it.with {
                    getScriptingClass(classGetter)
                }
            }
            implicitReceivers(implicitsList)
        }
    }

    fun addCompiledSnippet(
        compiledData: SerializedCompiledScriptsData,
        newClasspath: List<File>,
    ) {
        compileLock.write {
            try {
                val lineClassesDir = classesDir.resolve("line_$directoryCounter")
                directoryCounter.incrementAndGet()
                val lineClassesDirAsFile = lineClassesDir.toFile()
                lineClassesDirAsFile.mkdirs()
                currentClasspath.add(lineClassesDirAsFile)

                val newClasspathStr = if (newClasspath.isEmpty()) "> No new classpath added."
                else "> New classpath added:\n" + newClasspath.joinToString("\n", "  ")
                log.warn(newClasspathStr)
                currentClasspath.addAll(newClasspath)

                val kClassNames = deserializer.deserializeAndSave(compiledData, lineClassesDir)
                val classLoader = URLClassLoader(
                    arrayOf(lineClassesDir.toUri().toURL()),
                    (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
                )
                kClassNames.forEach {
                    val kClass = classLoader.loadClass(it).kotlin
                    implicitsList.addClass(kClass)
                }

                val injectedManager = InjectedLanguageManager.getInstance(project)
                val scriptManager = ScriptConfigurationManager.getInstance(project)
                    as? CompositeScriptConfigurationManager ?: return@write

                nbInjectionHosts.forEach { host ->
                    val injectedFiles = ReadAction.compute<InjectedElementsList?, Error> {
                        injectedManager.getInjectedPsiFiles(host)
                    } ?: return@forEach

                    val injectedKtScripts = injectedFiles.mapNotNull {
                        it.first as? KtFile
                    }

                    for (psi in injectedKtScripts) {
                        val definition = psi.findScriptDefinition() ?: continue
                        scriptingSettings.setAutoReloadConfigurations(definition, true)
                    }

                    ReadAction.run<Error> {
                        for (psi in injectedKtScripts) {
                            scriptManager.default.ensureUpToDatedConfigurationSuggested(psi)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error(e.printToString())
            }
        }
    }

    fun codeRanges(text: String): List<TextRange> {
        return magicsProcessor.codeIntervals(text).mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }
    }
}
