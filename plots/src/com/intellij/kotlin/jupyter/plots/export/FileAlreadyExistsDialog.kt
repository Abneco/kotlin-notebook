// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.AbstractAction

fun showFileAlreadyExistsDialog(
    file: File,
    showRememberChoiceCheckbox: Boolean,
    rememberChoiceProperty: MutableProperty<Boolean>,
    strategyProperty: MutableProperty<FileAlreadyExistsStrategy>
) {
    val dialogBuilder = DialogBuilder()

    val dialogPanel = panel {
        row {
            label(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.text", file.absolutePath))
        }

        if (showRememberChoiceCheckbox) {
            row {
                checkBox(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.remember.choice"))
                    .onChanged { checkBox -> rememberChoiceProperty.set(checkBox.isSelected) }
            }
        }
    }

    fun closeDialog() {
        dialogBuilder.dialogWrapper.close(OK_EXIT_CODE, true)
    }

    fun createChangeStrategyAction(
        name: @NlsActions.ActionText String,
        strategy: FileAlreadyExistsStrategy,
    ) = object : AbstractAction(name) {
        override fun actionPerformed(e: ActionEvent?) {
            strategyProperty.set(strategy)
            closeDialog()
        }
    }

    val createNewAction = createChangeStrategyAction(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.save.new.button"), FileAlreadyExistsStrategy.CREATE_NEW)
    val cancelAction = createChangeStrategyAction(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.do.not.save.button"), FileAlreadyExistsStrategy.SKIP)

    dialogBuilder
        .title(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.title", file.name))
        .centerPanel(dialogPanel)
        .apply {
            addOkAction().setText(KotlinNotebookPlotsBundle.message("kotlin.notebook.plots.dialog.file.already.exists.replace.button"))
            setOkOperation {
                strategyProperty.set(FileAlreadyExistsStrategy.OVERWRITE)
                closeDialog()
            }

            addLeftSideAction(cancelAction)
            setCancelOperation {
                strategyProperty.set(FileAlreadyExistsStrategy.SKIP)
                closeDialog()
            }

            addAction(createNewAction)
        }
        .show()
}
