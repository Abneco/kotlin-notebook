// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.ShutdownEvalResult
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

@Suppress("UNUSED_PARAMETER")
class EmbeddedReplFactory(
    replSettings: DefaultReplSettings,
    communicationFacility: JupyterCommunicationFacility,
    commManager: CommManagerInternal
) {
    fun createRepl(): ReplForJupyter {
        return ReplMock
    }

    object ReplMock : ReplForJupyter {
        override val currentClassLoader: ClassLoader
            get() = TODO("Not yet implemented")
        override val currentClasspath: Collection<String>
            get() = TODO("Not yet implemented")
        override val displayHandler: DisplayHandler
            get() = TODO("Not yet implemented")
        override val fileExtension: String
            get() = TODO("Not yet implemented")
        override val homeDir: File?
            get() = null
        override val librariesScanner: LibrariesScanner
            get() = TODO("Not yet implemented")
        override val libraryDescriptorsProvider: LibraryDescriptorsProvider
            get() = TODO("Not yet implemented")
        override val libraryResolver: LibraryResolver?
            get() = null
        override val notebook: MutableNotebook
            get() = TODO("Not yet implemented")
        override var outputConfig: OutputConfig
            get() = TODO("Not yet implemented")
            set(value) {}
        override val resolutionInfoProvider: ResolutionInfoProvider
            get() = TODO("Not yet implemented")
        override val runtimeProperties: ReplRuntimeProperties
            get() = TODO("Not yet implemented")
        override val throwableRenderersProcessor: ThrowableRenderersProcessor
            get() = TODO("Not yet implemented")

        override fun checkComplete(code: Code): CheckCompletenessResult {
            TODO("Not yet implemented")
        }

        override suspend fun complete(code: Code, cursor: Int, callback: (CompletionResult) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun <T> eval(execution: ExecutionCallback<T>): T {
            TODO("Not yet implemented")
        }

        override fun evalEx(evalData: EvalRequestData): EvalResultEx {
            TODO("Not yet implemented")
        }

        override fun evalOnShutdown(): List<ShutdownEvalResult> {
            TODO("Not yet implemented")
        }

        override suspend fun listErrors(code: Code, callback: (ListErrorsResult) -> Unit) {
            TODO("Not yet implemented")
        }
    }
}
