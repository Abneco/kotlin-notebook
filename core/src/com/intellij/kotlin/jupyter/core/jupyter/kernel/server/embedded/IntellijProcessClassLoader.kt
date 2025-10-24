// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_RESOURCES_PATH
import org.jetbrains.kotlinx.jupyter.util.ModifiableParentsClassLoader
import java.io.IOException
import java.net.URL
import java.util.*

class IntellijProcessClassLoader : ModifiableParentsClassLoader() {
    private val parents = mutableListOf<ClassLoader>()

    override fun addParent(parent: ClassLoader) {
        parents.add(parent)
    }

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
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
        if (name.isBlockedResourceName) return null
        for (parent in parents) {
            val resource = parent.getResource(name)
            if (resource != null) return resource
        }
        return null
    }

    override fun getResources(name: String): Enumeration<URL?>? {
        if (name.isBlockedResourceName) return null
        val resources =
            parents.flatMap {
                try {
                    it.getResources(name).toList()
                } catch (_: IOException) {
                    emptyList()
                }
            }
        return Collections.enumeration(resources)
    }

    companion object {
        private val String.isBlockedResourceName: Boolean
            get() = blockedResourceNamePrefixes.any { this.startsWith(it) }

        // We don't want to load any Jupyter integration if it's defined in the dependencies of our plugin
        // or in the IntelliJ platform itself
        private val blockedResourceNamePrefixes = setOf(
            "$KOTLIN_JUPYTER_RESOURCES_PATH/"
        )
    }
}