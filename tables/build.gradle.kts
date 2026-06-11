plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDirs("resources", "resources-en")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    intellijPlatform {
        // jupyter/core, jupyter/tables
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        // notebooks/dataframe, python/scientific-tables
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
        // com.intellij.database.extractors.ImageInfo, ColumnDescriptionStatistics, etc.
        bundledPlugin("com.intellij.database")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
