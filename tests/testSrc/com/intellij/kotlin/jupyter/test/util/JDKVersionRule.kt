// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.util

import com.intellij.kotlin.jupyter.core.settings.ui.runtimeJavaSdkVersion
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.JavaSdkVersion
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement


class JDKVersionRule(private val versionPredicate: (JavaSdkVersion) -> Boolean) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val currentJdkVersion = runtimeJavaSdkVersion ?: error("Runtime JDK version can't be determined")
                if (versionPredicate(currentJdkVersion)) {
                    base.evaluate() // Run the test
                } else {
                    thisLogger().info("Test was skipped because JDK version ($currentJdkVersion) is incompatible")
                }
            }
        }
    }
}
