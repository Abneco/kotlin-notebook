// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.actions.createFileFromTemplateWithProperties
import com.intellij.jupyter.core.jupyter.nbformat.schema.JUPYTER_NOTEBOOK_SCHEMA_VERSION_4_5
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSettings
import com.intellij.kotlin.jupyter.core.settings.asJson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo

/**
 * Enum describing how a Notebook is treated in the filesystem.
 */
enum class NotebookMode(
    val id: String // Unique id used for serialization to JSON. Changing this will break reading already existing notebook files.
) {
    // A standard notebook found inside the project. Will resolve paths relative to its own location
    STANDARD("standard"),

    // A light notebook found in the "Scratches and Consoles" section. Will resolve paths relative to the
    // project content root.
    LIGHT("light")
}

/**
 * Extension property for exposing the [NotebookMode] of Kotlin Notebooks.
 * Is an extension property to avoid a cyclic dependency with [BackedNotebookVirtualFile].
 */
val BackedNotebookVirtualFile.mode: NotebookMode
    get() {
        return when (ScratchUtil.isScratch(this.file)) {
            true -> NotebookMode.LIGHT
            false -> NotebookMode.STANDARD
        }
    }

/**
 * Class containing the logic for creating different kinds of notebooks. All actions that want to
 * create notebooks should go through this class.
 */
object CreateNotebookFactory {

    private const val VAR_KERNEL_SPEC = "KERNEL_SPEC"
    private const val VAR_LANGUAGE_SPEC = "LANGUAGE_SPEC"
    private const val VAR_KTNB_METADATA = "KTNB_METADATA"
    private const val VAR_NBFORMAT_MAJOR = "NBFORMAT_MAJOR"
    private const val VAR_NBFORMAT_MINOR = "NBFORMAT_MINOR"

    private val defaultSchemaVersion = JUPYTER_NOTEBOOK_SCHEMA_VERSION_4_5

    private val LOG = logger<CreateNotebookFactory>()

    private fun createTemplateValues(project: Project, mode: NotebookMode): Map<String, String> {
        val serializer = Json {
            prettyPrint = true
        }
        val kernelSpec = serializer.encodeToString(notebookKernelSpec)
        val languageSpec = serializer.encodeToString(notebookLanguageInfo)
        val notebookSettings = getNewKotlinNotebookSettings(project, mode).asJson()

        return buildMap {
            put(VAR_KERNEL_SPEC, kernelSpec)
            put(VAR_LANGUAGE_SPEC, languageSpec)
            put(VAR_NBFORMAT_MAJOR, defaultSchemaVersion.major.toString())
            put(VAR_NBFORMAT_MINOR, defaultSchemaVersion.minor.toString())
            if (notebookSettings != null) {
                put(VAR_KTNB_METADATA, notebookSettings)
            }
        }
    }

    @RequiresEdt
    private fun getNewKotlinNotebookSettings(project: Project, mode: NotebookMode): KotlinNotebookSettings {
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project)

        // Light Kotlin Notebooks should never include neither modules nor project dependencies as a default, as
        // they should be able to start as fast as possible.
        // Standard Notebooks should make the choice based on the default value for the property.
        fun disabledInLightMode(flag: Boolean) = flag && mode != NotebookMode.LIGHT

        val includeLibraries = disabledInLightMode(options.shouldAddProjectLibrariesToClasspath)

        return KotlinNotebookSettings(
            notebookDependencies = if (includeLibraries) KotlinNotebookDependencies.AllLibraries else KotlinNotebookDependencies.None,
            sessionRunMode = KotlinNotebookSessionRunMode.SEPARATE_PROCESS,
        )
    }

    /**
     * Creates a Notebook file from the provided [FileTemplate].
     * After the file is created, it will be given focus in the IDE.
     *
     * @see [KotlinNotebookCreateAction.createFileFromTemplate]
     */
    fun createFileFromTemplate(
        fileName: String,
        template: FileTemplate,
        directory: PsiDirectory,
        defaultTemplateProperty: String? = null,
        openFileInIde: Boolean = true,
        mode: NotebookMode = NotebookMode.STANDARD,
    ): PsiFile? {
        val templateValues = createTemplateValues(directory.project, mode)
        return createFileFromTemplateWithProperties(
            name = fileName,
            template = template,
            dir = directory,
            defaultTemplateProperty = defaultTemplateProperty,
            templateValues = templateValues,
            logger = LOG,
            openFile = openFileInIde,
        )
    }
}
