/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.detector.api

class ExtensionSdk(val name: String, val shortName: String?, val id: Int, val reference: String?) :
  Comparable<ExtensionSdk> {
  fun getSdkExtensionField(fullyQualified: Boolean): String {
    reference?.let { fqn ->
      if (fullyQualified) {
        return fqn
      } else {
        return fqn.substringAfterLast('.')
      }
    }
    return getSdkExtensionField(id, fullyQualified)
  }

  companion object {
    /** The SDK id reserved for the Android SDK */
    const val ANDROID_SDK_ID = 0
    val ANDROID_SDK = ExtensionSdk("Android SDK", "Android SDK", ANDROID_SDK_ID, null)
    /**
     * SDK id's 1 through 999999 are reserved for platform SDKs, where the id matches the
     * corresponding API level
     */
    const val MAX_PLATFORM_SDK_ID = 999999
    /**
     * Special marker value identifying not just the Android SDK but that the corresponding version
     * API level corresponds to a packed integer containing both the major and minor parts.
     * Deliberately not -1 since that's used as a "not found" return value.
     */
    const val ANDROID_SDK_ID_WITH_MINOR = -2

    /** Given a major API level, returns the corresponding field name which encodes the version. */
    fun getAndroidVersionField(api: Int, fullyQualified: Boolean): String =
      ApiLevel(api).toSourceReference(fullyQualified)

    /**
     * Given a major API level and a minor API level, returns the corresponding field name which
     * encodes the version. If [fullyQualified] is true, it will include the VERSION_CODES_FULL
     * class name containing the constant. If [requireFull] is false, it will try to use a plain
     * VERSION_CODES constant if the minor level is 0.
     */
    fun getAndroidVersionField(
      api: Int,
      minor: Int,
      fullyQualified: Boolean,
      requireFull: Boolean = true,
      kotlin: Boolean = true,
    ): String =
      if (minor > 0 || requireFull) ApiLevel(api, minor).toSourceReference(fullyQualified, kotlin)
      else ApiLevel(api).toSourceReference(fullyQualified, kotlin)

    fun getSdkExtensionField(sdkId: Int, fullyQualified: Boolean): String {
      if (
        sdkId <= MAX_PLATFORM_SDK_ID
      ) { // For values less than 1_000_000, the sdk ID corresponds to a platform level
        return getAndroidVersionField(sdkId, fullyQualified)
      }
      return sdkId.toString()
    }

    fun serialize(sdk: ExtensionSdk): String {
      return "${sdk.id};${sdk.name};${sdk.shortName};${sdk.reference ?: ""}"
    }

    fun deserialize(s: String): ExtensionSdk {
      val index = s.indexOf(';')
      val index2 = s.indexOf(';', index + 1)
      val index3 = s.indexOf(';', index2 + 1)
      val id = s.substring(0, index).toInt()
      val name = s.substring(index + 1, index2)
      val shortName = s.substring(index2 + 1, index3).ifEmpty { name }
      val reference = if (index3 < s.length - 1) s.substring(index3 + 1) else null
      return ExtensionSdk(name, shortName, id, reference)
    }
  }

  override fun toString(): String {
    return name
  }

  override fun hashCode(): Int {
    return id
  }

  override fun equals(other: Any?): Boolean {
    return other is ExtensionSdk && id == other.id
  }

  override fun compareTo(other: ExtensionSdk): Int {
    return id.compareTo(other.id)
  }
}

class ExtensionSdkRegistry(val sdks: List<ExtensionSdk>) {
  fun find(id: Int): ExtensionSdk? {
    return sdks.firstOrNull() { it.id == id }
  }
}
