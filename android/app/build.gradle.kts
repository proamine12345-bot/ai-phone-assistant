plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.aiphoneagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiphoneagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Base URL of the AI layer deployed by this Lovable project.
        // Replace with your published URL, e.g. https://project--<id>.lovable.app
        buildConfigField("String", "AGENT_API_BASE", "\"https://nexos-android-ai.lovable.app\"")
        // Optional shared token; must match the AGENT_API_TOKEN secret on the server.
        buildConfigField("String", "AGENT_API_TOKEN", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Vision layer (OCR) — used when accessibility nodes are not enough (games, canvas UIs)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
