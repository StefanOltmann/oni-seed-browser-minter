plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.git.versioning)
}

group = "org.mapsnotincluded"

gitVersioning.apply {

    refs {
        branch("main") {
            version = "\${commit.short}"
        }
    }

    rev {
        version = "\${commit.short}"
    }
}

kotlin {
    jvmToolchain(jdkVersion = 25)
}

application {

    mainClass.set("MainKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/source"))
        }
    }
}

dependencies {

    implementation(libs.oni.seed.browser.model)

    /*
     * Ktor server
     */
    implementation(libs.bundles.ktor.server)
    implementation(libs.logback.classic)

    /*
     * Ktor client
     */
    implementation(libs.ktor.client.okhttp)

    /*
     * Database
     */
    implementation(libs.sqlite)

    /*
     * JetBrains Exposed ORM
     */
    implementation(libs.bundles.exposed)

    implementation("com.github.luben:zstd-jni:1.5.7-11")

    /*
     * Unit tests
     */
    testImplementation(libs.kotlin.test.junit)
}

// region Version
project.afterEvaluate {

    logger.lifecycle("Generate Version.kt")

    val outputDir = layout.buildDirectory.file("generated/source/").get().asFile

    outputDir.mkdirs()

    val file = File(outputDir.absolutePath, "Version.kt")

    file.printWriter().use { writer ->

        writer.println("const val VERSION: String = \"$version\"")

        writer.flush()
    }
}
// endregion
