// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import org.junit.runners.model.FrameworkMember

// Extension function to find an annotation in the class hierarchy using BFS
inline fun <reified T : Annotation> Class<*>.findAnnotationInHierarchy(): T? {
    val annotationClass = T::class.java

    val queue = ArrayDeque<Class<*>>()
    queue.add(this)

    while (queue.isNotEmpty()) {
        val currentClass = queue.removeFirst()

        val annotation = currentClass.getAnnotation(annotationClass)
        if (annotation != null) {
            return annotation
        }

        // Add all supertypes of the current class to the queue
        val superClass = currentClass.superclass
        if (superClass != null && superClass != Any::class.java) {
            queue.add(superClass)
        }
        queue.addAll(currentClass.interfaces)
    }

    // If no annotation is found, return null
    return null
}



internal inline fun <reified T: Annotation> FrameworkMember<*>.getMethodOrClassAnnotation(): T? {
    val annotation = getAnnotation(T::class.java)
    if (annotation != null) return annotation
    return declaringClass.findAnnotationInHierarchy<T>()
}
