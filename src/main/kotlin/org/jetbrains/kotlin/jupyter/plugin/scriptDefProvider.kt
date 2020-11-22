package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.jupyter.config.DependsOn
import org.jetbrains.kotlin.jupyter.config.getCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class JupyterDefProvider(val project: Project): ScriptDefinitionsProvider, Disposable {
    private val disposable = Disposer.newDisposable()

    override val id: String = "Jupyter Definition provider"

    override fun getDefinitionClasses(): Iterable<String> = emptyList()

    override fun getDefinitionsClassPath(): Iterable<File> = emptyList()

    override fun useDiscovery(): Boolean = false

    override fun provideDefinitions(baseHostConfiguration: ScriptingHostConfiguration, loadedScriptDefinitions: List<ScriptDefinition>): Iterable<ScriptDefinition> {
        return loadedScriptDefinitions + listOf(ScriptDefinition(
            getCompilationConfiguration {
                println("Compilation of Jupyter.kts snippet")
            },
            ScriptEvaluationConfiguration()
        ))
    }

    override fun dispose() {
        disposable.dispose()
    }
}