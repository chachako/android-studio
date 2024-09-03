plugins {
  id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.example.transitiveDependencyLib"

    compileSdk = libs.versions.latestCompileSdk.get().toInt()

    defaultConfig {
        minSdk = 21
        targetSdk = libs.versions.latestCompileSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
