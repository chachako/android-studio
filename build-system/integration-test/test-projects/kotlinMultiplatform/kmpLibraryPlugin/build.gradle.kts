import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("kotlin-multiplatform")
  id("com.android.library")
}

kotlin {
  androidTarget()
  jvm()
}

android {
    namespace = "com.example.kmplibraryplugin"
    compileSdk = libs.versions.latestCompileSdk.get().toInt()

    defaultConfig {
      minSdk = 20
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType(KotlinCompile::class.java) {
    kotlinOptions {
        jvmTarget = "17"
    }
}
