package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.File
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

/**
 * [JupyterScriptDefinitionProvider] provides script definition for jupyter.kts
 * files. Script definition is compile configuration + evaluation configuration.
 * API gives us an ability to provide it in several different ways, but we use the most
 * universal one - [provideDefinitions] method, so that other methods return nothing.
 */
class JupyterScriptDefinitionProvider(project: Project) : ScriptDefinitionsProvider, Disposable {
    private val disposable = Disposer.newDisposable()
    private val projectCompilerService = JupyterCompilerService.getInstance(project)

    override val id: String = "Jupyter Definition provider"

    override fun getDefinitionClasses(): Iterable<String> = emptyList()

    override fun getDefinitionsClassPath(): Iterable<File> = emptyList()

    override fun useDiscovery(): Boolean = false

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>
    ): Iterable<ScriptDefinition> {
        return loadedScriptDefinitions + projectCompilerService.scriptDefinition
    }

    override fun dispose() {
        disposable.dispose()
    }
}
