/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.example.android.kotlin"
    compileSdk = libs.versions.latestCompileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildToolsVersion.get()

    defaultConfig {
        minSdk = 21
        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion
        targetSdk = libs.versions.latestCompileSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlinVersion.get()}")
    androidTestImplementation("androidx.test:core:1.4.0-alpha06")
    androidTestImplementation("androidx.test.ext:junit:1.1.3-alpha02")
    androidTestImplementation("androidx.test:monitor:1.4.0-alpha06")
    androidTestImplementation("androidx.test:rules:1.4.0-alpha06")
    androidTestImplementation("androidx.test:runner:1.4.0-alpha06")
}
