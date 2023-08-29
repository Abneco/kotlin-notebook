// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.ui.configuration.LanguageLevelCombo
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import kotlin.reflect.KMutableProperty0

private val runtimeJavaSdkVersion: JavaSdkVersion? by lazy {
    val runtimeVersion = Runtime.version()
    JavaSdkVersion.fromVersionString(runtimeVersion.toString())
}
internal val maxBytecodeVersion get() = runtimeJavaSdkVersion?.maxLanguageLevel

class SnippetsLanguageLevelComboBox(
    override val defaultLevel: LanguageLevel?,
    maxLevel: LanguageLevel?,
) :
    LanguageLevelCombo(
        KotlinNotebookBundle.message("kotlin.jupyter.settings.selected.jdk.default"),
        levelFilter = { maxLevel == null || it <= maxLevel }
    )
{
    fun reset() {
        selectedItem = defaultLevel
    }
}

fun Row.snippetsLanguageLevelComboBox(
    languageLevelProperty: KMutableProperty0<LanguageLevel?>,
): Cell<SnippetsLanguageLevelComboBox> {
    val initialLevel = languageLevelProperty.get()
    val maxLevel = maxBytecodeVersion
    val comboBox = SnippetsLanguageLevelComboBox(initialLevel, maxLevel)

    return cell(comboBox)
        .onReset {
            comboBox.reset()
        }.onIsModified {
            languageLevelProperty.get() != comboBox.selectedLevel
        }.onApply {
            languageLevelProperty.set(comboBox.selectedLevel)
        }.apply {
            if (maxLevel != null) {
                comment(
                    KotlinNotebookBundle.message(
                        "kotlin.jupyter.settings.jvm.target.for.snippets.comment",
                        maxLevel.toJavaVersion().toFeatureString()
                    )
                )
            }
        }
}
