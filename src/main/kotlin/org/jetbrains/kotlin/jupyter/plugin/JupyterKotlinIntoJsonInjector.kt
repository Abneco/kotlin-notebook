package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension


class JupyterKotlinIntoJsonInjector(val project: Project): MultiHostInjector, Disposable {
    private val disposable = Disposer.newDisposable()
    private val compilerService = project.service<JupyterCompilerService>()

    init {
        //Disposer.register(project, this)
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JsonArray) return

        val values = element.valueList
        val configuration = compilerService.jupyterCompileConfiguration
        val fileExtension = configuration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter-kts"

        for (value in values) {
            if (value !is JsonStringLiteral) continue
            registrar.startInjecting(Language.findLanguageByID("kotlin")!!, fileExtension)
            //registrar.startInjecting(Language.findLanguageByID("kotlin")!!)

            val compiler = compilerService.jupyterCompiler
            compilerService.rwLock.withLock {
                if (compiler.numberOfSnippets < 10) {
                    val sourceCode = compiler.nextSourceCode("""
                        val xyz${compiler.numberOfSnippets} = 42
                    """.trimIndent())
                    compiler.compileSync(sourceCode)

                    compiler.compiler.lastCompiledSnippet?.get()?.let {
                        compilerService.classWriter.writeCompiledSnippet(it)
                    }
                }
            }


            val textRange = (value as JsonStringLiteral).textRange

            val code = value.value

            val valueTextRange = TextRange(textRange.startOffset + 1, textRange.endOffset - 1)
            val shiftedRange = valueTextRange.shiftLeft(textRange.startOffset)
            registrar.addPlace(null, null, value as PsiLanguageInjectionHost, shiftedRange)
            registrar.doneInjecting()
        }

        
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JsonArray::class.java)
    }

    override fun dispose() {
       disposable.dispose()
    }

}