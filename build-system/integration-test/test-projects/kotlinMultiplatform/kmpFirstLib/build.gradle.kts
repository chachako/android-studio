plugins {
  id("dumpAndroidTarget")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("com.android.lint")
}

kotlin {
  androidLibrary {
    withAndroidTestOnJvm(compilationName = "unitTest")
    withAndroidTestOnDevice(compilationName = "instrumentedTest")

    sourceSets.getByName("androidMain") {
      dependencies {
        api(project(":androidLib"))
        implementation(project(":kmpSecondLib"))
        implementation(project(":kmpJvmOnly"))
      }
    }

    sourceSets.getByName("androidInstrumentedTest") {
      dependencies {
        implementation("androidx.test:runner:1.3.0")
        implementation("androidx.test:core:1.3.0")
        implementation("androidx.test.ext:junit:1.1.2")
      }
    }

    compilations.getByName("instrumentedTest") {
        kotlinOptions.languageVersion = "1.8"
    }
    namespace = "com.example.kmpfirstlib"
    compileSdk = property("latestCompileSdk") as Int
    minSdk = 22

    dependencyVariantSelection {
      buildTypes.add("debug")
      productFlavors.put("type", mutableListOf("typeone"))
      productFlavors.put("mode", mutableListOf("modetwo"))
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    aarMetadata.minAgpVersion = "7.2.0"
  }

   sourceSets.getByName("commonTest") {
     dependencies {
       implementation("junit:junit:4.13.2")
     }
   }
}
