plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ana Ekran: stick'in Google TV ana ekranının yerine geçen sade ekran.
// HanımTV ile aynı imza anahtarını kullanır (HanımTV'den "kaldığınız yer" bilgisini okuyabilmek için).
val imzaDosyasi = System.getenv("HANIMTV_KEYSTORE_FILE")

android {
    namespace = "tr.hayaloglu.hanimev"
    compileSdk = 34

    defaultConfig {
        applicationId = "tr.hayaloglu.hanimev"
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
    implementation("androidx.core:core-ktx:1.13.1")
}
