// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.hints

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiLanguageInjectionHost

interface PsiHostTypeHintsInvalidator {
    fun invalidateTypeHintsRegistry(host: PsiLanguageInjectionHost)

    companion object {
        val EP: ExtensionPointName<PsiHostTypeHintsInvalidator> = ExtensionPointName.create<PsiHostTypeHintsInvalidator>("com.intellij.kotlin.jupyter.core.psiHostTypeHintsInvalidator")

        fun invalidateTypeHintsRegistry(host: PsiLanguageInjectionHost) {
            for (extension in EP.extensionList) {
                extension.invalidateTypeHintsRegistry(host)
            }
        }
    }
}