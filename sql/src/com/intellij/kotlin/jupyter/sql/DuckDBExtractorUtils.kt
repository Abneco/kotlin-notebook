// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.database.dataSource.DatabaseDriverClasspathManager
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.artifacts.DatabaseArtifactContext
import com.intellij.database.dataSource.validation.DatabaseDriverValidator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.w3c.dom.Document
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class DuckDbRuntime(val driver: Driver, val version: String)

private const val DUCKDB_DRIVER_CLASS = "org.duckdb.DuckDBDriver"

@Volatile
lateinit var duckDbRuntime: DuckDbRuntime
    private set
private val initLock = Any()

/**
 * Creates a unique temporary path for an extracted DuckDB database file.
 *
 * The file is marked with `deleteOnExit()` to avoid accumulating stale files while still allowing
 * external processes (for example notebook kernels) to access it after extraction.
 */
@Throws(IOException::class)
fun createTemporaryFile(): Path {
    val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))
    if (!Files.exists(tmpDir)) {
        Files.createDirectories(tmpDir)
    }
    // We use a unique filename to avoid collisions during concurrent extractions.
    val tempDb = Path.of(System.getProperty("java.io.tmpdir"), "ij_duckdb_extract_" + UUID.randomUUID() + ".duckdb")
    Files.deleteIfExists(tempDb) // File will be created by DriverManager.
    // We do not want to delete it instantly because external processes (like a Python/Kotlin kernel)
    // might still need to read it. Setting it to delete on exit is a safer cleanup strategy here.
    tempDb.toFile().deleteOnExit()
    return tempDb
}

/**
 * Retrieves a DuckDB [Connection] and runs [callback] with it.
 *
 * The connection is created through the DuckDB JDBC driver artifact managed by the database plugin:
 * the driver is downloaded/resolved via the database infrastructure, loaded through a dedicated
 * [URLClassLoader], and then used to open the JDBC connection.
 */
fun retrieveDuckDBConnection(project: Project, callback: Connection.(path: Path, runtime: DuckDbRuntime) -> Unit): Path {
    // Only create driver once to improve performance.
    val rt = if (::duckDbRuntime.isInitialized)
        duckDbRuntime
    else synchronized(initLock) {
        if (::duckDbRuntime.isInitialized) duckDbRuntime
        else initializeDuckDbRuntime(project).also { duckDbRuntime = it }
    }

    val tempDb = createTemporaryFile()
    openDuckDbConnection(rt.driver, tempDb).use { callback(it, tempDb, rt) }
    return tempDb
}

private fun initializeDuckDbRuntime(project: Project): DuckDbRuntime {
    val ds = ensureDuckDbDownloaded(project)
    val jars = resolveDownloadedJars(project, ds)
    val cl = createDuckDbClassLoader(jars)

    val driver = loadDuckDbDriver(cl)
    val version = extractDuckDbDriverVersion(driver)

    return DuckDbRuntime(driver, version)
}

/**
 * Ensures that the DuckDB JDBC driver artifacts are available locally via the database plugin.
 *
 * Returns a temporary [LocalDataSource] configured for DuckDB that is used only for driver
 * resolution/download and classpath discovery.
 */
private fun ensureDuckDbDownloaded(project: Project): LocalDataSource {
    // Driver metadata can vary across IDE products/builds, so we match by id, name, and class hints.
    val duckDriver = DatabaseDriverManager.getInstance().drivers.firstOrNull { d ->
        d.id.equals("duckdb", ignoreCase = true) ||
        d.name.equals("duckdb", ignoreCase = true) ||
        d.driverClass?.contains("duckdb", ignoreCase = true) == true
    } ?: error("DuckDB driver not found in DatabaseDriverManager")

    val ds = LocalDataSource.fromDriver(duckDriver, "jdbc:duckdb:", true)

    // Database-Plugin should check for cached downloads before downloading again.
    object : Task.Modal(project, KotlinJupyterSqlBundle.message("dialog.title.downloading.duckdb.driver"), true) {
        override fun run(indicator: ProgressIndicator) {
            DatabaseDriverValidator.createDownloaderTask(ds, null, project).run(indicator)
        }
    }.queue()

    return ds
}

