// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.description
import com.intellij.kotlin.jupyter.core.language.displayName
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import icons.KotlinJupyterIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import kotlin.reflect.KMutableProperty0

private class KotlinNotebookGeneratorNewProjectWizard : GeneratorNewProjectWizard {
    override val id: @NonNls String
        get() = "KotlinNotebook"
    override val name: @Nls(capitalization = Nls.Capitalization.Title) String
        get() = KotlinNotebookBundle.message("kotlin.notebook.project.wizard.title")
    override val icon: Icon
        get() = KotlinJupyterIcons.FileIcon

    override fun isEnabled(): Boolean {
        return false
    }

    override fun createStep(context: WizardContext): NewProjectWizardStep {
        val path = DefaultKotlinNotebookProject.rootPath
        context.setProjectFileDirectory(path, true)
        context.projectName = KOTLIN_NOTEBOOK_TEMPLATE_PROJECT_FOLDER_NAME

        return RootNewProjectWizardStep(context)
            .nextStep(::KotlinNotebookWizardStep)
    }
}

private class KotlinNotebookWizardStep(parentStep: NewProjectWizardStep) : AbstractNewProjectWizardStep(parentStep) {
    private var notebookName: String = ""
    private var template: NotebookTemplate = NotebookTemplate.EMPTY

    override fun setupUI(builder: Panel) {
        super.setupUI(builder)
        builder.row(KotlinNotebookBundle.message("kotlin.notebook.project.wizard.notebook.name.label")) {
            textField().bindText(::notebookName)
        }
        builder.row(KotlinNotebookBundle.message("kotlin.notebook.project.wizard.template.label")) {}
        builder.row{
            cell(TemplateSelectionPanel(::template))
                .resizableColumn()
                .align(AlignX.FILL)
        }
    }


    override fun setupProject(project: Project) {
        super.setupProject(project)

        createKotlinNotebookInProjectWhenProjectIsInitialized(
            project,
            template,
            notebookName,
        )
    }
}

class TemplateSelectionPanel(val templateProperty: KMutableProperty0<NotebookTemplate>) : JBPanel<TemplateSelectionPanel>(BorderLayout()) {
    init {
        // Template list model
        val listModel = DefaultListModel<NotebookTemplate>().apply {
            addAll(NotebookTemplate.entries)
        }

        // JBList to display template options
        val templateList = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0

            installCellRenderer {
                JBLabel(it.displayName)
            }
        }

        // Description panel with JBLabel
        val descriptionLabel = JBLabel("", SwingConstants.LEFT)
        val descriptionPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(descriptionLabel, BorderLayout.NORTH)
        }

        fun updateDescriptionLabel() {
            descriptionLabel.text = templateList.selectedValue.description
            templateProperty.set(templateList.selectedValue)
        }

        // Selection listener to update description
        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateDescriptionLabel()
            }
        }
        templateList.selectedIndex = listModel.indexOf(templateProperty.get())
        updateDescriptionLabel()


        // Split pane layout
        val splitter = OnePixelSplitter(false, 0.3f).apply {
            firstComponent = JScrollPane(templateList)
            secondComponent = descriptionPanel

            border = BorderFactory.createLineBorder(JBColor.border(), 1, true)
            setResizeEnabled(true)
        }

        add(splitter, BorderLayout.CENTER)
    }
}

internal class KotlinNotebookModuleBuilder : GeneratorNewProjectWizardBuilderAdapter(KotlinNotebookGeneratorNewProjectWizard())
