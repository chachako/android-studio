plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
    namespace = "com.example.dependencyLib"

    compileSdk = property("latestCompileSdk") as Int

    defaultConfig {
        minSdk = 21
        targetSdk = property("latestCompileSdk") as Int
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

dependencies {
    implementation(project(":transitiveDependencyLib"))
}
