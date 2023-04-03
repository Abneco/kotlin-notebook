// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle

class KotlinNotebookNewNotebookConfigurable(val project: Project) :
    BoundConfigurable(JupyterKotlinBundle.getMessage("kotlin.jupyter.settings.new.notebook.title")),
    SearchableConfigurable {

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel {
        val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
        return panel {
            row {
                text(JupyterKotlinBundle.message("kotlin.jupyter.settings.new.notebook.description"))
            }
            panel {
                group(JupyterKotlinBundle.message("kotlin.jupyter.settings.build")) {
                    row {
                        checkBox(JupyterKotlinBundle.message("checkbox.should.build.project"))
                            .comment(JupyterKotlinBundle.message("checkbox.should.build.project.comment"))
                            .bindSelected(optionsProvider.state::shouldBuildProject)
                    }
                    row {
                        checkBox(JupyterKotlinBundle.message("checkbox.should.add.libraries"))
                            .bindSelected(optionsProvider.state::shouldAddProjectLibrariesToClasspath)
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "kotlinNotebook.newNotebook"
    }
}