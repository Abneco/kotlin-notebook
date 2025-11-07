// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.settings.ui

import com.intellij.kotlin.jupyter.core.settings.KotlinKernelVersions
import com.intellij.kotlin.jupyter.core.settings.extensions.KotlinNotebookSettingsPanelsBuilder
import com.intellij.kotlin.jupyter.core.settings.isKernelVersionEnoughForInstrumentation
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookSettingsPanelBuilder.KernelVersionSelectionChangedListener
import com.intellij.kotlin.jupyter.debug.i18n.KotlinNotebookDebugBundle
import com.intellij.kotlin.jupyter.debug.settings.KotlinNotebookDebugProjectOptionsProvider
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesEnabled
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected

/**
 * Debug-specific settings panel builder
 * All options are reflected in [com.intellij.kotlin.jupyter.debug.settings.KotlinNotebookDebugProjectOptionsProvider]
 */
internal class NotebookDebugFeaturesOptionsPanelBuilder : KotlinNotebookSettingsPanelsBuilder {
    override fun buildAdditionalOptionsPanel(
        project: Project,
        settingsPanel: Panel,
        componentDisposable: Disposable
    ): Row? {
        if (!debugFeaturesEnabled) return null

        val debugOptions = KotlinNotebookDebugProjectOptionsProvider.getInstance(project)
        return with(settingsPanel) {
            group(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug")) {
                row {
                    comment(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.features.separate.process.note"))
                }
                createVariablesViewSelector(project, debugOptions, componentDisposable)
            }
        }
    }

    private fun Panel.createVariablesViewSelector(
        project: Project,
        debugOptions: KotlinNotebookDebugProjectOptionsProvider,
        componentDisposable: Disposable
    ) {
        var variablesBox: Cell<JBCheckBox>? = null
        row {
            variablesBox = checkBox(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.variables"))
                .accessibleDescription(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.variables.description"))
                .comment(
                  KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.port.comment", KotlinKernelVersions.DEBUG_SUPPORTED.toMavenVersion()))
                .bindSelected(debugOptions::shouldShowNotebookVariables)
                .applyToComponent {
                    toolTipText = KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.variables.comment")
                    project.subscribeOnKernelVersionSelectionChange(componentDisposable) { newVersion ->
                        isEnabled = newVersion?.isKernelVersionEnoughForInstrumentation ?: false
                    }
                }
        }
        row {
            checkBox(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.variables.focus.check.box"))
                .bindSelected(debugOptions::shouldFocusOnVariables)
                .accessibleDescription(KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug.variables.focus.check.box.description"))
                .applyToComponent {
                    isEnabled = debugOptions.shouldShowNotebookVariables
                    variablesBox?.onChanged {
                        isEnabled = it.isEnabled && it.isSelected
                    }
                }
        }
    }

    private fun Project.subscribeOnKernelVersionSelectionChange(
        disposable: Disposable,
        listener: KernelVersionSelectionChangedListener
    ) {
        messageBus.connect(disposable).subscribe(KernelVersionSelectionChangedListener.TOPIC, listener)
    }
}