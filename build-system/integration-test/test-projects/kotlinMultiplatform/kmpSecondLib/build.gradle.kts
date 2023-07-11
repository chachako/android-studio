plugins {
  id("dumpAndroidTarget")
  id("kotlin-multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":kmpLibraryPlugin"))
      }
    }
  }

  jvm()

  targets.withType(com.android.build.api.variant.KotlinMultiplatformAndroidTarget::class.java) {
    namespace = "com.example.kmpsecondlib"
    compileSdk = property("latestCompileSdk") as Int
    minSdk = 22

    dependencyVariantSelection {
      buildTypes.add("debug")
    }
  }
}
