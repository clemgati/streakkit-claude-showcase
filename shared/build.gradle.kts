// AGP 9 refuses to apply `com.android.library` alongside the Kotlin Multiplatform plugin, so the
// Android side of this module is configured through `com.android.kotlin.multiplatform.library`
// and the `kotlin { androidLibrary { ... } }` DSL instead of a top-level `android { }` block.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "dev.clement.streakkit.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // Host-side unit tests are opt-in with this plugin. Without this, `commonTest` would never
        // run on the JVM and `./gradlew :shared:allTests` would silently only cover native.
        withHostTestBuilder {}.configure {}
    }

    // Three Apple targets: Intel simulator, device, and Apple-silicon simulator.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Exported so Swift can see the kotlinx types that appear in the public API.
            export(libs.kotlinx.datetime)
            export(libs.kotlinx.coroutines.core)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
