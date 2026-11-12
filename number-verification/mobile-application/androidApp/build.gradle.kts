import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    debugImplementation(libs.compose.uiTooling)
}

// Release signing comes exclusively from environment variables -- neither the keystore
// nor its credentials ever live in this repository. ANDROID_KEYSTORE_FILE is a path to
// the keystore (absolute, or relative to the root project); the rest are its secrets.
// If any of them is missing the release build stays unsigned, which is fine for local
// verification but cannot be uploaded to Play.
//
// The key entry uses the same password as the keystore: the PKCS12 format keytool
// writes by default cannot hold a separate key password.
fun secret(env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("ANDROID_KEYSTORE_FILE")
val releaseStorePassword = secret("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("ANDROID_KEY_ALIAS")

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
).all { it != null } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.infobip.napi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.infobip.napi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 6
        versionName = "1.3.0"

        // Ship native debug symbols inside the bundle so Play can symbolicate crashes and
        // ANRs in whatever .so files the dependencies bring in. Without this Play warns on
        // every upload. SYMBOL_TABLE keeps function names (a few MB); FULL would add DWARF
        // line numbers and is far larger.
        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseStorePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "androidApp: no release signing configured -- the release build will be " +
                        "UNSIGNED and cannot be uploaded to Google Play. Set ANDROID_KEYSTORE_FILE, " +
                        "ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_ALIAS."
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}