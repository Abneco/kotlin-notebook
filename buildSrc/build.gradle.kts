plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val intellijPluginVersion: String by project
val http4kVersion: String by project

dependencies {
    implementation("org.jetbrains.intellij.plugins:gradle-intellij-plugin:$intellijPluginVersion")

    fun http4k(name: String) = api("org.http4k:http4k-$name:$http4kVersion")
    http4k("core")
    http4k("client-apache")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
