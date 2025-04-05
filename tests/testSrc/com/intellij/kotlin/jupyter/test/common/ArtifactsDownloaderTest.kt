// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.common

import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersionAsString
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
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
        setKernelVersion("0.12.0-117")
    }

    override fun tearDown() {
        try {
            setKernelVersion(currentKernelVersion.toMavenVersion())
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    override fun runInDispatchThread() = false

    @Test
    fun `there should be 5 artifacts`() {
        val artifacts = KotlinNotebookMavenArtifacts.all()
        artifacts shouldHaveSize 6
    }

    @Test
    fun `all artifacts should consist of a single JAR`() {
        runBlocking {
            val artifacts = KotlinNotebookMavenArtifacts.all()
            val version = project.selectedKernelVersionAsString
            artifacts.forEach { artifact ->
                val jars = downloader.downloadArtifactAsync(artifact, version)
                jars shouldHaveSize 1
            }
        }
    }

    @Test
    fun `blocking mode should work the same way`() {
        val artifacts = KotlinNotebookMavenArtifacts.all()
        val version = project.selectedKernelVersionAsString
        artifacts.forEach { artifact ->
            val jars = downloader.downloadArtifactBlocking(artifact, version)
            jars shouldHaveSize 1
        }
    }
}
