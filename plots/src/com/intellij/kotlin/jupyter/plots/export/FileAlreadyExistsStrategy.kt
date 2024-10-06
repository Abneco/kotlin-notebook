// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

/**
 * Enum representing different strategies to handle scenarios where a file
 * already exists during an operation that attempts to write to a file.
 *
 * - [ASK]: The user will be prompted to decide whether to overwrite the existing file or not.
 * - [OVERWRITE]: The existing file will be overwritten with the new content.
 * - [CREATE_NEW]: A new file will be created, and the existing file remains unchanged.
 * - [SKIP]: The operation will be skipped, and the existing file will remain unchanged.
 */
enum class FileAlreadyExistsStrategy {
    ASK,
    OVERWRITE,
    CREATE_NEW,
    SKIP,
}