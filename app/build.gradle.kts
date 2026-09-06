import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/**
 * Signing credentials.
 *
 * CI supplies them as environment variables; locally they live in an untracked
 * `keystore.properties` at the repo root. Both paths are optional so a fresh clone
 * can always build `assembleDebug` without any secrets present.
 */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun signingSecret(key: String): String? =
    System.getenv(key) ?: keystoreProps.getProperty(key)

val releaseKeystore = rootProject.file("scanrift-release.jks")
val canSignRelease =
    releaseKeystore.exists() &&
        signingSecret("RELEASE_STORE_PASSWORD") != null &&
        signingSecret("RELEASE_KEY_PASSWORD") != null

android {
    namespace = "com.scanrift.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scanrift.android"
        minSdk = 26
        targetSdk = 36
        // Play refuses a re-upload at a versionCode that has already been used, so
        // this only ever goes up, once per release, whether or not Play saw the last one.
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingSecret("RELEASE_STORE_PASSWORD")
                keyAlias = "scanrift"
                keyPassword = signingSecret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        // Keep every locale in the base APK so in-app locale switching keeps working.
        language { enableSplit = false }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Adaptive layouts / foldable support
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.window)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Network / serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // Referenced from shared code behind a BuildConfig.DEBUG check, so it has to be
    // on the release classpath too; R8 strips the branch since DEBUG is a constant.
    implementation(libs.okhttp.logging.interceptor)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit
    implementation(libs.mlkit.text.recognition)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
