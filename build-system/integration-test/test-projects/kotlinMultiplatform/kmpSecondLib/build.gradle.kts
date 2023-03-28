plugins {
  id("dumpAndroidTarget")
  id("org.jetbrains.kotlin.multiplatform")
}

project.plugins.apply(com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin::class.java)

kotlin {
  jvm()

  targets.withType(com.android.build.api.variant.impl.KotlinMultiplatformAndroidTarget::class.java) {
    options {
      namespace = "com.example.kmpsecondlib"
      compileSdk = property("latestCompileSdk") as Int
      minSdk = 22
    }
  }
}
