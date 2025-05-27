// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.util.ModifiableParentsClassLoader
import java.io.IOException
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * A custom class loader that extends [ModifiableParentsClassLoader] and supports multiple parent class loaders.
 * This allows delegation to multiple specified class loaders for loading classes and resources.
 */
class MultiDelegatingClassLoader : ModifiableParentsClassLoader() {
    private val parents = mutableListOf<ClassLoader>()

    override fun addParent(parent: ClassLoader) {
        parents.add(parent)
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // First, check if a class is already loaded
        val loaded = findLoadedClass(name)
        if (loaded != null) {
            if (resolve) resolveClass(loaded)
            return loaded
        }

        // Attempt to load a class from each parent in order
        for (parent in parents) {
            try {
                val clazz = parent.loadClass(name)
                if (resolve) resolveClass(clazz)
                return clazz
            } catch (_: ClassNotFoundException) {
                // Try next
            }
        }

        // Class isn't found in any parent
        throw ClassNotFoundException("Class $name not found in any delegate classloader")
    }

    // Optional: restrict resources the same way
    override fun getResource(name: String): URL? {
        for (parent in parents) {
            val resource = parent.getResource(name)
            if (resource != null) return resource
        }
        return null
    }

    override fun getResources(name: String): Enumeration<URL?>? {
        val resources = parents.flatMap {
            try {
                it.getResources(name).toList()
            } catch (_: IOException) {
                emptyList()
            }
        }
        return Collections.enumeration(resources)
    }
}
