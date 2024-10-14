// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.Request
import org.jetbrains.kotlinx.jupyter.common.Response
import org.jetbrains.kotlinx.jupyter.common.ResponseImpl
import org.jetbrains.kotlinx.jupyter.common.Status
import java.net.HttpURLConnection

object IdeaHttpClient : HttpClient {
    override fun makeRequest(request: Request): Response {
        val connectionBuilder = HttpRequests.request(request.url)
            .tuner { connection ->
                connection as HttpURLConnection
                connection.requestMethod = request.method
                request.headers.forEach { (key, value) ->
                    connection.addRequestProperty(key, value)
                }
            }

        request.body?.let { body ->
            connectionBuilder.write(body)
        }

        var statusCode = 200
        val responseText = try {
            connectionBuilder.readString()
        } catch (e: HttpStatusException) {
            statusCode = e.statusCode
            ""
        }

        val status = Status(statusCode)
        return ResponseImpl(status, responseText)
    }
}
