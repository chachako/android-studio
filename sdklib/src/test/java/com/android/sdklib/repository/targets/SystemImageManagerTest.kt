/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repository.targets

import com.android.SdkConstants
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.testing.TestSystemImages
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [SystemImageManager] */
class SystemImageManagerTest {
  val sdkRoot = createInMemoryFileSystemAndFolder("sdk")
  val handler = AndroidSdkHandler(sdkRoot, null)
  val testImages = TestSystemImages(handler)

  @After
  fun tearDown() {
    testImages.progress.assertNoErrorsOrWarnings()
  }

  @Test
  fun verifyPlatform13() {
    val img = platform13.image

    assertEquals("armeabi", img.primaryAbiType)
    assertNull(img.addonVendor)
    assertEquals(sdkRoot.resolve("platforms/android-13/images/"), img.location)
    assertEquals("default", img.tag.id)
    assertEquals(2, img.skins.size)
  }

  @Test
  fun verifyTvAddon13() {
    val img = googleTvAddon13.image

    assertEquals("x86", img.primaryAbiType)
    assertEquals("google", img.addonVendor!!.id)
    assertEquals(
      sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/images/x86/"),
      img.location,
    )
    assertEquals("google_tv_addon", img.tag.id)
  }

  @Test
  fun verifyGoogleApisSysImg23() {
    val img = googleApisSysImg23.image

    // ABIs should be read from build.prop
    assertEquals("x86_64", img.primaryAbiType)
    assertThat(img.abiTypes).containsExactly("x86_64", "x86")
    assertThat(img.translatedAbiTypes).containsExactly(SdkConstants.ABI_ARM64_V8A)

    assertEquals("google", img.addonVendor!!.id)
    assertEquals(sdkRoot.resolve("system-images/android-23/google_apis/x86_64/"), img.location)
    assertEquals("google_apis", img.tag.id)
  }

  @Test
  fun verifySysImg23() {
    val img = sysImg23.image

    assertEquals("x86", img.primaryAbiType)
    assertNull(img.addonVendor)
    assertEquals(sdkRoot.resolve("system-images/android-23/default/x86/"), img.location)
    assertEquals(
      ImmutableList.of(
        sdkRoot.resolve("system-images/android-23/default/x86/skins/res1/"),
        sdkRoot.resolve("system-images/android-23/default/x86/skins/res2/"),
      ),
      img.skins,
    )
    assertEquals("default", img.tag.id)
  }

  @Test
  fun verifySysImg35() {
    val img = googlePlayTabletSysImg35.image

    assertThat(img.abiTypes).containsExactly("x86_64", "x86")
    assertThat(img.translatedAbiTypes).containsExactly(SdkConstants.ABI_ARM64_V8A)
    assertThat(img.tags).containsExactly(SystemImageTags.PLAY_STORE_TAG, SystemImageTags.TABLET_TAG)
  }