/**
 * Resolves downloaded DuckDB driver JARs from the database plugin classpath infrastructure.
 *
 * Only existing `.jar` files are returned.
 */
private fun resolveDownloadedJars(project: Project, dataSource: LocalDataSource): List<Path> {
    val context = DatabaseArtifactContext.getInstance(project, dataSource)
    val driver = requireNotNull(dataSource.databaseDriver) { "No driver in data source" }

    val files = DatabaseDriverClasspathManager.getInstance(project)
        .getClasspathElements(driver, context)
        .flatMap { it.classesRootUrls }
        .mapNotNull { url ->
            runCatching { Path.of(URI(url)) }.getOrNull()
        }
        .filter { Files.exists(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }

    require(files.isNotEmpty()) { "No downloaded JARs found in ${context.getLocalDownloadPath()}" }
    return files
}

/**
 * Creates a dedicated classloader for DuckDB JDBC artifacts.
 *
 * The parent classloader is set to the plugin classloader to keep plugin-level visibility while
 * isolating driver classes.
 */
private fun createDuckDbClassLoader(jars: List<Path>): ClassLoader {
    val urls = jars.map { it.toUri().toURL() }.toTypedArray()
    return URLClassLoader(urls, DuckDBExtractor::class.java.classLoader)
}

private fun loadDuckDbDriver(classLoader: ClassLoader): Driver {
    val clazz = Class.forName(DUCKDB_DRIVER_CLASS, true, classLoader)
    return clazz.getDeclaredConstructor().newInstance() as Driver
}

/**
 * Determines the DuckDB driver version for the [loadedDriver] instance that is actually used at runtime.
 *
 * Resolution order:
 * 1. Resolve the loaded driver's code source JAR and read `project.version` from a sibling `.pom` file,
 * 2. Parse the version from that JAR file name,
 * 3. Fall back to package `implementationVersion` metadata.
 */
private fun extractDuckDbDriverVersion(loadedDriver: Driver): String {
    val actualJar = runCatching {
        Path.of(loadedDriver.javaClass.protectionDomain.codeSource.location.toURI())
    }.getOrNull()

    val fromActual = actualJar?.let { jar ->
        readProjectVersionFromSiblingPom(jar)
        ?: extractVersionFromJarName(jar.fileName.toString())
    }
    if (!fromActual.isNullOrBlank()) return fromActual

    // fallback only if location unavailable/non-file
    val fromPkg = loadedDriver.javaClass.`package`?.implementationVersion
    if (!fromPkg.isNullOrBlank()) return fromPkg

    error("Failed to extract DuckDB driver version")
}

/**
 * Reads `project.version` from a `.pom` file located next to [jar], if present.
 */
private fun readProjectVersionFromSiblingPom(jar: Path): String? {
    val jarBaseName = jar.fileName.toString().removeSuffix(".jar")
    val siblingPom = jar.resolveSibling("$jarBaseName.pom")
    if (!Files.exists(siblingPom)) return null

    return runCatching {
        Files.newInputStream(siblingPom).use(::readProjectVersionFromPom)
    }.getOrNull()?.takeIf(String::isNotBlank)
}

/**
 * Parses Maven `project.version` from a POM XML [input].
 */
private fun readProjectVersionFromPom(input: InputStream): String? {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val document: Document = factory.newDocumentBuilder().parse(input)

    val xPath = XPathFactory.newInstance().newXPath()
    val version = xPath.evaluate(
        "/*[local-name()='project']/*[local-name()='version']/text()",
        document,
        XPathConstants.STRING
    ) as String
    return version.trim().ifBlank { null }
}

/**
 * Extracts a version suffix from a DuckDB JDBC JAR file name.
 *
 * For example, `duckdb_jdbc-1.3.1.0.jar` yields `1.3.1.0`.
 */
private fun extractVersionFromJarName(fileName: String): String {
    val base = fileName.removeSuffix(".jar")
    // e.g. duckdb_jdbc-1.3.1.0 -> 1.3.1.0
    return base.substringAfter("-")
}

private fun openDuckDbConnection(driver: Driver, dbFile: Path): Connection {
    val url = "jdbc:duckdb:${dbFile.toUri()}"
    return driver.connect(url, null)
           ?: error("Failed to open connection to DuckDB database at $url")
}
