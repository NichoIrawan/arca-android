// :testing — shared test fixtures: in-memory stores, TestDataBuilder (SDD-ARC-002 §5.1).
// Used only from test source sets; never shipped.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
}
