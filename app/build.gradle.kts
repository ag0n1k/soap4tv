plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.soap4tv.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.soap4tv.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        // AGP 8.7.3 ships a lint that crashes on compileSdk=36 with Kotlin
        // analysis-API NoSuchMethodError. Disable the known-broken detector
        // until we upgrade AGP.
        disable += "NullSafeMutableLiveData"
        // Don't fail the release build on lint issues — we still run it manually.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
