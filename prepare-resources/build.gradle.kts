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

dependencies {
    kernel(kotlinJupyter("kernel"))
    ideLib(kotlinJupyter("lib")) { isTransitive = false }
    ideLib(kotlinJupyter("api")) { isTransitive = false }
    ideLib(kotlinJupyter("common-dependencies")) { isTransitive = false }

    lib(kotlinJupyter("lib"))
    lib(kotlinJupyter("api"))
    lib(kotlinJupyter("common-dependencies"))
    lib(kotlin("script-runtime:1.7.10"))
}

val resourcesDir = projectDir.parentFile.resolve("resources")

listOf(kernel, ideLib, lib).forEach { conf ->
    val capName = conf.name.capitalized()
    val dirName = buildDir.resolve(conf.name)
    val copyTask = tasks.create<Copy>("copy$capName") {
        from(conf)
        into(dirName)
    }

    tasks.create<Zip>("zip$capName") {
        dependsOn(tasks.build)
        dependsOn(copyTask)

        from(dirName)
        archiveFileName.set(resourcesDir.resolve(conf.name + ".zip").toString())
        outputs.file(archiveFileName.get())
        destinationDirectory.set(resourcesDir)
    }
}

val prepareKernelResources: Task by tasks.creating {
    dependsOn("zipKernel", "zipLib", "zipIdeLib")
}
