// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

class KotlinNotebookNewNotebookConfigurable(val project: Project) :
    BoundConfigurable(KotlinNotebookBundle.getMessage("kotlin.jupyter.settings.new.notebook.title")),
    SearchableConfigurable {

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel {
        val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
        return panel {
            row {
                text(KotlinNotebookBundle.message("kotlin.jupyter.settings.new.notebook.description"))
            }
            panel {
                group(KotlinNotebookBundle.message("kotlin.jupyter.settings.build")) {
                    row {
                        checkBox(KotlinNotebookBundle.message("checkbox.should.build.project"))
                            .comment(KotlinNotebookBundle.message("checkbox.should.build.project.comment"))
                            .bindSelected(optionsProvider::shouldBuildProject)
                    }
                    row {
                        checkBox(KotlinNotebookBundle.message("checkbox.should.add.libraries"))
                            .bindSelected(optionsProvider::shouldAddProjectLibrariesToClasspath)
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "kotlinNotebook.newNotebook"
    }
}