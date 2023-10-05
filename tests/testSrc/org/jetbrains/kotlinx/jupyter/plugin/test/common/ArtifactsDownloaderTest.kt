// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.common

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ArtifactsDownloaderTest: BasePlatformTestCase() {
    private val downloader get() = KotlinNotebookMavenArtifactsDownloader.getInstance(project)

    private fun setKernelVersion(version: String) {
        KotlinNotebookProjectOptionsProvider.getInstance(project).state.kernelVersion = version
    }

    override fun setUp() {
        super.setUp()
        setKernelVersion("0.12.0-72")
    }

    override fun runInDispatchThread() = false

    @Test
    fun `there should be 5 artifacts`() {
        val artifacts = KotlinNotebookMavenArtifacts.all()
        UsefulTestCase.assertSize(5, artifacts)
    }

    @Test
    fun `all artifacts should consist of a single JAR`() {
        runBlocking {
            val artifacts = KotlinNotebookMavenArtifacts.all()
            artifacts.forEach { artifact ->
                val jars = downloader.downloadArtifactAsync(artifact)
                UsefulTestCase.assertSize(1, jars)
            }
        }
    }

    @Test
    fun `blocking mode should work the same way`() {
        val artifacts = KotlinNotebookMavenArtifacts.all()
        artifacts.forEach { artifact ->
            val jars = downloader.downloadArtifactBlocking(artifact)
            UsefulTestCase.assertSize(1, jars)
        }
    }
}
