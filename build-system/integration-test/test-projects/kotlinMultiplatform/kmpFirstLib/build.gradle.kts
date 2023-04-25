plugins {
  id("dumpAndroidTarget")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  androidExperimental {
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


    options {
      namespace = "com.example.kmpfirstlib"
      compileSdk = property("latestCompileSdk") as Int
      minSdk = 22
      buildTypeMatching.add("debug")
      productFlavorsMatching["type"] = mutableListOf("typeone")
      productFlavorsMatching["mode"] = mutableListOf("modetwo")

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

      aarMetadata.minAgpVersion = "7.2.0"

      enableUnitTest = true
      enableAndroidTest = true

        lint {
            disable += "GradleDependency" // such that we don't flag newly available Kotlin versions etc
            abortOnError = true
            checkTestSources = true
            textReport = true
        }
    }
  }

   sourceSets.getByName("commonTest") {
     dependencies {
       implementation("junit:junit:4.13.2")
     }
   }
}
