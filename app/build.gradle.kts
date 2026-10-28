plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.chesspocket"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.chesspocket"
        minSdk = 23
        targetSdk = 28 // debug builds: tolerate old Android + executable packaged native binaries
        versionCode = 1
        versionName = "0.1.0-debug"
        val targetAbi = project.findProperty("targetAbi") as String?
        if (!targetAbi.isNullOrBlank()) ndk { abiFilters += targetAbi }
    }
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs.useLegacyPackaging = true // extracted executable in nativeLibraryDir for ProcessBuilder
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.github.bhlangonijr:chesslib:1.3.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
