package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.File
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class JupyterDefProvider(project: Project) : ScriptDefinitionsProvider, Disposable {
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
        return loadedScriptDefinitions + listOf(
            ScriptDefinition(
                projectCompilerService.initialCompileConfiguration,
                projectCompilerService.evaluationConfiguration
            )
        )
    }

    override fun dispose() {
        disposable.dispose()
    }
}
