import org.gradle.configurationcache.extensions.capitalized

plugins {
    kotlin("jvm") version "1.7.10"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
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
val ideLib: Configuration by configurations.creating {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
}

fun ModuleDependency.excludeKotlinDependencies(vararg dependencyNames: String) {
    dependencyNames.forEach {
        exclude("org.jetbrains.kotlin", "kotlin-$it")
    }
}

dependencies {
    kernel(kotlinJupyter("kernel"))

    ideLib(kotlinJupyter("lib")) { isTransitive = false }
    ideLib(kotlinJupyter("api")) { isTransitive = false }
    ideLib(kotlinJupyter("common-dependencies")) {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common"
        )
    }

    lib(kotlinJupyter("lib"))
    lib(kotlinJupyter("api"))
    lib(kotlinJupyter("common-dependencies"))
    lib(kotlin("script-runtime:1.7.10"))
}

val intellijRoot = projectDir.parentFile.parentFile.parentFile.parentFile.apply { println(this) }
val outDir = intellijRoot.resolve("out")
val classesDir = outDir.resolve("classes")
val productionOutDir = classesDir.resolve("production/intellij.kotlin.jupyter").apply { mkdirs() }
val testOutDir = classesDir.resolve("test/intellij.kotlin.jupyter.tests").apply { mkdirs() }

val resourcesDir = projectDir.parentFile.resolve("resources")

listOf(kernel, ideLib, lib).forEach { conf ->
    val capName = conf.name.capitalized()
    val dirName = buildDir.resolve(conf.name)
    val zipFileName = conf.name + ".zip"
    val zipPathInResources = resourcesDir.resolve(zipFileName)

    val copyTask = tasks.create<Copy>("copy$capName") {
        from(conf)
        into(dirName)
    }

    val zipTask = tasks.create<Zip>("zip$capName") {
        dependsOn(tasks.build)
        dependsOn(copyTask)

        from(dirName)
        archiveFileName.set(zipPathInResources.toString())
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
    }
}

val prepareKernelResources: Task by tasks.creating {
    dependsOn("copyZipKernel", "copyZipLib", "copyZipIdeLib")

    doLast {
        println("Resources folder after preparing resources:")
        fileTree(resourcesDir).visit {
            println(file.absolutePath)
        }
    }
}
