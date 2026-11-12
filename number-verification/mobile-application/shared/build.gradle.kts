import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

abstract class GenerateBuildConfig : DefaultTask() {
    @get:Input
    abstract val backendDomain: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val domain = backendDomain.get().trimEnd('/')
        require(domain.startsWith("http://") || domain.startsWith("https://")) {
            "backendDomain must start with http:// or https://, was: $domain"
        }
        val file = outputDirectory.file("com/infobip/napi/config/BuildConfig.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.infobip.napi.config
            |
            |object BuildConfig {
            |    const val BACKEND_DOMAIN = "${domain.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")}"
            |}
            |""".trimMargin()
        )
    }
}

val generateBuildConfig by tasks.registering(GenerateBuildConfig::class) {
    backendDomain = providers.gradleProperty("backendDomain").orElse("http://localhost:8080")
    outputDirectory = layout.buildDirectory.dir("generated/buildConfig/commonMain/kotlin")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.infobip.napi.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildConfig)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigationevent.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

compose.resources {
    // Pinned so generated Res imports don't follow rootProject.name.
    packageOfResClass = "com.infobip.napi.resources"
}
