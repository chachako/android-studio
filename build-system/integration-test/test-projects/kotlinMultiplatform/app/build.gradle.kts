plugins {
  id("com.android.application")
}
android {
  namespace = "com.example.app"

  compileSdk = property("latestCompileSdk") as Int

  defaultConfig {
    minSdk = 22
    targetSdk = property("latestCompileSdk") as Int
    missingDimensionStrategy("type", "typeone")
    missingDimensionStrategy("mode", "modetwo")
  }
}

dependencies {
    implementation(project(":kmpFirstLib"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:0.44")
}
