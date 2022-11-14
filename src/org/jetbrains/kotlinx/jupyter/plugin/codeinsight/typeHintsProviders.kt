// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.codeinsight

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinLambdasHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinReferencesTypeHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinValuesHintsProvider


class NotebookLambdaTypeHintsProvider: KotlinNotebookAbstractInlayTypeHintsProvider<KotlinLambdasHintsProvider.Settings>() {
    private val backingKtLambdaProvider = KotlinLambdasHintsProvider()

    override val key: SettingsKey<KotlinLambdasHintsProvider.Settings> = backingKtLambdaProvider.key

    override fun createConfigurable(settings: KotlinLambdasHintsProvider.Settings): ImmediateConfigurable {
        return backingKtLambdaProvider.createConfigurable(settings)
    }

    override val name: String = backingKtLambdaProvider.name

    override val group: InlayGroup = backingKtLambdaProvider.group

    override fun isElementSupported(resolved: HintType?, settings: KotlinLambdasHintsProvider.Settings): Boolean =
        backingKtLambdaProvider.isElementSupported(resolved, settings)

    override fun isHintSupported(hintType: HintType): Boolean = backingKtLambdaProvider.isHintSupported(hintType)

    override fun createSettings() = backingKtLambdaProvider.createSettings()

    override val properTarget: String = "lambdas"

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinLambdasHintsProvider.Settings) = Unit
    override val previewText: String? = null
}


class KotlinNotebookReferencesTypeHintsProvider : KotlinNotebookAbstractInlayTypeHintsProvider<KotlinReferencesTypeHintsProvider.Settings>() {
    private val backingKtProvider = KotlinReferencesTypeHintsProvider()

    override val key: SettingsKey<KotlinReferencesTypeHintsProvider.Settings> = backingKtProvider.key
    override val name = backingKtProvider.name
    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

    override val properTarget: String = "types"

    override fun createConfigurable(settings: KotlinReferencesTypeHintsProvider.Settings): ImmediateConfigurable
        = backingKtProvider.createConfigurable(settings)

    override fun createSettings() = backingKtProvider.createSettings()

    override fun isElementSupported(resolved: HintType?, settings: KotlinReferencesTypeHintsProvider.Settings): Boolean
        = backingKtProvider.isElementSupported(resolved, settings)

    override fun isHintSupported(hintType: HintType): Boolean = backingKtProvider.isHintSupported(hintType)

    override val previewText: String? = null

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinReferencesTypeHintsProvider.Settings) = Unit
}

class NotebookValuesHintProvider: KotlinNotebookAbstractInlayTypeHintsProvider<KotlinValuesHintsProvider.Settings>() {
    private val backingKtProvider = KotlinValuesHintsProvider()

    override fun isElementSupported(resolved: HintType?, settings: KotlinValuesHintsProvider.Settings): Boolean
        = backingKtProvider.isElementSupported(resolved, settings)

    override fun createSettings(): KotlinValuesHintsProvider.Settings = backingKtProvider.createSettings()
    override val name: String = backingKtProvider.name
    override val key: SettingsKey<KotlinValuesHintsProvider.Settings> = backingKtProvider.key

    override fun createConfigurable(settings: KotlinValuesHintsProvider.Settings): ImmediateConfigurable
        = backingKtProvider.createConfigurable(settings)

    override val properTarget: String
        get() = "ranges"

    override val description: String? = null

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinValuesHintsProvider.Settings) = Unit
}