  val platform13 =
    with(testImages) {
      TestSdkPackage("platforms/android-13") {
        write("images/system.img")
        write("android.jar")
        write("framework.aidl")
        write("skins/HVGA/layout")
        write("skins/sample.txt")
        write("skins/WVGA800/layout")
        write(
          "sdk.properties",
          """
                sdk.ant.templates.revision=1
                sdk.skin.default=WXGA

                """
            .trimIndent(),
        )
        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns2:sdk-repository xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                                  xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                                  xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                                  xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                <localPackage path="platforms;android-13" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns2:platformDetailsType">
                    <api-level>13</api-level>
                    <layoutlib api="4"/>
                  </type-details>
                  <revision>
                    <major>1</major>
                  </revision>
                  <display-name>API 13: Android 3.2 (Honeycomb)</display-name>
                  <dependencies>
                    <dependency path="tools">
                      <min-revision>
                        <major>12</major>
                      </min-revision>
                    </dependency>
                  </dependencies>
                </localPackage>
              </ns2:sdk-repository>
          """
            .trimIndent(),
        )
      }
    }

  val googleTvAddon13 =
    with(testImages) {
      TestSdkPackage("add-ons/addon-google_tv_addon-google-13") {
        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns5:sdk-addon xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                             xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                             xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                             xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                <localPackage path="add-ons;addon-google_tv_addon-google-13" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:addonDetailsType">
                    <api-level>13</api-level>
                    <vendor>
                      <id>google</id>
                      <display>Google Inc.</display>
                    </vendor>
                    <tag>
                      <id>google_tv_addon</id>
                      <display>Google TV Addon</display>
                    </tag>
                    <default-skin>720p</default-skin>
                  </type-details>
                  <revision>
                    <major>1</major>
                    <minor>0</minor>
                    <micro>0</micro>
                  </revision>
                  <display-name>Google TV Addon, Android 13</display-name>
                </localPackage>
              </ns5:sdk-addon>
          """
            .trimIndent(),
        )
        write("images/x86/system.img")
        path("skins") {
          write("1080p/layout")
          write("sample.txt")
          write("720p-overscan/layout")
        }
      }
    }

  val sysImg23 =
    with(testImages) {
      TestSystemImage("android-23/default/x86") {
        write("system.img")
        write("skins/res1/layout")
        write("skins/sample")
        write("skins/res2/layout")
        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                               xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                               xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                               xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                <localPackage path="system-images;android-23;default;x86" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
                    <api-level>23</api-level>
                    <tag>
                      <id>default</id>
                      <display>Default</display>
                    </tag>
                    <abi>x86</abi>
                  </type-details>
                  <revision>
                    <major>5</major>
                  </revision>
                  <display-name>Intel x86 Atom System Image</display-name>
                </localPackage>
              </ns3:sdk-sys-img>
          """
            .trimIndent(),
        )
      }
    }

  val googleApisSysImg23 =
    with(testImages) {
      TestSystemImage("android-23/google_apis/x86_64") {
        write("system.img")
        write("build.prop", "ro.product.cpu.abilist=x86_64,x86,arm64-v8a")
        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                               xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                               xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                               xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                <localPackage path="system-images;android-23;google_apis;x86_64" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
                    <api-level>23</api-level>
                    <tag>
                      <id>google_apis</id>
                      <display>Google APIs</display>
                    </tag>
                    <vendor>
                      <id>google</id>
                      <display>Google Inc.</display>
                    </vendor>
                    <abi>x86_64</abi>
                  </type-details>
                  <revision>
                    <major>9</major>
                  </revision>
                  <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>
                </localPackage>
              </ns3:sdk-sys-img>
          """
            .trimIndent(),
        )
      }
    }

  val googlePlayTabletSysImg35 =
    with(testImages) {
      TestSystemImage("android-35/google_apis_playstore_tablet/x86_64") {
        write("system.img")
        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02"
                              xmlns:ns12="http://schemas.android.com/sdk/android/repo/sys-img2/04">
                <localPackage path="system-images;android-35;google_apis_playstore_tablet;x86_64" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns12:sysImgDetailsType">
                    <api-level>35</api-level>
                    <extension-level>13</extension-level>
                    <base-extension>true</base-extension>
                    <tag>
                      <id>google_apis_playstore</id>
                      <display>Google Play</display>
                    </tag>
                    <tag>
                      <id>tablet</id>
                      <display>Tablet</display>
                    </tag>
                    <vendor>
                      <id>google</id>
                      <display>Google Inc.</display>
                    </vendor>
                    <abi>x86_64</abi>
                    <abis>x86_64</abis>
                    <abis>x86</abis>
                    <translatedAbis>arm64-v8a</translatedAbis>
                  </type-details>
                  <revision>
                    <major>7</major>
                  </revision>
                  <display-name>Google Play ARM 64 v8a System Image</display-name>
                </localPackage>
              </ns2:repository>
          """
            .trimIndent(),
        )
      }
    }

  val googleApis13 =
    with(testImages) {
      TestSdkPackage("add-ons/addon-google_apis-google-13") {
        write("images/system.img")

        write(
          "package.xml",
          """
              <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
              <ns5:sdk-addon xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                             xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                             xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                             xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                <localPackage path="add-ons;addon-google_apis-google-13" obsolete="false">
                  <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:addonDetailsType">
                    <api-level>13</api-level>
                    <vendor>
                      <id>google</id>
                      <display>Google Inc.</display>
                    </vendor>
                    <tag>
                      <id>google_apis</id>
                      <display>
                        Google APIs
                      </display>
                    </tag>
                  </type-details>
                  <revision>
                    <major>1</major>
                    <minor>0</minor>
                    <micro>0</micro>
                  </revision>
                  <display-name>Google APIs, Android 13</display-name>
                </localPackage>
              </ns5:sdk-addon>
          """
            .trimIndent(),
        )
      }
    }
}
