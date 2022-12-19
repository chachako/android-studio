plugins {
  id("com.android.library")
}
android {
  namespace = "com.example.androidlib"

  compileSdk = property("latestCompileSdk") as Int

  defaultConfig {
    minSdk = 21
    targetSdk = property("latestCompileSdk") as Int
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
