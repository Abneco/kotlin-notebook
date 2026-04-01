// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

/**
 * The version of the DuckDB JDBC driver used for dynamic dependency addition in Kotlin notebooks.
 *
 * This driver facilitates SQL operations by acting as an intermediate transporter
 * between the notebook plugin and the kernel. It should be kept in sync with the
 * version used by the plugin itself.
 */
const val DUCKDB_JDBC_DRIVER_VERSION: String = "1.5.1.0"
