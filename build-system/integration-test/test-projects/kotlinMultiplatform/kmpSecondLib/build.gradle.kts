plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.experimental.kotlin.multiplatform.library")
}

kotlin {
  jvm()

  androidPrototype {
    options {
      namespace = "com.example.kmpsecondlib"
      compileSdk = property("latestCompileSdk") as Int
      minSdk = 22
    }
  }
}
