plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// İmza bilgileri GitHub'daki gizli ayarlardan (Secrets) gelir; koda yazılmaz.
val imzaDosyasi = System.getenv("HANIMTV_KEYSTORE_FILE")

android {
    namespace = "tr.hayaloglu.hanimtv"
    compileSdk = 34

    defaultConfig {
        applicationId = "tr.hayaloglu.hanimtv"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("HANIMTV_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("HANIMTV_VERSION_CODE") ?: "1")
        resourceConfigurations += listOf("tr")
    }

    signingConfigs {
        if (imzaDosyasi != null) {
            create("hanim") {
                storeFile = file(imzaDosyasi)
                storePassword = System.getenv("HANIMTV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HANIMTV_KEY_ALIAS")
                keyPassword = System.getenv("HANIMTV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (imzaDosyasi != null) signingConfig = signingConfigs.getByName("hanim")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.2")
}
