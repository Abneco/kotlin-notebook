// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.util

import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement


class StopExecutionOnFailureRule(private val optionValue: Boolean): TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val prop = KotlinNotebookApplicationOptions.get()::shouldStopExecutionOnFailure
                val initialValue = prop.get()

                try {
                    prop.set(optionValue)
                    base.evaluate() // Run the test
                } finally {
                    prop.set(initialValue)
                }
            }
        }
    }
}
