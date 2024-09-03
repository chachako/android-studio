plugins {
    id("com.android.library")
    id("com.example.apiuser.example-plugin")
}

android {
    namespace = "com.example.lib"
    compileSdk = libs.versions.latestCompileSdk.get().toInt()
    flavorDimensions += "color"
    productFlavors {
        create("yellow") {
        }
    }
}
