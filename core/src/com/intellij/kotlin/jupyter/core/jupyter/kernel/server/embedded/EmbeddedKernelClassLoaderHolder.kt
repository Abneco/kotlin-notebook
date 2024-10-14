// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds classloaders that are responsible for loading kernel classes.
 * Each version of kernel is loaded with its own classloader to avoid
 * conflicts and errors.
 * Each created classloader has Kotlin Notebook plugin's classloader as
 * a parent, that implies that REPL snippets may load classes of plugin and IDE,
 * all you need is to provide a correct classpath for
 * compilation and completion to work.
 */
@Service(Service.Level.PROJECT)
class EmbeddedKernelClassLoaderHolder(private val project: Project) {
    private val classLoaders = ConcurrentHashMap<String, ClassLoader>()

    @RequiresBackgroundThread
    private fun loadClassLoader(kernelVersion: String): ClassLoader {
        ThreadingAssertions.assertBackgroundThread()
        val downloader = KotlinNotebookMavenArtifactsDownloader.getInstance(project)
        val classpath = downloader.downloadArtifactBlocking(KotlinNotebookMavenArtifacts.EMBEDDED_KERNEL, kernelVersion)
        return URLClassLoader(
            classpath.map { file -> file.toURI().toURL() }.toTypedArray(),
            EmbeddedKernelClassLoaderHolder::class.java.classLoader
        )
    }

    fun getClassLoader(kernelVersion: String): ClassLoader {
        return classLoaders.getOrPut(kernelVersion) {
            loadClassLoader(kernelVersion)
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<EmbeddedKernelClassLoaderHolder>()
    }
}
