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
    compileSdk = property("latestCompileSdk") as Int

    defaultConfig {
      minSdk = 20
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
