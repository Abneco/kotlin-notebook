// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.IdeCompatibleMessageRequestProcessor
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterOutType
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.sendOut
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.CapturingOutputStream
import org.jetbrains.kotlinx.jupyter.protocol.DisabledStdinInputStream
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicLong

class EmbeddedMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    socketManager: JupyterBaseSockets,
    commManager: CommManagerInternal,
    executor: JupyterExecutor,
    executionCount: AtomicLong,
    repl: ReplForJupyter,
): IdeCompatibleMessageRequestProcessor(
    rawIncomingMessage,
    messageFactoryProvider,
    socketManager,
    commManager,
    executor,
    executionCount,
    repl
){
    override fun processInputReply(content: InputReply) {
        socketManager.stdin.setClientReply(incomingMessage.toRawMessage())
    }

    override fun <T> evalWithIO(allowStdIn: Boolean, body: () -> T): T {
        val config = repl.options.outputConfig
        val out = System.out
        val err = System.err
        repl.notebook.beginEvalSession()
        val cell = { repl.notebook.currentCell }

        fun getCapturingStream(stream: PrintStream?, outType: JupyterOutType, captureOutput: Boolean): CapturingOutputStream {
            return CapturingOutputStream(
                stream,
                config,
                captureOutput,
            ) { text ->
                cell()?.appendStreamOutput(text)
                this.sendOut(outType, text)
            }
        }

        val forkedOut = getCapturingStream(out, JupyterOutType.STDOUT, config.captureOutput)
        val forkedError = getCapturingStream(err, JupyterOutType.STDERR, false)
        val userError = getCapturingStream(null, JupyterOutType.STDERR, true)

        fun flushStreams() {
            for (stream in listOf(forkedOut, forkedError, userError)) {
                stream.flush()
                stream.close()
            }
        }

        val printForkedOut = PrintStream(forkedOut, false, "UTF-8")
        val printForkedErr = PrintStream(forkedError, false, "UTF-8")
        val printUserError = PrintStream(userError, false, "UTF-8")

        KernelStreams.setStreams(true, printForkedOut, printUserError)

        System.setOut(printForkedOut)
        System.setErr(printForkedErr)

        val `in` = System.`in`
        System.setIn(if (allowStdIn) stdinIn else DisabledStdinInputStream)
        try {
            return body()
        } finally {
            flushStreams()
            System.setIn(`in`)
            System.setErr(err)
            System.setOut(out)

            KernelStreams.setStreams(false, out, err)
        }
    }
}
