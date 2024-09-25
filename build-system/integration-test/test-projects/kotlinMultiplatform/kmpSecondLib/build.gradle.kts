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

  targets.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java) {
    dependencyVariantSelection {
      buildTypes.add("debug")
    }
  }
}

androidComponents {
    finalizeDsl { extension ->
        extension.namespace = "com.example.kmpsecondlib"
        extension.minSdk = 22
        extension.compileSdk = libs.versions.latestCompileSdk.get().toInt()
    }
    onVariant { variant ->
        if (variant.name == null || variant.name.isEmpty()) {
            throw IllegalArgumentException("must have variant name")
        }
    }
}
