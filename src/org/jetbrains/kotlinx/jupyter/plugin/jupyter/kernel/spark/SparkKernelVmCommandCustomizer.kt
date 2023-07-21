// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.spark

import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelVmCommandCustomizer

class SparkKernelVmCommandCustomizer : KernelVmCommandCustomizer {
    override fun addVmArguments(arguments: MutableList<String>) {
        arguments.apply {
            add("-XX:+IgnoreUnrecognizedVMOptions")
            add("--add-opens=java.base/java.lang=ALL-UNNAMED")
            add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
            add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
            add("--add-opens=java.base/java.io=ALL-UNNAMED")
            add("--add-opens=java.base/java.net=ALL-UNNAMED")
            add("--add-opens=java.base/java.nio=ALL-UNNAMED")
            add("--add-opens=java.base/java.util=ALL-UNNAMED")
            add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED")
            add("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED")
            add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
            add("--add-opens=java.base/sun.nio.cs=ALL-UNNAMED")
            add("--add-opens=java.base/sun.security.action=ALL-UNNAMED")
            add("--add-opens=java.base/sun.util.calendar=ALL-UNNAMED")
            add("--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED")
        }
    }
}