package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

class JupyterKotlinIntoJsonInjector(project: Project) : MultiHostInjector, Disposable {
    private val disposable = Disposer.newDisposable()
    private val projectCompilerService = JupyterCompilerService.getInstance(project)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JsonArray) return

        for (value in element.valueList) {
            if (value !is JsonStringLiteral) continue
            registrar.startInjecting(Language.findLanguageByID("kotlin")!!, projectCompilerService.fileExtension)

            val textRange = value.textRange

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
