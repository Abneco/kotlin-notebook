// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.ZipUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.util.getKotlinNotebookCacheDirectory
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@Service(Service.Level.PROJECT)
class KotlinNotebookMavenArtifactsDownloader(private val project: Project) : Disposable {
    private val remoteRepositories = listOf(RemoteRepositoryDescription.MAVEN_CENTRAL)

    private val onlyJars = setOf(ArtifactKind.ARTIFACT)
    private val onlySources = setOf(ArtifactKind.SOURCES)
    private val onlyZip = setOf(ArtifactKind.ZIP)

    private val downloadJobs = mutableMapOf<ArtifactDescriptionWithVersion, Deferred<List<File>>>()
    private val cacheSearchLock = ReentrantLock()
    private val downloadJobsScope = CoroutineScope(Dispatchers.Default)
    private val preloadJobScope = CoroutineScope(Dispatchers.Default)

    init {
        preloadArtifacts()
        KotlinNotebookProjectOptionsProvider.getInstance(project).addListener(
            object: KotlinNotebookProjectOptionsProvider.Listener {
                override fun onKernelVersionChanged() {
                    preloadArtifacts()
                }
            },
            this,
        )
    }

    @RequiresBackgroundThread
    fun downloadArtifactBlocking(
        artifact: ArtifactDescriptionWithKind,
        version: String = getSelectedKernelVersion(project),
    ): List<File> {
        return runBlocking {
            downloadArtifactAsync(artifact, version)
        }
    }

    suspend fun downloadArtifactAsync(
        artifact: ArtifactDescriptionWithKind,
        version: String = getSelectedKernelVersion(project),
    ): List<File> {
        val artifactWithVersion = ArtifactDescriptionWithVersion(artifact, version)
        return downloadWithCache(artifactWithVersion) { cacheDirectory ->
            downloadAndSaveToDirectory(artifactWithVersion, cacheDirectory)
        }.await()
    }

    @RequiresBackgroundThread
    fun downloadAndUnzipBlocking(
        artifact: ArtifactDescriptionWithKind,
        version: String = getSelectedKernelVersion(project),
    ): List<File> {
        return runBlocking {
            downloadAndUnzipAsync(artifact, version)
        }
    }

    private suspend fun downloadAndUnzipAsync(
        artifact: ArtifactDescriptionWithKind,
        version: String = getSelectedKernelVersion(project),
    ): List<File> {
        assert(artifact.kind.extension == "zip")
        val zipFiles = downloadArtifactAsync(artifact, version)
        val outputDirectory = locateDirectoryForArtifactInCache(ArtifactDescriptionWithVersion(artifact, version), "_extracted")
        val artifacts = outputDirectory.files()
        if (artifacts.isNotEmpty()) {
            return artifacts
        } else {
            val outputDirPath = outputDirectory.toPath()
            for (zipFile in zipFiles) {
                ZipUtil.extract(
                    zipFile.toPath(),
                    outputDirPath,
                    null,
                    true
                )
            }
            return outputDirectory.files()
        }
    }

    override fun dispose() {
        downloadJobsScope.cancel()
        preloadJobScope.cancel()
        downloadJobs.clear()
    }

    private fun preloadArtifacts() {
        preloadJobScope.launch {
            for (artifact in KotlinNotebookMavenArtifacts.all()) {
                downloadArtifactAsync(artifact)
            }
        }
    }

    /**
     * Downloads the artifact with the specified version and saves it to the given directory.
     *
     * @param artifactWithVersion The artifact description with the version to download.
     * @param directory The directory to save the downloaded artifact.
     * @return True if the download and save operation was successful, false otherwise.
     */
    private fun downloadAndSaveToDirectory(
        artifactWithVersion: ArtifactDescriptionWithVersion,
        directory: File,
    ): Boolean {
        val (artifact, version) = artifactWithVersion
        val resolvedLibraryRoots = JarRepositoryManager.loadDependenciesSync(
            project,
            JpsMavenRepositoryLibraryDescriptor(artifact.group, artifact.artifact, version, false, emptyList()),
            artifact.selectKinds(),
            remoteRepositories,
            directory.absolutePath,
        ) ?: return false

        resolvedLibraryRoots.mapNotNull { root ->
            val virtualFile = root.file
            VfsUtilCore.virtualToIoFile(virtualFile)
        }

        return true
    }

    private fun ArtifactDescriptionWithKind.selectKinds() = when (kind) {
        ArtifactKind.ARTIFACT -> onlyJars
        ArtifactKind.SOURCES -> onlySources
        ArtifactKind.ZIP -> onlyZip
        else -> setOf(kind)
    }

    /**
     * Searches for artifacts in the local IDEA cache, downloads them otherwise.
     *
     * @param artifactWithVersion The artifact description with version.
     * @param downloader The downloader that takes a cache location as a parameter and returns a boolean indicating the success of the download.
     * Downloader should save download result in the given location. [downloader] jobs are started inside [downloadJobsScope]
     * @return A deferred list of downloaded files.
     */
    private fun downloadWithCache(artifactWithVersion: ArtifactDescriptionWithVersion, downloader: Downloader): Deferred<List<File>> {
        return cacheSearchLock.withLock {
            downloadJobs.getOrPut(artifactWithVersion) {
                val cacheDirectory = locateDirectoryForArtifactInCache(artifactWithVersion)
                val artifacts = cacheDirectory.files()
                if (artifacts.isNotEmpty()) {
                    CompletableDeferred(artifacts)
                } else {
                    startDownload(cacheDirectory, downloader)
                }
            }
        }
    }

    private fun startDownload(cacheDirectory: File, downloader: Downloader): Deferred<List<File>> {
        return downloadJobsScope.async {
            if (downloader.download(cacheDirectory)) cacheDirectory.files()
            else emptyList()
        }
    }

    private fun File.files() = listFiles()?.toList().orEmpty()

    private fun locateDirectoryForArtifactInCache(
        artifactWithVersion: ArtifactDescriptionWithVersion,
        directorySuffix: String = "",
    ): File {
        val (artifact, version) = artifactWithVersion
        val versionDirectory = project.getKotlinNotebookCacheDirectory().resolve("kernels").resolve(version)
        val artifactDirectoryName = buildString {
            append(artifact.artifact)
            artifact.kind.classifier.takeIf { it.isNotEmpty() }?.let { classifier ->
                append('-')
                append(classifier)
            }
            artifact.kind.extension.takeIf { it.isNotEmpty() }?.let { extension ->
                append('-')
                append(extension)
            }
        }
        val artifactDirectory = versionDirectory.resolve(artifactDirectoryName + directorySuffix)
        artifactDirectory.toFile().mkdirs()
        return artifactDirectory.toFile()
    }

    private fun interface Downloader {
        suspend fun download(cacheLocation: File): Boolean
    }

    private data class ArtifactDescriptionWithVersion(
        val artifact: ArtifactDescriptionWithKind, val version: String,
    )

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookMavenArtifactsDownloader>()
    }
}
