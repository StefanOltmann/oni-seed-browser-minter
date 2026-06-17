import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.mapsnotincluded"

kotlin {

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {

        outputModuleName = "minter"

        browser {

            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path

            commonWebpackConfig {
                outputFileName = "minter.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }

        binaries.executable()
    }

    sourceSets {

        commonMain.dependencies {

            implementation(libs.oni.seed.browser.model)

            /* Compose UI */
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            /* REST client */
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.encoding)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.serialization.protobuf)

            /*
             * ONI Worldgen worldgen reverse-engineered in Rust
             *
             * https://www.npmjs.com/package/@tigin-backwards/oxygen-not-included-worldgen-node
             */
            implementation(npm("@tigin-backwards/oxygen-not-included-worldgen", "3.0.2"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
