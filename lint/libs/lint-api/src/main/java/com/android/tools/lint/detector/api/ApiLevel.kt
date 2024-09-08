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
package com.android.tools.lint.detector.api

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID

/**
 * Represents an `SDK_INT` or `SDK_INT_FULL` integer, containing a specific API level (possibly with
 * minor version).
 */
@JvmInline
value class ApiLevel(val bits: Int) : Comparable<ApiLevel> {
  constructor(major: Int, minor: Int) : this((SDK_INT_MULTIPLIER * major) + minor)

  operator fun component1(): Int = major

  operator fun component2(): Int = minor

  /** Whether this integer represents a dotted number (e.g. 36.0 instead of 36). */
  fun isDotted(): Boolean = bits >= SDK_INT_MULTIPLIER

  val major: Int
    get() =
      if (bits < SDK_INT_MULTIPLIER) {
        bits
      } else if (bits == Integer.MAX_VALUE) Integer.MAX_VALUE else bits / SDK_INT_MULTIPLIER

  val minor: Int
    get() = if (bits >= SDK_INT_MULTIPLIER) bits % SDK_INT_MULTIPLIER else 0

  /** Returns the corresponding version code reference */
  fun toSourceReference(fullyQualified: Boolean = true, kotlin: Boolean = true): String {
    val codeName =
      // 36.0 != 36, look up separately
      if (isDotted()) {
        SdkVersionInfo.getBuildCode(major, minor)
      } else {
        SdkVersionInfo.getBuildCode(major)
      }
    return if (codeName == null) {
      if (kotlin) bits.toKotlinLiteral() else bits.toString()
    } else if (!fullyQualified) {
      codeName
    } else if (isDotted()) {
      "android.os.Build.VERSION_CODES_FULL.$codeName"
    } else {
      "android.os.Build.VERSION_CODES.$codeName"
    }
  }

  private fun Int.toKotlinLiteral(): String {
    val string = this.toString()
    val sb = StringBuilder()
    for ((index, char) in string.withIndex().reversed()) {
      sb.append(char)
      if ((index + 1) % 3 == 0 && index != string.lastIndex) {
        sb.append('_')
      }
    }
    return sb.reverse().toString()
  }

  /** Given an SDK version like 36, returns the constraint X >= 36. */
  fun atLeast(sdkId: Int = ANDROID_SDK_ID): ApiConstraint.SdkApiConstraint? {
    return when {
      this == NONE -> null
      else -> atLeast(this, sdkId)
    }
  }

  override fun compareTo(other: ApiLevel): Int {
    return if (isDotted() == other.isDotted()) {
      bits.compareTo(other.bits)
    } else if (isDotted()) {
      this.compareTo(ApiLevel(other.major, 0))
    } else {
      ApiLevel(major, 0).compareTo(other)
    }
  }

  override fun toString(): String {
    return if (minor > 0) {
      "$major.$minor"
    } else {
      major.toString()
    }
  }

  fun isValid() = bits != 0

  fun isMissing() = bits == 0

  companion object {
    /** Represents no, unknown or missing API level */
    val NONE = ApiLevel(0)

    // 100_000: see current encoding in android.os.Build.VERSION_CODES_FULL.
    // We unfortunately have to operate on actual major+minor knowledge inside lint
    // so we need to decode the constant. See Build.VERSION_CODES_FULL:
    private const val SDK_INT_MULTIPLIER = 100_000

    /** Returns true if the given API level represents a full SDK int rather than an SDK_INT. */
    fun isFullSdkInt(fullSdk: Int): Boolean {
      // Note, we're using > rather than >= since == SDK_INT_MULTIPLIER happens
      // to be equal to Build.VERSION_CODES.CUR_DEVELOPMENT, a "valid" non-minor-carrying
      // version integer.
      return fullSdk > SDK_INT_MULTIPLIER
    }

    /**
     * Get the [ApiLevel] corresponding to the given integer, which can be either an `SDK_INT`
     * representing a whole API level, or an `SDK_INT_FULL` representing a packed major+minor API
     * level.
     */
    fun get(value: Int): ApiLevel {
      return ApiLevel(value)
    }

    fun getMinConstraint(value: Int, sdkId: Int): ApiConstraint.SdkApiConstraint {
      return atLeast(ApiLevel(value), sdkId)
    }

    fun getMinConstraint(
      value: String,
      sdkId: Int,
      recognizeUnknowns: Boolean = true,
    ): ApiConstraint.SdkApiConstraint? {
      return get(value, recognizeUnknowns).atLeast(sdkId)
    }

    /**
     * Maps a [string] like "31" and "36.2" and "UPSIDE_DOWN_CAKE" and "VANILLA_ICE_CREAM_2" to a
     * corresponding [ApiLevel].
     *
     * If [recognizeUnknowns] is true, it will treat a codename it doesn't recognize as probably
     * being the next API level, [SdkVersionInfo.HIGHEST_KNOWN_API] + 1.
     *
     * If it cannot find an ApiLevel, it returns [NONE].
     */
    fun get(string: String, recognizeUnknowns: Boolean = true): ApiLevel {
      when {
        string.isBlank() -> return NONE
        string.first().isDigit() -> {
          val dot = string.indexOf('.')
          return if (dot != -1) {
            val major = string.substring(0, dot).toIntOrNull() ?: return NONE
            if (major < 1) {
              return NONE
            }
            val minor = string.substring(dot + 1).toIntOrNull() ?: return NONE
            ApiLevel(major, minor)
          } else {
            ApiLevel(string.toInt())
          }
        }
        else -> {
          val codeName = string.substringAfterLast('.')
          val underscore = codeName.lastIndexOf('_')
          val hasMinor =
            underscore != -1 &&
              underscore < codeName.length - 1 &&
              codeName[underscore + 1].isDigit()
          if (hasMinor) {
            val minor = codeName.substring(underscore + 1).toIntOrNull() ?: return NONE
            val majorCodeName = codeName.substring(0, underscore)
            val apiLevel = SdkVersionInfo.getApiByBuildCode(majorCodeName, recognizeUnknowns)
            return if (apiLevel != -1) {
              ApiLevel(apiLevel, minor)
            } else {
              NONE
            }
          } else {
            val apiLevel = SdkVersionInfo.getApiByBuildCode(codeName, recognizeUnknowns)
            return if (apiLevel != -1) {
              ApiLevel(apiLevel)
            } else {
              NONE
            }
          }
        }
      }
    }
  }
}
