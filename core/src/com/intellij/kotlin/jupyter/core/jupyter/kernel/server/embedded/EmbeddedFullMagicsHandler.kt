// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.IdeCompatibleMagicsHandler
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

/**
 * A class that handles magic commands in an embedded Kotlin kernel.
 * It doesn't yet support %logHandler command, but supports changing log level
 */
class EmbeddedFullMagicsHandler(
    replOptions: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
    private val loggerFactory: EmbeddedKotlinKernelLoggerFactory,
) : IdeCompatibleMagicsHandler(
    replOptions,
    librariesProcessor,
    switcher
) {
    override fun handleLogLevel() {
        object : CliktCommand() {
            val level by argument().choice(
                mapOf(
                    "off" to LogLevel.OFF,
                    "error" to LogLevel.ERROR,
                    "warn" to LogLevel.WARN,
                    "info" to LogLevel.INFO,
                    "debug" to LogLevel.DEBUG,
                    "trace" to LogLevel.TRACE,
                ),
                ignoreCase = false,
            )

            override fun run() {
                loggerFactory.logLevel = level
            }
        }.parse(argumentsList())
    }
}
