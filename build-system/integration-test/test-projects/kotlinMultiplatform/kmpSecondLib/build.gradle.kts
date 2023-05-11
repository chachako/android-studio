plugins {
  id("dumpAndroidTarget")
  id("kotlin-multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  jvm()

  targets.withType(com.android.build.api.variant.impl.KotlinMultiplatformAndroidTarget::class.java) {
    namespace = "com.example.kmpsecondlib"
    compileSdk = property("latestCompileSdk") as Int
    minSdk = 22
  }
}
