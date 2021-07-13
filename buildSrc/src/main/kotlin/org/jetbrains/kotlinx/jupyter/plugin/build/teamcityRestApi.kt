package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.maven
import org.http4k.core.Method
import org.jetbrains.intellij.dependency.PluginsRepositoryConfiguration
import org.http4k.core.Request
import java.io.File

const val PUBLIC_TEAMCITY = "https://teamcity.jetbrains.com"
const val INTERNAL_TEAMCITY = "https://buildserver.labs.intellij.net"

data class BuildLocator(
    val buildId: String,
    val buildNumber: String,
    val branch: String = "default:any"
) {
    val urlPart: String get() = "buildType:(id:$buildId),number:$buildNumber,branch:$branch"
}

interface TeamcityAuth {
    val urlPart: String

    val header: Pair<String, String>? get() = null
}

object GuestAuth: TeamcityAuth {
    override val urlPart: String
        get() = "guestAuth"
}

class HttpAuth(private val username: String, private val password: String): TeamcityAuth {
    override val urlPart: String
        get() = "httpAuth"

    override val header: Pair<String, String>
        get() = basicAuthHeader(username, password)
}

data class TeamcityArtifact(
    val teamcityUrl: String,
    val auth: TeamcityAuth,
    val buildLocator: BuildLocator,
    val artifactPath: String
) {
    val url: String get() {
        return buildString {
            append(teamcityUrl)
            append('/')
            append(auth.urlPart)
            append("/app/rest/builds/")
            append(buildLocator.urlPart)
            append("/artifacts/content/")
            append(artifactPath)
        }
    }

    val request: Request get() {
        return Request(Method.GET, url).withHeader(auth.header)
    }
}

fun RepositoryHandler.tcMaven(
    teamcityUrl: String,
    auth: TeamcityAuth,
    buildLocator: BuildLocator,
    artifactPath: String
) = maven(TeamcityArtifact(teamcityUrl, auth, buildLocator, artifactPath).url)

fun PluginsRepositoryConfiguration.teamcity(
    buildId: String,
    buildNumber: String,
    pathToPluginsRepo: String
) {
    val artifact = TeamcityArtifact(
        INTERNAL_TEAMCITY,
        GuestAuth,
        BuildLocator(buildId, buildNumber),
        pathToPluginsRepo
    )
    custom(artifact.url)
}

fun PluginsRepositoryConfiguration.local(
    localRepoPath: File,
    buildNumber: String,
    pathToPluginsRepo: String,
) {
    val file = localRepoPath
        .resolve(buildNumber)
        .resolve(pathToPluginsRepo)
    custom(file.toURI().toURL().toString())
}

fun Project.downloadTeamcityArtifact(
    localPath: File,
    buildLocator: BuildLocator,
    remotePath: String,
    artifactDebugName: String,
    withAuth: Boolean
): Boolean {
    if (localPath.exists()) return false

    val artifact = if (withAuth) {
        val username = findProperty("teamcity.auth.userId") as String
        val password = findProperty("teamcity.auth.password") as String
        TeamcityArtifact(
            INTERNAL_TEAMCITY,
            HttpAuth(username, password),
            buildLocator,
            remotePath
        )
    } else {
        TeamcityArtifact(
            INTERNAL_TEAMCITY,
            GuestAuth,
            buildLocator,
            remotePath
        )
    }

    return downloadTeamcityArtifact(localPath, artifact, artifactDebugName)
}

fun downloadTeamcityArtifact(
    localPath: File,
    artifact: TeamcityArtifact,
    artifactDebugName: String
): Boolean {
    if (localPath.exists()) return false
    println("Downloading $artifactDebugName artifact: ${artifact.url}")
    download(artifact.request, localPath)
    return true
}

fun Project.unzipArchive(archivePath: File, destPath: File) {
    if (destPath.exists()) return
    copy {
        from(zipTree(archivePath))
        into(destPath)
    }
}

fun Project.unzipArchiveHere(archivePath: File): File {
    val destPath = archivePath.parentFile.resolve(archivePath.nameWithoutExtension)
    unzipArchive(archivePath, destPath)
    return destPath
}

fun Project.useIdeaArchive(archivePath: File, intellijBuildNumber: String): String {
    val doDownload = findProperty("intellij.platform.local.archive.download").isTrue()
    if (doDownload) {
        val buildLocator = BuildLocator("ijplatform_master_Idea_Installers", intellijBuildNumber)
        val withAuth = hasProperty("teamcity.auth.userId")
        downloadTeamcityArtifact(archivePath, buildLocator, archivePath.name, "IDEA", withAuth)
    }
    val destFolder = unzipArchiveHere(archivePath)
    return destFolder.absolutePath
}
