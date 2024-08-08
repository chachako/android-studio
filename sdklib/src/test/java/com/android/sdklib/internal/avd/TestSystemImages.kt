/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImage
import com.android.testutils.file.recordExistingFile
import java.nio.file.Path
import kotlin.io.path.createDirectories

class PathContext(val basePath: Path) {
  fun write(path: String, contents: String? = null) {
    basePath.resolve(path).recordExistingFile(contents = contents)
  }

  fun createDirectories(path: String) {
    basePath.resolve(path).createDirectories()
  }

  fun path(subPath: String, action: PathContext.() -> Unit) =
    with(PathContext(basePath.resolve(subPath)), action)
}

class TestSystemImages(val sdkHandler: AndroidSdkHandler) {

  /** Defines a system image at the given path, relative to $SDK/system-images. */
  inner class TestSystemImage(relativePath: String, val definition: PathContext.() -> Unit) {
    val path = sdkHandler.location!!.resolve("system-images").resolve(relativePath)

    fun write() {
      with(PathContext(path)) { definition() }
    }

    val image: SystemImage by lazy {
      write()
      with(sdkHandler.getSdkManager(FakeProgressIndicator())) {
        // Force a rescan of the packages
        markInvalid()
        reloadLocalIfNeeded(FakeProgressIndicator())
      }
      sdkHandler.getSystemImageManager(FakeProgressIndicator()).getImageAt(path) as SystemImage
    }
  }

  val api21 =
    TestSystemImage("android-21/default/x86") {
      write("system.img")
      write(AvdManager.USERDATA_IMG)

      path("skins") {
        write("res1/layout")
        write("res2/layout")
        write("sample")
      }
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
  <localPackage path="system-images;android-21;default;x86" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>21</api-level>
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
</ns3:sdk-sys-img>""",
      )
    }

  val api23 =
    TestSystemImage("android-23/default/x86") {
      write("system.img")
      // Include data/ directory, but no userdata.img file
      createDirectories("data")
      path("skins") {
        write("res1/layout")
        write("res2/layout")
        write("sample")
      }
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
            """,
      )
    }

  val api23GoogleApis =
    TestSystemImage("android-23/google_apis/x86_64") {
      write("system.img")
      createDirectories("data")
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
""",
      )
    }

  val api24PlayStore =
    TestSystemImage("android-24/google_apis_playstore/x86_64") {
      write("system.img")
      createDirectories("data")
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
  <localPackage path="system-images;android-24;google_apis_playstore;x86_64" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>24</api-level>
      <tag>
        <id>google_apis_playstore</id>
        <display>Google Play</display>
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
    <display-name>Google APIs with Playstore Intel x86 Atom System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }

  val api24Wear =
    TestSystemImage("android-24/android-wear/x86") {
      write("system.img")
      write(AvdManager.USERDATA_IMG)
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
  <localPackage path="system-images;android-24;android-wear;x86" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>24</api-level>
      <tag>
        <id>android-wear</id>
        <display>Android Wear</display>
      </tag>
      <abi>x86</abi>
    </type-details>
    <revision>
      <major>2</major>
    </revision>
    <display-name>Android Wear Intel x86 Atom System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }

  val api25Wear =
    TestSystemImage("android-25/android-wear/x86") {
      write("system.img")
      write(AvdManager.USERDATA_IMG)
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
  <localPackage path="system-images;android-25;android-wear;x86" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>25</api-level>
      <tag>
        <id>android-wear</id>
        <display>Android Wear</display>
      </tag>
      <abi>x86</abi>
    </type-details>
    <revision>
      <major>2</major>
    </revision>
    <display-name>Android Wear Intel x86 Atom System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }

  val api25WearChina =
    TestSystemImage("android-25/android-wear-cn/x86") {
      write("system.img")
      write(AvdManager.USERDATA_IMG)
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
  <localPackage path="system-images;android-25;android-wear-cn;x86" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>25</api-level>
      <tag>
        <id>android-wear</id>
        <display>Android Wear for China</display>
      </tag>
      <abi>x86</abi>
    </type-details>
    <revision>
      <major>2</major>
    </revision>
    <display-name>Android Wear Intel x86 Atom System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }

  val chromeOs =
    TestSystemImage("chromeos/m60/x86") {
      write("system.img")
      write(AvdManager.USERDATA_IMG)
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01">
  <localPackage path="system-images;chromeos;m60;x86">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>25</api-level>
      <tag>
        <id>chromeos</id>
        <display>Chrome OS</display>
      </tag>
      <abi>x86</abi>
    </type-details>
    <revision>
      <major>1</major>
    </revision>
    <display-name>Chrome OS m60 System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }

  val api33ext4 =
    TestSystemImage("android-33-ext4/google_apis_playstore/x86_64") {
      write("system.img")
      createDirectories("data")
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <localPackage path="system-images;android-33-ext4;google_apis_playstore;x86_64" obsolete="false">
    <type-details xsi:type="sys-img:sysImgDetailsType">
      <api-level>33</api-level>
      <extension-level>4</extension-level>
      <base-extension>false</base-extension>
      <tag>
        <id>google_apis_playstore</id>
        <display>Google Play</display>
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
    <display-name>Google APIs with Playstore Intel x86 Atom System Image</display-name>
  </localPackage>
</sys-img:sdk-sys-img>
""",
      )
    }

  val api34TabletPlayStore =
    TestSystemImage("android-34/google_apis_playstore_tablet/x86_64") {
      write("system.img")
      createDirectories("data")
      // Note that we must use the current schema version here ("/03") for multi-tag to work.
      write(
        "package.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns3:sdk-sys-img xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/03"
                 xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/03"
                 xmlns:ns4="http://schemas.android.com/repository/android/common/02"
                 xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/03">
  <localPackage path="system-images;android-34;google_apis_playstore_tablet;x86_64" obsolete="false">
    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:sysImgDetailsType">
      <api-level>34</api-level>
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
    </type-details>
    <revision>
      <major>9</major>
    </revision>
    <display-name>Tablet Google APIs with Playstore Intel x86 Atom System Image</display-name>
  </localPackage>
</ns3:sdk-sys-img>
""",
      )
    }
}
