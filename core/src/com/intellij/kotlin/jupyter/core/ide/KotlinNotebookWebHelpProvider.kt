// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide

import com.intellij.openapi.help.WebHelpProvider

private const val KOTLIN_NOTEBOOK_HELP_TOPIC_PREFIX = "kotlin.notebook."
private fun helpTopic(topic: String) = KOTLIN_NOTEBOOK_HELP_TOPIC_PREFIX + topic

enum class KotlinNotebookHelpId(val url: String) {
    NEW_NOTEBOOK_DIALOG("https://www.jetbrains.com/help/idea/kotlin-notebook.html")
}

val KotlinNotebookHelpId.helpTopic: String get() = helpTopic(name.lowercase())

class KotlinNotebookWebHelpProvider: WebHelpProvider() {
    private val helpUrls = KotlinNotebookHelpId.entries
        .associateBy({ it.helpTopic }, { it.url })

    override fun getHelpPageUrl(helpTopicId: String): String? {
        return helpUrls[helpTopicId]
    }

    override fun getHelpTopicPrefix(): String {
        return KOTLIN_NOTEBOOK_HELP_TOPIC_PREFIX
    }
}
