import org.gradle.configurationcache.extensions.capitalized

plugins {
    kotlin("jvm") version "1.8.0"
}

repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    // mavenLocal()
}

val kernelVersion = defineLibVersion()

fun defineLibVersion(): String {
    val pattern = """"org\.jetbrains\.kotlinx:kotlin-jupyter-shared-compiler\:(.+)"""".toRegex(RegexOption.MULTILINE)
    val projectFile = projectDir.parentFile.resolve("intellij.kotlin.jupyter.iml")
    val imlText = projectFile.readText()
    val match = pattern.findAll(imlText).single()
    val version = match.groupValues[1]
    println("Detected kotlin kernel version: $version")
    return version
}

fun kotlinJupyter(name: String) = "org.jetbrains.kotlinx:kotlin-jupyter-$name:$kernelVersion"

val kernel: Configuration by configurations.creating
val lib: Configuration by configurations.creating
val ideLib: Configuration by configurations.creating

fun ModuleDependency.excludeKotlinDependencies(vararg dependencyNames: String) {
    dependencyNames.forEach {
        exclude("org.jetbrains.kotlin", "kotlin-$it")
    }
}

fun ModuleDependency.excludeStandardKotlinDependencies() {
    excludeKotlinDependencies(
        "stdlib",
        "stdlib-common",
        "kotlin-stdlib-jdk7",
        "kotlin-stdlib-jdk8"
    )
}

fun detectIntellijRoot(): File {
    val pathString = projectDir.absoluteFile.invariantSeparatorsPath
    val intellijPathString = pathString.substringBefore("plugins/")
    val intellijPath = file(intellijPathString)
    println("Detected IntelliJ path: $intellijPath")
    return intellijPath
}

dependencies {
    kernel(kotlinJupyter("kernel"))

    ideLib(kotlinJupyter("lib")) { isTransitive = false }
    ideLib(kotlinJupyter("api")) { isTransitive = false }
    ideLib(kotlinJupyter("common-dependencies")) {
        excludeStandardKotlinDependencies()
    }
    ideLib(kotlin("stdlib"))
    ideLib(kotlin("stdlib-common"))

    lib(kotlinJupyter("lib"))
    lib(kotlinJupyter("api"))
    lib(kotlinJupyter("common-dependencies")) {
        excludeStandardKotlinDependencies()
    }
    lib(kotlin("stdlib"))
    lib(kotlin("script-runtime"))
}

val intellijRoot = detectIntellijRoot()
val outDir = intellijRoot.resolve("out")
val classesDir = outDir.resolve("classes")
val productionOutDir = classesDir.resolve("production/intellij.kotlin.jupyter").apply { mkdirs() }
val testOutDir = classesDir.resolve("test/intellij.kotlin.jupyter.tests").apply { mkdirs() }

val resourcesDir = projectDir.parentFile.resolve("resources")

val zipTasks = mutableListOf<Task>()

listOf(kernel, ideLib, lib).forEach { conf ->
    val capName = conf.name.capitalized()
    val dirName = buildDir.resolve(conf.name)
    val zipFileName = conf.name + ".zip"
    val zipPathInResources = resourcesDir.resolve(zipFileName)

    val zipSrcFileName = conf.name + "Sources.zip"
    val zipSrcPathInResources = resourcesDir.resolve(zipSrcFileName)

    val componentIds = conf.incoming.resolutionResult.allDependencies.map { it.from.id }
    @Suppress("UnstableApiUsage")
    val sourceRequestResult = dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
        .execute()
    val sourcesArtifacts = sourceRequestResult.resolvedComponents.flatMap {
        it.getArtifacts(SourcesArtifact::class).mapNotNull { res ->
            (res as? ResolvedArtifactResult)?.file
        }
    }

    val copyTask = tasks.create<Copy>("copy$capName") {
        from(conf)
        into(dirName)

        doFirst {
            project.delete(files(dirName))
        }
    }

    val zipTask = tasks.create<Zip>("zip$capName") {
        dependsOn(tasks.build)
        dependsOn(copyTask)

        from(dirName)
        archiveFileName.set(zipPathInResources.toString())
        outputs.file(archiveFileName.get())
        destinationDirectory.set(resourcesDir)
    }

    val zipSourcesTask = tasks.create<Zip>("zipSources$capName") {
        dependsOn(tasks.build)

        from(sourcesArtifacts)
        archiveFileName.set(zipSrcPathInResources.toString())
        outputs.file(archiveFileName.get())
        destinationDirectory.set(resourcesDir)
    }

    val copyZipToProdTask = tasks.create<Copy>("copyZipProdOut$capName") {
        dependsOn(zipTask)

        from(zipPathInResources)
        into(productionOutDir)
    }
    val copyZipToTestTask = tasks.create<Copy>("copyZipTestOut$capName") {
        dependsOn(zipTask)

        from(zipPathInResources)
        into(testOutDir)
    }

    val copyZipTask = tasks.create("copyZip$capName") {
        // dependsOn(copyZipToTestTask, copyZipToProdTask)
        dependsOn(zipTask)
        dependsOn(zipSourcesTask)
    }

    zipTasks.add(copyZipTask)
}

val prepareKernelResources: Task by tasks.creating {
    dependsOn(zipTasks)

    doLast {
        println("Resources folder after preparing resources:")
        fileTree(resourcesDir).visit {
            println(file.absolutePath)
        }
    }
}
