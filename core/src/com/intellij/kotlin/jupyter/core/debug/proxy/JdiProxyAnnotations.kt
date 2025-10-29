// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

/**
 * Annotation for marking special created properties for [JdiObjectReferenceProxy],
 * so that it would be possible to distinguish them from own properties of the [com.sun.jdi.ReferenceType].
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
annotation class JdiProxyArtificialField(val name: String)

/**
 * Annotation for specifying the path to the field in the [com.sun.jdi.ObjectReference] hierarchy
 * to access actual value of the property.
 *
 * Format: `path1.path2.path3...`
 * path1 is expected to be a property of this objected, path2 - of path1's owner, etc.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
annotation class JdiFieldAccessPath(val path: String)