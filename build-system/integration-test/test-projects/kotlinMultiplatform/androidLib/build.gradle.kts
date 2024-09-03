plugins {
  id("com.android.library")
}
android {
  namespace = "com.example.androidlib"

  compileSdk = libs.versions.latestCompileSdk.get().toInt()

  defaultConfig {
    minSdk = 21
    targetSdk = libs.versions.latestCompileSdk.get().toInt()
  }

  flavorDimensions("type", "mode")
  productFlavors {
    create("typeone") {
        dimension = "type"
    }
    create("typetwo") {
        dimension = "type"
    }
    create("modeone") {
        dimension = "mode"
    }
    create("modetwo") {
        dimension = "mode"
    }
  }
}
