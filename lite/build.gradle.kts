import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Folio Lite is the reader for devices that are too old for the main app (from Android 4.0.3).
// It has no Compose and no AndroidX: only platform views and the pdfium library.
android {
    namespace = "com.shreyas.pdfreader.lite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shreyas.pdfreader.lite"
        minSdk = 15
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "1.0"
        // Old devices are 32-bit ARM. One ABI keeps the APK at about 3 MB in place of 19 MB.
        ndk { abiFilters += "armeabi-v7a" }
    }

    // Same release key as the main app. See app/build.gradle.kts.
    val keystorePath = System.getenv("FOLIO_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FOLIO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOLIO_KEY_ALIAS")
                keyPassword = System.getenv("FOLIO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // The old support library is left out. The one class that pdfium needs from it is in
    // src/main/java/android/support/v4/util/ArrayMap.java.
    implementation(libs.pdfium.android) {
        exclude(group = "com.android.support")
    }

    testImplementation(libs.junit)
}
