// :app — Compose UI, ViewModels, navigation, platform surfaces, Hilt wiring (SDD-ARC-002 DD-A02).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.nicho.arca"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.nicho.arca"
        minSdk = 29 // placeholder, OPEN-A01
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // debug, beta and release install side by side (SDD-ARC-002 §5.3).
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "DEMO_DATA_ENABLED", "true")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            buildConfigField("boolean", "DEMO_DATA_ENABLED", "false")
        }
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DEMO_DATA_ENABLED", "true")
            // TODO: replace with a real signing config before sharing a beta APK.
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // per-app language (LanguageController)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Platform surfaces (Phase 4)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json) // type-safe navigation routes

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
