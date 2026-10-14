// :data — Room, DataStore and repository implementations. Everything here is `internal`
// except DataModule (SDD-ARC-002 DD-A02, §8).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "org.nicho.arca.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29 // placeholder, OPEN-A01
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        create("beta") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric
    }
}

room {
    // Exported schema JSON is committed (SDD-ARC-002 §6.5).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
