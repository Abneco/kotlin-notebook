package org.jetbrains.kotlinx.jupyter.plugin.build

import org.http4k.asString
import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.IOException
import java.util.Base64
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ResponseWrapper(
    response: Response,
    val url: String
) : Response by response

fun httpRequest(request: Request, client: HttpHandler): ResponseWrapper {
    val response = client(request)

    return ResponseWrapper(response, request.uri.toString())
}

fun getHttp(url: String) = httpRequest(Request(Method.GET, url), ApacheClient())

fun basicAuthHeader(username: String, password: String): Pair<String, String> {
    val b64 = Base64.getEncoder().encode("$username:$password".toByteArray()).toString(Charsets.UTF_8)
    return "Authorization" to "Basic $b64"
}

fun Request.withHeader(header: Pair<String, String>?): Request {
    if (header == null) return this
    return this.header(header.first, header.second)
}

fun Request.withBasicAuth(username: String, password: String): Request {
    return withHeader(basicAuthHeader(username, password))
}

val Response.text: String get() {
    return body.payload.asString()
}

fun ResponseWrapper.assertSuccessful() {
    if (!status.successful) {
        throw IOException("Http request failed. Url = $url. Response = $text")
    }
}

fun downloadUrl(url: String, file: File) {
    download(Request(Method.GET, url), file)
}

fun download(request: Request, file: File) {
    val response = httpRequest(request, ApacheClient(responseBodyMode = BodyMode.Stream))
    val stream = response.body.stream
    file.parentFile.mkdirs()
    Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
}
