plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.zapresponde"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.zapresponde"
        minSdk = 29
        targetSdk = 35
        versionCode = 27
        versionName = "0.9.17"
    }
    // Fixed key so each new APK installs over the previous one (CI would otherwise make a new debug key every build).
    signingConfigs {
        create("fixa") {
            storeFile = file("zapresponde.keystore")
            storePassword = "zapresponde"
            keyAlias = "zapresponde"
            keyPassword = "zapresponde"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixa") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
