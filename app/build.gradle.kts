import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// URL server dibaca dari android/local.properties supaya tidak perlu
// mengubah kode tiap ganti jaringan. File itu tidak ikut Git.
// Contoh isi local.properties:
//   api.baseUrl=http://192.168.1.10:8000
//   api.baseUrl.release=https://form.perusahaan.co.id
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.tambalban.form"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tambalban.form"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // 10.0.2.2 = localhost komputer saat jalan di emulator.
            // Untuk HP fisik, isi api.baseUrl di local.properties.
            val url = localProps.getProperty("api.baseUrl") ?: "http://10.0.2.2:8000"
            buildConfigField("String", "BASE_URL", "\"$url\"")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // WAJIB diganti sebelum rilis: domain produksi yang HTTPS.
            val url = localProps.getProperty("api.baseUrl.release") ?: "https://form.example.com"
            buildConfigField("String", "BASE_URL", "\"$url\"")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
