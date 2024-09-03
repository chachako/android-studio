plugins {
  id("com.android.application")
}
android {
  namespace = "com.example.app"

  compileSdk = libs.versions.latestCompileSdk.get().toInt()

  defaultConfig {
    minSdk = 22
    targetSdk = libs.versions.latestCompileSdk.get().toInt()
    missingDimensionStrategy("type", "typeone")
    missingDimensionStrategy("mode", "modetwo")
  }
}

dependencies {
    implementation(project(":kmpFirstLib"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:0.44")
}
