// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.magics.BasicMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicHandlerFactoryImpl
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext

/**
 * Handles the %logLevel command for setting the logging level.
 */
class EmbeddedLogLevelMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val loggingContext = context.requireContext<Context>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.LOG_LEVEL to ::handleLogLevel,
        )

    /**
     * Handles the %logLevel command, which sets the logging level.
     */
    private fun handleLogLevel() {
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
                loggingContext.loggerFactory.logLevel = level
            }
        }.parse(commandHandlingContext.argumentsList())
    }

    class Context(
        val loggerFactory: EmbeddedKotlinKernelLoggerFactory,
    ): MagicHandlerContext

    companion object : MagicHandlerFactoryImpl(
        ::EmbeddedLogLevelMagicsHandler,
        listOf(
            Context::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
