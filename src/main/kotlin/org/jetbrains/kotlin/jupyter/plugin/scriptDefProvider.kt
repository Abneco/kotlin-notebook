package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.compiler.JupyterCompiler
import org.jetbrains.kotlin.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlin.jupyter.compiler.getSimpleCompiler
import org.jetbrains.kotlin.jupyter.config.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClass
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

abstract class BaseClass {
    val xyz42: Int = 42
}


@KotlinScript(fileExtension = "jupyter.kts")
abstract class JupyterScript {
    val xyz42 = 42
}

val classesDir by lazy {
    val tempDir = Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
    tempDir.toFile().deleteOnExit()
    tempDir
}

val classWriter = ClassWriter(classesDir)

val currentClasspath: List<File>
    get() {
        /*
        val classes = listOf<KClass<*>>(
            //JupyterScript::class,
            ScriptTemplateWithDisplayHelpers::class,
        ).map { it.qualifiedName!! }

        val result = mutableListOf<File>()
        for (kClass in classes) {
            val cl = kClass.java.classLoader
            val cp = scriptCompilationClasspathFromContext(keyNames = classes)
            result.addAll(cp ?: emptyList())
        }
        return result

         */

        return scriptCompilationClasspathFromContext(
            "notebook-api",
            "compiler",
            classLoader = ScriptTemplateWithDisplayHelpers::class.java.classLoader
        )
    }

val jupyterCompileConfiguration by lazy {
    System.setProperty(
        "script.compilation.disable.plugins",
        listOf(
            "org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar",
            "org.jetbrains.kotlin.noarg.NoArgComponentRegistrar",
            "org.jetbrains.kotlin.android.synthetic.AndroidComponentRegistrar",
            "org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar",
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

    getCompilationConfiguration(
        scriptClasspath = currentClasspath + listOf(classesDir.toFile()),
        scriptingClassGetter = JupyterScriptClassGetter {
            jupyterCompiler.previousScriptsClasses
        }
    ) {
        refineConfiguration {
            beforeCompiling { (_, config, _) ->
                println("Compilation of Jupyter.kts snippet")

                val previousScriptClasses: Array<KClass<*>> = jupyterCompiler.previousScriptsClasses.toTypedArray()
                config.with {
                    implicitReceivers(*previousScriptClasses)
                }.asSuccess()
            }
        }
    }
}

val jupyterEvaluationConfiguration by lazy {
    ScriptEvaluationConfiguration {
        jvm {
            baseClassLoader(BaseClass::class.java.classLoader)
        }
    }
}

val jupyterCompiler: JupyterCompiler<KJvmReplCompilerBase<ReplCodeAnalyzerBase>> = getSimpleCompiler(jupyterCompileConfiguration, jupyterEvaluationConfiguration)

val rwLock = ReentrantLock()


class JupyterDefProvider(val project: Project): ScriptDefinitionsProvider, Disposable {
    private val disposable = Disposer.newDisposable()

    override val id: String = "Jupyter Definition provider"

    override fun getDefinitionClasses(): Iterable<String> = emptyList()

    override fun getDefinitionsClassPath(): Iterable<File> = emptyList()

    override fun useDiscovery(): Boolean = false

    override fun provideDefinitions(baseHostConfiguration: ScriptingHostConfiguration, loadedScriptDefinitions: List<ScriptDefinition>): Iterable<ScriptDefinition> {
        return loadedScriptDefinitions + listOf(ScriptDefinition(
            jupyterCompileConfiguration,
            jupyterEvaluationConfiguration
        ))
    }

    override fun dispose() {
        disposable.dispose()
    }
}