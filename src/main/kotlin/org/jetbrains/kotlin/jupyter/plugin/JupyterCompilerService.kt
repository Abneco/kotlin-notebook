package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.io.isFile
import com.jetbrains.rd.util.string.printToString
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlin.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlin.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlin.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlin.jupyter.config.getLogger
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactoryDefaultInfoSwitcher
import org.jetbrains.kotlin.jupyter.magics.LibrariesOnlyMagicsHandler
import org.jetbrains.kotlin.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.streams.toList

typealias InjectedElementsList = List<Pair<PsiElement, TextRange>>

@Service
class JupyterCompilerService(private val project: Project) {
    private val logger = getLogger("Jupyter Compiler Service")
    val compileLock = ReentrantReadWriteLock()
    private val scriptingSettings = KotlinScriptingSettings.getInstance(project)
    val nbInjectionHosts = mutableListOf<NotebookCellInjectionHost>()

    init {
        System.setProperty(
            "script.compilation.disable.plugins",
            listOf(
                "org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar",
                "org.jetbrains.kotlin.noarg.NoArgComponentRegistrar",
                "org.jetbrains.kotlin.android.synthetic.AndroidComponentRegistrar",
                "org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar",
                "org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar",
            ).joinToString(";")
        )
        System.setProperty(
            "script.compilation.disable.commandline.processors",
            listOf(
                "org.jetbrains.kotlin.noarg.NoArgCommandLineProcessor",
                "org.jetbrains.kotlin.allopen.AllOpenCommandLineProcessor",
                "org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor",
                "org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor",
                // "org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor",
            ).joinToString(";")
        )
    }

    private val classesDir: Path by lazy {
        val tempDir = Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
        tempDir.toFile().deleteOnExit()
        tempDir
    }

    private val deserializer = CompiledScriptsSerializer()

    private val librariesFactory = LibraryFactory.withDefaultGitRefResolution("master")
    private val librariesProcessor = LibrariesProcessor(librariesFactory.getStandardResolver(), null, librariesFactory)
    private val infoSwitcher = LibraryFactoryDefaultInfoSwitcher.noop(librariesFactory.resolutionInfoProvider)

    private val magicsProcessor = MagicsProcessor(
        handler = LibrariesOnlyMagicsHandler(librariesProcessor, infoSwitcher),
        parseOutCellMarker = true
    )

    private val currentClasspath: List<File> by lazy {
        val pathToJars = Paths.get("C:/Users/Ilya.Muradyan/AppData/Roaming/jupyter/kernels/kotlin/jars")
        val files = Files.walk(pathToJars).filter { path ->
            path.isFile() && !path.fileName.toString().contains("kotlin-jupyter-kernel")
        }.map {
            it.toFile()
        }.toList()

        files
        /*
        scriptCompilationClasspathFromContext(
            "notebook-api",
            "notebook-lib",
            "kotlin-stdlib",
            "kotlin-reflect",
            "kotlin-script-runtime",
            classLoader = ScriptTemplateWithDisplayHelpers::class.java.classLoader
        )*/
    }

    private val implicitsList = KotlinImplicitsList()

    val jupyterCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = currentClasspath + listOf(classesDir.toFile()),
            scriptingClassGetter = JupyterScriptClassGetter {
                compileLock.read {
                    implicitsList
                }
            }
        ) {
            implicitReceivers(implicitsList)
            refineConfiguration {
                beforeCompiling { (_, config, _) ->
                    println("Compilation of Jupyter.kts snippet")

                    // val previousScriptClasses: Array<KClass<*>> = jupyterCompiler.previousScriptsClasses.toTypedArray()
                    // config.with {
                    //     implicitReceivers(*previousScriptClasses)
                    // }.asSuccess()
                    config.asSuccess()
                }
            }
        }
    }

    val jupyterEvaluationConfiguration by lazy {
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(JupyterDefProvider::class.java.classLoader)
            }
        }
    }

    fun addCompiledSnippet(compiledData: SerializedCompiledScriptsData) {
        compileLock.write {
            try {
                val kClassNames = deserializer.deserializeAndSave(compiledData, classesDir)
                val classLoader = URLClassLoader(
                    arrayOf(classesDir.toUri().toURL()),
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
            } catch (e: ReplCompilerException) {
                logger.error(e.printToString())
            }
        }
    }

    fun codeRanges(text: String): List<TextRange> {
        return magicsProcessor.codeIntervals(text).mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }
    }
}
