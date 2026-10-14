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

// Robolectric URL-encodes a space in the user's home path ("Nicholas%20Irawan") and then can't
// find its own Android jar under ~/.m2. Gradle resolves that jar instead, copies it into this
// module's build dir (no spaces), and Robolectric runs offline from there.
val robolectricRuntime = configurations.create("robolectricRuntime")
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val copyRobolectricRuntime = tasks.register<Copy>("copyRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricDepsDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricRuntime)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.absolutePath)
    jvmArgs("--enable-native-access=ALL-UNNAMED") // Robolectric loads SQLite natively
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
    add(robolectricRuntime.name, libs.robolectric.android.all)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
