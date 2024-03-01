// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.language

import com.intellij.jupyter.core.jupyter.JupyterLanguage
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import javax.swing.Icon

/**
 * Language representing Jupyter Notebooks using the Kotlin kernel.
 * It is only represented as a language here, so Kotlin Notebooks are picked
 * up as an option when creating Scratch files.
 */
object JupyterKotlinLanguage : Language(JupyterLanguage, "Kotlin Notebook")

object JupyterKotlinFileType: LanguageFileType(JupyterKotlinLanguage) {
    override fun getName(): String = "JupyterKotlinFile"
    override fun getDescription(): String = KotlinNotebookBundle.message("jupyterkotlin.file.type.description")
    override fun getDefaultExtension(): String = "ipynb"
    override fun getIcon(): Icon = KotlinJupyterIcons.FileIcon
}
