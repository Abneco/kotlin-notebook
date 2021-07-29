package org.jetbrains.kotlinx.jupyter.plugin.util

import java.io.File

val File.isNotEmptyDirectory: Boolean
  get() = exists() && isDirectory && list()?.isEmpty()?.not() ?: false
