// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.modcommand.ModCommandAction

/**
 * Creates intention based on FQN.
 * Might return null if a specified intention is not found for a current Kotlin mode.
 */
internal fun createIntention(classFqn: String): IntentionAction? {
    val klass = try {
        Class.forName(classFqn)
    } catch (_: ClassNotFoundException) {
        return null
    }
    val newInstance = klass.getDeclaredConstructor().newInstance()
    return (newInstance as? ModCommandAction)?.asIntention() ?: newInstance as? IntentionAction
    ?: error("Class `$classFqn` has to be IntentionAction or ModCommandAction")
}

internal fun IntentionAction.actionId(): String {
    val asModCommand = asModCommandAction()
    if (asModCommand != null) {
        return asModCommand.javaClass.name
    }

    return when (this) {
        is IntentionActionDelegate -> delegate.actionId()
        else -> javaClass.name
    }
}