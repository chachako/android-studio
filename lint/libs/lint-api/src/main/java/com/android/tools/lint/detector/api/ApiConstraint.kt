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

import com.android.SdkConstants
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint.Companion.deserialize
import com.android.tools.lint.detector.api.ApiConstraint.SdkApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.SdkApiConstraint.Companion.deserialize
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Expresses an API constraint, such as "API level must be at least 21", or "API level must be 24 or
 * 25" or "API level must be less than 31". This is usually referring to the Android platform API
 * level, but it can also refer to SDK extension API versions.
 *
 * The [ApiConstraint] is used in several contexts:
 * * The `minSdkVersion` of an app is an [ApiConstraint]; for example, `minSdkVersion="31"` in the
 *   manifest is recorded as [Project.getMinSdkVersions] returning an [SdkApiConstraint] of "API
 *   level ≥ 31".
 * * If code performs an explicit `SDK_INT` check, this will be modeled as an [ApiConstraint] by
 *   [VersionChecks]. For example, in the code snippet "SDK_INT < 31 || methodCall()", the version
 *   constraint computed for `methodCall()` will be "API level ≥ 31".
 * * When methods are introduced in later versions of an API, these are also modeled as API
 *   constraints; e.g. a method introduced in API level 32 has the requirement API constraint "API
 *   level ≥ 32".
 *
 * Note that API constraints aren't always simple "X >= Y" relationships. They are modeled as
 * bitvectors, so for example, in the following code:
 * ```
 * when (SDK_INT) {
 *     in 1..14 -> { }
 *     16 -> { }
 *     in 17..20 -> { }
 *     else -> methodCall()
 * }
 * ```
 *
 * the [ApiConstraint] computed for the `methodCall` site knows that `SDK_INT` is either 15
 * or >= 21.
 *
 * There are a number of operations defined on [ApiConstraint]s. For example, [isAtLeast] returns
 * true if the receiver API level is at least as high as the parameter API level.
 *
 * In the normal case where you're referring to a simple Android API, this is just an integer
 * comparison -- is manifest minSdkVersion at least as high as the introduced-in API level for the
 * API? But with mainline modules, APIs can be backported and appear in many different SDKs. This is
 * handled by the [ApiConstraint] class as a "vector" of API levels. Both the minSdkVersion and the
 * required API can include multiple API levels, and the [isAtLeast] method checks that the
 * requirements are satisfied.
 */
sealed class ApiConstraint {
  /**
   * The lowest API level included in the constraint. E.g. for "X >= 21" it's 21, for "X < 15" it's
   * 1, and for [NONE] and [UNKNOWN] it's -1.
   *
   * Alias for [fromInclusive] which makes some code clearer.
   */
  fun min(): Int = fromInclusive()

  /**
   * The lowest API level included in the constraint. E.g. for "X >= 21" it's 21, for "X < 15" it's
   * 1, and for [NONE] or [UNKNOWN] it's -1.
   *
   * **This method should not be called on a multi-version constraint**. For backwards
   * compatibility, this will attempt to return the lowest API level for the constraint
   * corresponding to the Android SDK (if included in the multi constraint); otherwise, it will
   * return -1.
   */
  abstract fun fromInclusive(): Int

  /**
   * The **minor version** of the lowest API level included in the constraint. E.g. for "X >= 21.2"
   * it's 2, for "X < 15" is 0".
   */
  abstract fun fromInclusiveMinor(): Int

  /**
   * The highest API level included in the constraint. E.g. for "X < 15" it's 15. For "X > 15" it
   * will return the highest value representable in the API level data structures; this is not
   * Integer.MAX_VALUE.
   *
   * **This method should not be called on a multi-version constraint**. For backwards
   * compatibility, this will attempt to return the highest API level for the constraint
   * corresponding to the Android SDK (if included in the multi constraint); otherwise, it will
   * return -1.
   */
  abstract fun toExclusive(): Int

  /**
   * The **minor version** of the highest API level included in the constraint. E.g. for "X < 15.2"
   * it's 2, for "X >= 21.5" it's 0.
   */
  abstract fun toExclusiveMinor(): Int

  /**
   * Is this [ApiConstraint] at least as high as the given [constraint] ?
   *
   * For example, let's say `this` is the constraint `SDK_INT >= 31`, constructed from
   * `minSdkVersion = 31` in the manifest. And let's say [constraint] is `SDK_INT >= 28`, the
   * since-requirement for a new method introduced in API level 28. Here,
   * `this.isAtLeast(constraint)` is true, because 31 >= 28.
   */
  abstract fun isAtLeast(constraint: ApiConstraint): Boolean

  /** Does this API constraint include the given [apiLevel] ? */
  abstract fun includes(apiLevel: Int): Boolean

  /**
   * Will this API level or anything higher always match this constraint?
   *
   * For example, if we know from minSdkVersion that SDK_INT >= 32, and we see a check if SDK_INT
   * is >= 21, that check will always be true. That's what this method is for; this [ApiConstraint]
   * is the SDK_INT check, and the passed in [minSdk] represents the minimum value of SDK_INT (the
   * known constraint from minSdkVersion).
   */
  abstract fun alwaysAtLeast(minSdk: ApiConstraint): Boolean

  /** Inverts the given constraint, e.g. X < 20 becomes X >= 20. */
  abstract operator fun not(): ApiConstraint

  /**
   * Whether this constraint can be negated (using [not]). For example, the [ApiConstraint] derived
   * from `SDK_INT >= 24` is negatable; in the `else` clause of `if (SDK_INT >= 24)` we can negate
   * the constraint and conclude that `SDK_INT < 24`.
   *
   * However, for the constraint derived from something like `SDK_INT >= 24 && day == TUESDAY`, the
   * constraint itself should not be negated. This is basically expressing something about the
   * *context* of the constraint, which we bundle with the constraint itself to make it easier to
   * pass constraints around. For example, the various [VersionChecks] utility methods which
   * recursively compute [ApiConstraint] objects for elements can set this bit when combining
   * contains in `&&` expressions.
   */
  abstract fun negatable(): Boolean

  /**
   * Marks this [ApiConstraint] as not being safe to negate; see [negatable] for more information.
   */
  abstract fun asNonNegatable(): ApiConstraint

  abstract fun isEmpty(): Boolean

  /** Returns a new constraint which takes the union of the two constraints. */
  abstract infix fun or(other: ApiConstraint?): ApiConstraint

  /** Returns a new constraint which takes the intersection of the two constraints. */
  abstract infix fun and(other: ApiConstraint?): ApiConstraint

  /**
   * Serializes this constraint into a String, which can later be retrieved by calling
   * [deserialize].
   */
  abstract fun serialize(): String

  /** Creates a string representation of this constraint, suitable for display to the user. */
  abstract override fun toString(): String

  /** String representation of this constraint, including logical SDK names */
  abstract fun toString(sdkRegistry: ExtensionSdkRegistry? = null): String

  /** Returns the string of the lowest API level included. */
  fun minString(): String {
    val sb = StringBuilder()
    sb.append(fromInclusive().toString())
    val minor = fromInclusiveMinor()
    if (minor > 0) {
      sb.append('.').append(minor.toString())
    }
    return sb.toString()
  }

  /** Returns the specific constraint for the given SDK id, if any. */
  abstract fun findSdk(sdkId: Int, certain: Boolean = false): SdkApiConstraint?

  /** Returns the SDK id for this ApiConstraint; returns -1 for multi-SDK constraints. */
  abstract fun getSdk(): Int

  /**
   * If this is a multi-SDK constraint, returns a list of all the individual constraints; if not,
   * returns self.
   */
  abstract fun getConstraints(): List<SdkApiConstraint>

  @Deprecated(
    "Use the ApiConstraint version instead to make sure you're checking the right SDK extension",
    ReplaceWith(
      "isAtLeast(ApiConstraint.get(apiLevel,com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID))"
    ),
  )
  open fun isAtLeast(apiLevel: Int): Boolean = isAtLeast(atLeast(apiLevel, 0, getSdk()))

  @Deprecated(
    "Use the ApiConstraint version instead to make sure you're checking the right SDK extension",
    ReplaceWith(
      "alwaysAtLeast(ApiConstraint.get(apiLevel,com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID))"
    ),
  )
  abstract fun alwaysAtLeast(apiLevel: Int): Boolean

  @Deprecated(
    "Use the ApiConstraint version instead to make sure you're checking the right SDK extension",
    ReplaceWith(
      "everHigher(ApiConstraint.get(apiLevel,com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID))"
    ),
  )
  abstract fun everHigher(apiLevel: Int): Boolean

  /**
   * Find the first missing or unsatisfied [SdkApiConstraint] from the given [requirement]. Should
   * only be called on an [ApiConstraint] where [isAtLeast] returned false for the given
   * [requirement].
   */
  abstract fun firstMissing(requirement: ApiConstraint): SdkApiConstraint?

  companion object {
    /**
     * Special constraint value which means that there are no valid API levels. This is for example
     * the case inside "if (SDK_INT < 5 && SDK_INT > 5)".
     */
    @JvmField val NONE: SdkApiConstraint = SdkApiConstraint.NO_LEVELS

    /**
     * Special constraint value which means that the API is unknown or the constraint is unknown.
     *
     * This is not a valid level, only a marker, so you cannot call operations on it like
     * [isAtLeast], [and], etc.
     */
    @JvmField val UNKNOWN: SdkApiConstraint = SdkApiConstraint(Intervals.NONE, -2)

    /** All versions allowed (1; always available) */
    @JvmField val ALL: SdkApiConstraint = atLeast(1)

    // 99.9% of API lookups are for simple API levels, not extensions; we pre-create these
    private val simpleApiLevels = List(SdkVersionInfo.HIGHEST_KNOWN_API + 1, ::atLeast)

    /** Returns a SDK_INT >= [apiLevel] constraint. */
    @JvmStatic
    fun get(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      if (apiLevel == -1) {
        return UNKNOWN
      } else if (apiLevel < simpleApiLevels.size && sdkId == ANDROID_SDK_ID) {
        return simpleApiLevels[apiLevel]
      }
      return atLeast(apiLevel, sdkId)
    }

    /** Nullable version of the [ApiConstraint.max] method. */
    @JvmName("maxNullable")
    fun max(api1: ApiConstraint?, api2: ApiConstraint?, either: Boolean = false): ApiConstraint? {
      if (api1 == null) return api2
      return max(api1, api2, either)
    }

    /**
     * Returns the max of two constraints, across SDKs. If we're joining constraints, the [either]
     * flag indicates whether we only know that either constraint is available or required, rather
     * than both.
     */
    fun max(api1: ApiConstraint, api2: ApiConstraint?, either: Boolean = false): ApiConstraint {
      assert(api1 !== UNKNOWN)
      assert(api2 !== UNKNOWN)
      when {
        api2 == null -> return api1
        api1.isEmpty() -> return NONE
        api2.isEmpty() -> return NONE
        else -> {
          when (api1) {
            is SdkApiConstraint -> {
              return if (api2 is SdkApiConstraint) {
                val sdkId = api1.sdkId
                if (sdkId == api2.sdkId) {
                  SdkApiConstraint(api1.intervals and api2.intervals, sdkId)
                } else {
                  MultiSdkApiConstraint(
                    listOf(
                      SdkApiConstraints(sdkId, api1, either),
                      SdkApiConstraints(api2.sdkId, api2, either),
                    )
                  )
                }
              } else {
                // Reverse args and recurse such that we only need to handle this combination in one
                // direction (below)
                max(api2, api1, either)
              }
            }
            is MultiSdkApiConstraint -> {
              when (api2) {
                is SdkApiConstraint -> {
                  val match =
                    api1.findSdks(api2.sdkId)
                      ?: return MultiSdkApiConstraint(
                        api1.sdkConstraints + SdkApiConstraints(api2.sdkId, api2, either)
                      )
                  val apis: List<SdkApiConstraints> =
                    api1.sdkConstraints
                      .map {
                        if (it === match) {
                          it.andWith(api2, either)
                        } else it
                      }
                      .toList()
                  return MultiSdkApiConstraint(apis)
                }
                is MultiSdkApiConstraint -> {
                  val apis =
                    api1.sdkConstraints
                      .map { have ->
                        val match = api2.findSdks(have.sdkId)
                        if (match != null) {
                          have and match
                        } else {
                          have
                        }
                      }
                      .toMutableList()
                  // Finally, pick up the SDKs that are in api2 and not api1, and add those too.
                  api2.sdkConstraints.forEach { if (api1.findSdks(it.sdkId) == null) apis.add(it) }
                  return MultiSdkApiConstraint(apis)
                }
              }
            }
          }
        }
      }
    }

    /**
     * Returns true if the given level (typically returned from [fromInclusive] or [toExclusive]
     * represents infinity, e.g. it's an open-ended interval such as `x >= 10`, represented as the
     * interval `[x, ∞)`.
     */
    fun isInfinity(level: Int): Boolean {
      return Intervals.isInfinity(level)
    }

    /** Create constraint where the API level is at least [apiLevel]. */
    fun atLeast(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return atLeast(apiLevel.major, apiLevel.minor, sdkId)
    }

    /** Create constraint where the API level is at least [apiLevel]. */
    @JvmStatic
    fun atLeast(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(fromInclusive = apiLevel, sdkId = sdkId)
    }

    /** Create constraint where the API level is at least [apiLevel].[minorLevel] in SDK [sdkId]. */
    @JvmStatic
    fun atLeast(apiLevel: Int, minorLevel: Int, sdkId: Int): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        fromInclusive = apiLevel,
        fromInclusiveMinor = minorLevel,
        sdkId = sdkId,
      )
    }

    /** Create constraint where the API level is less than [apiLevel]. */
    fun below(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return below(apiLevel.major, apiLevel.minor, sdkId)
    }

    /** Create constraint where the API level is less than [apiLevel].[minorLevel] in SDK [sdkId] */
    fun below(apiLevel: Int, minorLevel: Int, sdkId: Int): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        toExclusive = apiLevel,
        toExclusiveMinor = minorLevel,
        sdkId = sdkId,
      )
    }

    /** Create constraint where the API level is higher than [apiLevel]. */
    fun above(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      // Note that the two construction methods here have different semantics --
      // calling above(4) and above(4,0) return different constraints. If you
      // have a traditional constraint, `x > 4` -- that historically meant `x
      // >= 5`. But with minor versions, you could also mean `x >= 4.1`. Which
      // is it? Here we're handling this by treating SDK_INT_FULL operations
      // (identified by ANDROID_SDK_ID_WITH_MINOR over in VersionChecks) as
      // operating on real numbers and SDK_INT as operating on whole numbers.
      // This is covered by unit tests, where for example `if (SDK_INT <=
      // $LEVEL36) ... else { code }` infers SDK_INT >= 37 in the else clause and
      // will not flag attempts to use 36.2 code. But with a `if (SDK_INT_FULL
      // <= $LEVEL36_FULL) else` block, we'll infer SDK_INT >= 36.1, and code
      // referencing 36.2 APIs would be flagged as wrong.
      return if (apiLevel.isDotted()) {
        above(apiLevel.major, apiLevel.minor, sdkId)
      } else {
        above(apiLevel.major, sdkId)
      }
    }

    /** Create constraint where the API level is higher than [apiLevel]. */
    fun above(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(fromInclusive = apiLevel + 1, sdkId = sdkId)
    }

    /**
     * Create constraint where the API level is higher than [apiLevel].[minorLevel]. Notice how this
     * isn't just adding a decimal to the other version of this method; it instead treats the
     * constraint as being on the decimal part. E.g. for "X > 4", the above level-only method
     * returns "X >= 5", and this method returns "X >= 4.1".
     */
    fun above(apiLevel: Int, minorLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        fromInclusive = apiLevel,
        fromInclusiveMinor = minorLevel + 1,
        sdkId = sdkId,
      )
    }

    /** Create constraint where the API level is lower than or equal to [apiLevel]. */
    fun atMost(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      // atMost(21) => X < 21, and atMost(21,0) => X < 21.1
      return if (apiLevel.isDotted()) {
        atMost(apiLevel.major, apiLevel.minor, sdkId)
      } else {
        atMost(apiLevel.major, sdkId)
      }
    }

    /** Create constraint where the API level is lower than or equal to [apiLevel]. */
    fun atMost(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(toExclusive = apiLevel + 1, sdkId = sdkId)
    }

    /** Create constraint where the API level is lower than or equal to [apiLevel].[minorLevel]. */
    fun atMost(apiLevel: Int, minorLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        toExclusive = apiLevel,
        toExclusiveMinor = minorLevel + 1,
        sdkId = sdkId,
      )
    }

    /** Create constraint where the API level is in the given range. */
    fun range(
      fromInclusive: ApiLevel,
      toExclusive: ApiLevel,
      sdkId: Int = ANDROID_SDK_ID,
    ): SdkApiConstraint {
      return range(
        fromInclusive.major,
        fromInclusive.minor,
        toExclusive.major,
        toExclusive.minor,
        sdkId,
      )
    }

    /** Create constraint where the API level is in the given range. */
    fun range(
      fromInclusive: Int,
      fromInclusiveMinor: Int,
      toExclusive: Int,
      toExclusiveMinor: Int,
      sdkId: Int = ANDROID_SDK_ID,
    ): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        fromInclusive,
        fromInclusiveMinor,
        toExclusive,
        toExclusiveMinor,
        sdkId = sdkId,
      )
    }

    /** Creates an API constraint where the API level equals a specific level. */
    fun exactly(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      // Note that exactly(21) and exactly(21,0) are not the same; if we take the
      // .not() of these constraints, the first is 1 < x < 21 or x > 22, and the second
      // is 1 < x < 21 or x > 21.1.
      return if (apiLevel.isDotted()) {
        exactly(apiLevel.major, apiLevel.minor, sdkId)
      } else {
        exactly(apiLevel.major, sdkId)
      }
    }

    /** Creates an API constraint where the API level equals a specific level. */
    fun exactly(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(apiLevel, apiLevel + 1, sdkId = sdkId)
    }

    /**
     * Creates an API constraint where the API level equals a specific level. Note that the version
     * which doesn't take a minor level will model this as the range from X to X + 1. If x is 4,
     * this is 4 <= x <= 5. But this version will work on minor versions instead, in other words, if
     * x is 4.0, this models the range 4.0 <= x <= 4.1.
     */
    fun exactly(apiLevel: Int, minorLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return SdkApiConstraint.createConstraint(
        apiLevel,
        minorLevel,
        apiLevel,
        minorLevel + 1,
        sdkId = sdkId,
      )
    }

    /**
     * Creates an API constraint where the API level is **not** a specific value (e.g. API !=
     * apiLevel).
     */
    fun not(apiLevel: ApiLevel, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return if (apiLevel.isDotted()) {
        not(apiLevel.major, apiLevel.minor, sdkId)
      } else {
        not(apiLevel.major, sdkId)
      }
    }

    fun not(apiLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return exactly(apiLevel, sdkId).not()
    }

    /**
     * Creates an API constraint where the API level is **not** a specific value (e.g. API !=
     * apiLevel). See the [exactly] documentation to explain the difference between specifying a
     * minor level and not.
     */
    fun not(apiLevel: Int, minorLevel: Int, sdkId: Int = ANDROID_SDK_ID): SdkApiConstraint {
      return exactly(apiLevel, minorLevel, sdkId).not()
    }

    /**
     * Serializes the given constraint into a String, which can later be retrieved by calling
     * [deserialize].
     */
    fun serialize(constraint: ApiConstraint): String {
      return constraint.serialize()
    }

    /**
     * Deserializes a given string (previously computed by [serialize]) into the corresponding
     * constraint.
     */
    fun deserialize(s: String): ApiConstraint {
      return if (s.startsWith('{')) {
        MultiSdkApiConstraint.deserialize(s)
      } else {
        SdkApiConstraint.deserialize(s)
      }
    }

    /**
     * Given a `<uses-sdk>` element from an Android manifest, returns the corresponding
     * [ApiConstraint]. This normally just corresponds to the `minSdkVersion`, but if there are
     * `<extension-sdk>` element children, those are included in the vector as well.
     */
    fun getFromUsesSdk(usesSdk: Element): ApiConstraint? {
      assert(usesSdk.tagName == SdkConstants.TAG_USES_SDK)
      val minSdkVersionString =
        usesSdk.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
      val minSdkVersion = minSdkVersionString.getApiLevel()
      val codeName = if (minSdkVersionString.isNumber()) null else minSdkVersionString
      var curr = usesSdk.firstChild ?: return if (minSdkVersion != -1) get(minSdkVersion) else null

      val list = mutableListOf<SdkApiConstraint>()
      var addMinSdkVersion = minSdkVersion != -1
      while (true) {
        if (curr.nodeType == Node.ELEMENT_NODE && curr.nodeName == "extension-sdk") {
          val extension = curr as Element
          // Example: android:sdkVersion="30" android:minExtensionVersion="12"
          val sdkVersionString = extension.getAttributeNS(SdkConstants.ANDROID_URI, "sdkVersion")
          val sdkVersion = sdkVersionString.getApiLevel()
          val extensionVersion =
            extension.getAttributeNS(SdkConstants.ANDROID_URI, "minExtensionVersion").getApiLevel()
          if (sdkVersion == minSdkVersion && (codeName == null || codeName == sdkVersionString)) {
            addMinSdkVersion = false
          }
          list.add(
            SdkApiConstraint.createConstraint(fromInclusive = extensionVersion, sdkId = sdkVersion)
          )
        }
        curr = curr.nextSibling ?: break
      }
      if (addMinSdkVersion) {
        list.add(
          0,
          SdkApiConstraint.createConstraint(fromInclusive = minSdkVersion, sdkId = ANDROID_SDK_ID),
        )
      }
      return MultiSdkApiConstraint(list, anyOf = false)
    }
  }

  /**
   * Expresses an API constraint, such as "API level must be at least 21", for a specific SDK (such
   * as the Android SDK, or a mainline module backport for API level 30, and so on).
   */
  class SdkApiConstraint(
    /**
     * The bits here represent API levels; bit 0 is API level 1, bit 1 is API level 2 etc., all the
     * way up. The very last bit represents infinity.
     */
    val intervals: Intervals,
    val sdkId: Int = ANDROID_SDK_ID,
    /**
     * Whether this constraint can be negated (using [not]). For example, the [ApiConstraint]
     * derived from `SDK_INT >= 24` is negatable; in the `else` clause of `if (SDK_INT >= 24)` we
     * can negate the constraint and conclude that `SDK_INT < 24`.
     *
     * However, for the constraint derived from something like `SDK_INT >= 24 && day == TUESDAY`,
     * the constraint itself should not be negated. This is basically expressing something about the
     * *context* of the constraint, which we bundle with the constraint itself to make it easier to
     * pass constraints around. For example, the various [VersionChecks] utility methods which
     * recursively compute [ApiConstraint] objects for elements can set this bit when combining
     * contains in `&&` expressions.
     */
    private val isNegatable: Boolean = true,
  ) : ApiConstraint() {
    override fun fromInclusive(): Int {
      return intervals.fromInclusive()
    }

    override fun fromInclusiveMinor(): Int {
      return intervals.fromInclusiveMinor()
    }

    override fun toExclusive(): Int {
      return intervals.toExclusive()
    }

    override fun toExclusiveMinor(): Int {
      return intervals.toExclusiveMinor()
    }

    /**
     * Returns true if this is an "open ended" constraint, e.g. it goes up to infinity. True for
     * something like "SDK_INT >= 31", and false for "SDK_INT < 24".
     */
    fun isOpenEnded(): Boolean {
      return this.toExclusive() == Integer.MAX_VALUE
    }

    override fun isAtLeast(constraint: ApiConstraint): Boolean {
      assert(this !== UNKNOWN && constraint !== UNKNOWN)
      if (intervals.isEmpty()) {
        return true
      }
      when (constraint) {
        is SdkApiConstraint -> {
          return sdkId == constraint.sdkId && intervals.isAtLeast(constraint.intervals)
        }
        is MultiSdkApiConstraint -> {
          var anyMatch = false
          for ((_, alwaysNeed, mightNeed) in constraint.sdkConstraints) {
            if (alwaysNeed != null) {
              if (!isAtLeast(alwaysNeed)) {
                return false
              }
              anyMatch = true
            } else if (mightNeed != null) {
              if (anyMatch) continue
              if (isAtLeast(mightNeed)) {
                anyMatch = true
              }
            } else {
              throw IllegalStateException() // alwaysNeed or mightNeed should be non-null
            }
          }

          return anyMatch
        }
      }
    }

    override fun firstMissing(requirement: ApiConstraint): SdkApiConstraint? {
      when (requirement) {
        is SdkApiConstraint -> {
          return requirement
        }
        is MultiSdkApiConstraint -> {
          var anyMatch = false
          var firstMissing: SdkApiConstraint? = null
          for ((_, alwaysNeed, mightNeed) in requirement.sdkConstraints) {
            if (alwaysNeed != null) {
              if (!isAtLeast(alwaysNeed)) {
                return alwaysNeed
              }
              anyMatch = true
            } else if (mightNeed != null) {
              if (anyMatch) continue
              if (isAtLeast(mightNeed)) {
                anyMatch = true
              } else if (firstMissing == null) {
                firstMissing = mightNeed
              }
            } else {
              throw IllegalStateException() // alwaysNeed or mightNeed should be non-null
            }
          }

          return if (anyMatch) null else firstMissing ?: requirement.sdkConstraints.first().lowest()
        }
      }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun isAtLeast(apiLevel: Int): Boolean {
      assert(this !== UNKNOWN)
      // True if the target [apiLevel] does not have any API levels
      // lower than this.
      return sdkId == ANDROID_SDK_ID && intervals.isAtLeast(Intervals.atLeast(apiLevel))
    }

    override fun includes(apiLevel: Int): Boolean {
      return intervals.contains(apiLevel)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun alwaysAtLeast(apiLevel: Int): Boolean {
      return intervals.alwaysAtLeast(apiLevel)
    }

    override fun alwaysAtLeast(minSdk: ApiConstraint): Boolean {
      when (minSdk) {
        is SdkApiConstraint -> {
          if (minSdk == NONE || this == NONE) {
            return true
          }
          assert(sdkId == minSdk.sdkId)
          return intervals.alwaysAtLeast(minSdk.intervals)
        }
        is MultiSdkApiConstraint -> {
          // Compare by SDK ints
          val sdk = minSdk.findSdk(sdkId) ?: return false
          return alwaysAtLeast(sdk)
        }
      }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun everHigher(apiLevel: Int): Boolean {
      return intervals.everHigher(apiLevel)
    }

    override operator fun not(): SdkApiConstraint {
      if (!isNegatable) {
        return SdkApiConstraint(Intervals.NONE, sdkId)
      } else if (this == NONE) {
        return ALL
      } else if (this == ALL) {
        return NONE
      } else {
        return SdkApiConstraint(intervals.not(), sdkId)
      }
    }

    override fun negatable(): Boolean = isNegatable

    override fun asNonNegatable(): SdkApiConstraint {
      if (!negatable()) {
        return this
      }
      return SdkApiConstraint(intervals, sdkId, isNegatable = false)
    }

    override fun isEmpty(): Boolean {
      return intervals.isEmpty()
    }

    fun isNotEmpty(): Boolean {
      return intervals.isNotEmpty()
    }

    override infix fun or(other: ApiConstraint?): ApiConstraint {
      assert(this !== UNKNOWN && other !== UNKNOWN)
      when (other) {
        null -> return this
        is SdkApiConstraint -> {
          if (other.isEmpty()) return this
          if (sdkId != other.sdkId) {
            if (isEmpty()) return other
            return MultiSdkApiConstraint(
              listOf(
                SdkApiConstraints(sdkId, null, this),
                SdkApiConstraints(other.sdkId, null, other),
              )
            )
          }
          return SdkApiConstraint(intervals or other.intervals, sdkId)
        }
        is MultiSdkApiConstraint -> {
          if (isEmpty()) return other
          if (other.isEmpty()) return this
          val list =
            other.sdkConstraints.map {
              if (it.sdkId == sdkId) {
                it.orWith(this, true)
              } else it
            }
          val mergedList =
            if (other.findSdk(sdkId) != null) {
              list
            } else {
              list + SdkApiConstraints(sdkId, null, this)
            }
          return MultiSdkApiConstraint(mergedList)
        }
      }
    }

    override infix fun and(other: ApiConstraint?): SdkApiConstraint {
      assert(this !== UNKNOWN && other !== UNKNOWN)
      if (this.isEmpty() || other != null && other.isEmpty()) return NONE
      when (other) {
        null -> return this
        is SdkApiConstraint -> {
          if (sdkId != other.sdkId) {
            return NONE
          }
          return SdkApiConstraint(intervals and other.intervals, sdkId)
        }
        is MultiSdkApiConstraint -> {
          val match = other.findSdk(sdkId) ?: return NONE
          return SdkApiConstraint(intervals and match.intervals, sdkId)
        }
      }
    }

    override fun serialize(): String {
      val s = intervals.serialize()
      return if (sdkId == ANDROID_SDK_ID) {
        s
      } else {
        "$s;$sdkId"
      }
    }

    override fun toString(): String {
      val desc = if (sdkId == ANDROID_SDK_ID) "API level" else "version"
      return intervals.toString(desc, false)
    }

    override fun toString(sdkRegistry: ExtensionSdkRegistry?): String {
      val sdk = sdkRegistry?.find(this.sdkId) ?: "SDK ${this.sdkId}"
      val constraintString = this.toString()
      return if (this.sdkId == ANDROID_SDK_ID) constraintString else "$sdk: $constraintString"
    }

    override fun findSdk(sdkId: Int, certain: Boolean): SdkApiConstraint? {
      return if (sdkId == this.sdkId) this else null
    }

    override fun getSdk(): Int = sdkId

    override fun getConstraints(): List<SdkApiConstraint> = listOf(this)

    companion object {
      val NO_LEVELS = SdkApiConstraint(Intervals.NONE, -1)

      fun createConstraint(
        fromInclusive: Int? = null,
        toExclusive: Int? = null,
        sdkId: Int = ANDROID_SDK_ID,
      ): SdkApiConstraint {
        return createConstraint(fromInclusive, null, toExclusive, null, sdkId)
      }

      fun createConstraint(
        fromInclusive: Int? = null,
        fromInclusiveMinor: Int? = null,
        toExclusive: Int? = null,
        toExclusiveMinor: Int? = null,
        sdkId: Int = ANDROID_SDK_ID,
      ): SdkApiConstraint {
        val intervals =
          if (fromInclusive != null) {
            if (toExclusive != null) {
              Intervals.range(
                fromInclusive,
                fromInclusiveMinor ?: 0,
                toExclusive,
                toExclusiveMinor ?: 0,
              )
            } else {
              if (fromInclusiveMinor != null) {
                Intervals.atLeast(fromInclusive, fromInclusiveMinor)
              } else {
                Intervals.atLeast(fromInclusive)
              }
            }
          } else {
            Intervals.below(toExclusive!!, toExclusiveMinor ?: 0)
          }
        return SdkApiConstraint(intervals, sdkId)
      }

      /**
       * Serializes the given constraint into a String, which can later be retrieved by calling
       * [deserialize].
       */
      fun serialize(constraint: SdkApiConstraint): String {
        return constraint.serialize()
      }

      /**
       * Deserializes a given string (previously computed by [serialize]) into the corresponding
       * constraint.
       */
      fun deserialize(s: String): SdkApiConstraint {
        val index = s.indexOf(';')
        val sdkId: Int
        val intervals =
          if (index == -1) {
            sdkId = ANDROID_SDK_ID
            Intervals.deserialize(s)
          } else {
            sdkId = s.substring(index + 1).toInt()
            Intervals.deserialize(s.substring(0, index))
          }
        return SdkApiConstraint(intervals, sdkId)
      }
    }
  }

  /**
   * The known constraints for a particular SDK. The [always] constraint is known to always be
   * present or required, and the [sometimes] constraint may or may not be present.
   *
   * Instead of just recording the constraint and a Boolean flag for whether the constraint is
   * optional, we have two constraints to handle the case where you're combining two API vectors.
   *
   * For example, if you have the constraint "SDK_INT >= 21 or R_EXT >= 5", and you max that with
   * the constraint "SDK_INT >= 19 and R_EXT >= 3", what's the result? Here we're trying to decide
   * if the max of SDK_INT is a certain 19 or an optional 21. We can't really combine these; if we
   * pick the higher number, we also lose certainty. And depending on whether we need certainty or
   * the higher number in a call check, it's useful to know both.
   */
  internal data class SdkApiConstraints(
    val sdkId: Int,
    val always: SdkApiConstraint?,
    val sometimes: SdkApiConstraint?,
  ) {
    fun lowest(): SdkApiConstraint = always ?: sometimes!!

    fun highest(): SdkApiConstraint = sometimes ?: always!!

    constructor(
      sdkId: Int,
      constraint: SdkApiConstraint,
      either: Boolean,
    ) : this(sdkId, if (either) null else constraint, if (either) constraint else null)

    infix fun and(other: SdkApiConstraints): SdkApiConstraints {
      return andWith(other.always, other.sometimes)
    }

    fun andWith(constraint: SdkApiConstraint, either: Boolean): SdkApiConstraints {
      return andWith(if (either) null else constraint, if (either) constraint else null)
    }

    private fun andWith(
      matchAlways: SdkApiConstraint?,
      matchSometimes: SdkApiConstraint?,
    ): SdkApiConstraints {
      val have = this
      val always =
        if (matchAlways != null) {
          if (have.always != null) {
            SdkApiConstraint(matchAlways.intervals and have.always.intervals, have.sdkId)
          } else {
            matchAlways
          }
        } else {
          have.always
        }
      var sometimes =
        if (matchSometimes != null) {
          if (have.sometimes != null) {
            SdkApiConstraint(matchSometimes.intervals and have.sometimes.intervals, have.sdkId)
          } else {
            matchSometimes
          }
        } else {
          have.sometimes
        }
      // If sometimes isn't higher than always, just switch to it since it's more certain
      if (
        always != null &&
          sometimes != null &&
          always.intervals and sometimes.intervals == always.intervals
      ) {
        sometimes = null
      }
      assert(always != null || sometimes != null)
      return SdkApiConstraints(have.sdkId, always, sometimes)
    }

    infix fun or(other: SdkApiConstraints): SdkApiConstraints {
      return orWith(other.always, other.sometimes)
    }

    fun orWith(constraint: SdkApiConstraint, either: Boolean): SdkApiConstraints {
      return orWith(if (either) null else constraint, if (either) constraint else null)
    }

    private fun orWith(
      matchAlways: SdkApiConstraint?,
      matchSometimes: SdkApiConstraint?,
    ): SdkApiConstraints {
      val have = this
      val always =
        if (matchAlways != null) {
          if (have.always != null) {
            SdkApiConstraint(matchAlways.intervals or have.always.intervals, have.sdkId)
          } else {
            matchAlways
          }
        } else {
          have.always
        }
      var sometimes =
        if (matchSometimes != null) {
          if (have.sometimes != null) {
            SdkApiConstraint(matchSometimes.intervals or have.sometimes.intervals, have.sdkId)
          } else {
            matchSometimes
          }
        } else {
          have.sometimes
        }
      // If sometimes isn't higher than always, just switch to it since it's more certain
      if (
        always != null &&
          sometimes != null &&
          always.intervals or sometimes.intervals == always.intervals
      ) {
        sometimes = null
      }
      assert(always != null || sometimes != null)
      return SdkApiConstraints(have.sdkId, always, sometimes)
    }
  }

  /**
   * A constraint vector recording multiple constraints for separate SDKs.
   *
   * Note that [MultiSdkApiConstraint] intentionally does not implement [Comparable]; implication
   * among predicates only form a partial order, as opposed to total order that the Comparable<*>
   * interface expresses.
   *
   * Put another way, it's because API level vectors are different from simple API levels and will
   * not behave the same symmetric way that an API level would, so we cannot create a consistent
   * ordering (for example an API level vector can contain standalone SDKs which cannot be compared
   * with each other, so there's no sense of one being "higher" than the other. We would want both x
   * < y and y < x to be false, and there isn't a return value from compareTo we could return that
   * would allow that.)
   *
   * In converting existing API logic using comparator, the following may be helpful:
   * ```
   *   x > y         =>  y < x      => !(y >= x)  => !y.isAtLeast(x)
   *   x < y         =>  !(x >= y)  => !x.isAtLeast(y)
   *   x <= y        =>  y >= x     => y.isAtLeast(x)
   *   x >= y        =>  x.isAtLeast(y) / x.supports(y)
   *   max(x, y) < z => x < z && y < z
   * ```
   *
   * though be careful with if/else handling here due to the incomparable issue mentioned above;
   * just because < is false doesn't mean >= in the else clause will be true.
   */
  class MultiSdkApiConstraint
  internal constructor(internal val sdkConstraints: List<SdkApiConstraints>) : ApiConstraint() {
    constructor(
      apis: List<SdkApiConstraint>,
      anyOf: Boolean,
    ) : this(
      apis.map {
        if (anyOf) SdkApiConstraints(it.sdkId, always = null, sometimes = it)
        else SdkApiConstraints(it.sdkId, always = it, sometimes = null)
      }
    )

    private val apis: Sequence<SdkApiConstraint> = sdkConstraints.asSequence().map { it.lowest() }

    override fun fromInclusive(): Int {
      val constraint = sdkConstraints.firstOrNull { it.sdkId == ANDROID_SDK_ID } ?: return -1
      return constraint.lowest().fromInclusive()
    }

    override fun fromInclusiveMinor(): Int {
      val constraint = sdkConstraints.firstOrNull { it.sdkId == ANDROID_SDK_ID } ?: return -1
      return constraint.lowest().fromInclusiveMinor()
    }

    override fun toExclusive(): Int {
      val constraint = sdkConstraints.firstOrNull { it.sdkId == ANDROID_SDK_ID } ?: return -1
      return constraint.highest().toExclusive()
    }

    override fun toExclusiveMinor(): Int {
      val constraint = sdkConstraints.firstOrNull { it.sdkId == ANDROID_SDK_ID } ?: return -1
      return constraint.highest().toExclusiveMinor()
    }

    override fun isAtLeast(constraint: ApiConstraint): Boolean {
      assert(constraint !== UNKNOWN)
      if (constraint.isEmpty()) {
        return true
      }
      when (constraint) {
        is SdkApiConstraint -> {
          val sdkId = constraint.sdkId
          val match = sdkConstraints.firstOrNull { it.sdkId == sdkId }
          return match?.always?.isAtLeast(constraint) ?: false
        }
        is MultiSdkApiConstraint -> {
          // Compare by SDK id's
          var anyMatch = false
          for ((sdkId, alwaysNeed, mightNeed) in constraint.sdkConstraints) {
            val match = sdkConstraints.firstOrNull { it.sdkId == sdkId }

            if (alwaysNeed != null) {
              val alwaysHave = match?.always ?: return false
              if (!alwaysHave.isAtLeast(alwaysNeed)) {
                return false
              }
              anyMatch = true
            } else if (mightNeed != null) {
              if (match == null || anyMatch) continue
              val (_, alwaysHave, sometimesHave) = match
              if (
                alwaysHave != null && alwaysHave.isAtLeast(mightNeed) ||
                  sometimesHave != null && sometimesHave.isAtLeast(mightNeed)
              ) {
                anyMatch = true
              }
            } else {
              throw IllegalStateException() // alwaysNeed or mightNeed should be non-null
            }
          }

          // If there's any optional SDK ID in this not in constraint, no match
          if (
            sdkConstraints.any { it.sometimes != null && constraint.findSdks(it.sdkId) == null }
          ) {
            return false
          }
          return anyMatch
        }
      }
    }

    override fun includes(apiLevel: Int): Boolean {
      for (api in apis) {
        if (api.includes(apiLevel)) {
          return true
        }
      }
      return false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun alwaysAtLeast(apiLevel: Int): Boolean {
      for (api in apis) {
        if (api.alwaysAtLeast(apiLevel)) {
          return true
        }
      }
      return false
    }

    override fun alwaysAtLeast(minSdk: ApiConstraint): Boolean {
      when (minSdk) {
        is SdkApiConstraint -> {
          val sdk = findSdk(minSdk.sdkId) ?: return false
          return sdk.alwaysAtLeast(minSdk)
        }
        is MultiSdkApiConstraint -> {
          // Compare by SDK id's
          for (api in apis) {
            val match = minSdk.findSdk(api.sdkId)
            if (match != null && api.alwaysAtLeast(match)) {
              return true
            }
          }

          return false
        }
      }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun everHigher(apiLevel: Int): Boolean {
      for (api in apis) {
        if (api.everHigher(apiLevel)) {
          return true
        }
      }
      return false
    }

    override fun firstMissing(requirement: ApiConstraint): SdkApiConstraint? {
      if (requirement.isEmpty()) {
        return null
      }
      when (requirement) {
        is SdkApiConstraint -> {
          val sdkId = requirement.sdkId
          val match = sdkConstraints.firstOrNull { it.sdkId == sdkId }
          return if (match?.always?.isAtLeast(requirement) == true) {
            null
          } else {
            requirement
          }
        }
        is MultiSdkApiConstraint -> {
          // Compare by SDK id's
          var anyMatch = false
          var firstMissing: SdkApiConstraint? = null
          for ((sdkId, alwaysNeed, mightNeed) in requirement.sdkConstraints) {
            val sdkMatch = sdkConstraints.firstOrNull { it.sdkId == sdkId }
            val match =
              if (sdkMatch != null && (alwaysNeed ?: mightNeed!!).isAtLeast(sdkMatch.highest()))
                null
              else sdkMatch

            if (alwaysNeed != null) {
              val alwaysHave = sdkMatch?.always ?: return alwaysNeed
              if (!alwaysHave.isAtLeast(alwaysNeed)) {
                return alwaysNeed
              }
              anyMatch = true
            } else if (mightNeed != null) {
              if (match == null && (firstMissing == null)) {
                firstMissing = mightNeed
              }
              if (anyMatch) continue
              if (match == null) {
                continue
              }
              val (_, alwaysHave, sometimesHave) = match
              if (
                alwaysHave != null && alwaysHave.isAtLeast(mightNeed) ||
                  sometimesHave != null && sometimesHave.isAtLeast(mightNeed)
              ) {
                anyMatch = true
              } else if (firstMissing == null) {
                firstMissing = mightNeed
              }
            } else {
              throw IllegalStateException() // alwaysNeed or mightNeed should be non-null
            }
          }

          // If there's any optional SDK ID in this not in constraint, no match
          if (
            sdkConstraints.any { it.sometimes != null && requirement.findSdks(it.sdkId) == null }
          ) {
            return firstMissing
          }
          return if (anyMatch) null else firstMissing
        }
      }
    }

    override fun not(): ApiConstraint {
      val reversed =
        sdkConstraints.map { SdkApiConstraints(it.sdkId, it.sometimes?.not(), it.always?.not()) }
      return MultiSdkApiConstraint(reversed)
    }

    override fun negatable(): Boolean {
      return sdkConstraints.all { it.always?.negatable() ?: it.sometimes?.negatable() ?: true }
    }

    override fun asNonNegatable(): ApiConstraint {
      if (!negatable()) {
        return this
      }
      val reversed =
        sdkConstraints.map {
          SdkApiConstraints(it.sdkId, it.always?.asNonNegatable(), it.sometimes?.asNonNegatable())
        }
      return MultiSdkApiConstraint(reversed)
    }

    override fun isEmpty(): Boolean {
      return sdkConstraints.all { it.lowest().isEmpty() }
    }

    override fun or(other: ApiConstraint?): ApiConstraint {
      assert(other !== UNKNOWN)
      other ?: return this
      when (other) {
        is SdkApiConstraint -> {
          // "or" is symmetric, and we've already implemented it with the SdkApiConstraint on the
          // left hand side
          return other or this
        }
        is MultiSdkApiConstraint -> {
          // For each of the SDKs, find the corresponding one in other, and
          // construct a list of the union of each.
          val apis =
            this.sdkConstraints
              .map {
                val match = other.findSdks(it.sdkId)
                if (match != null) {
                  match or it
                } else {
                  it
                }
              }
              .toMutableList()
          // Finally, pick up the SDKs that are in other but not in this, and add those too.
          other.sdkConstraints.forEach { if (this.findSdks(it.sdkId) == null) apis.add(it) }
          return MultiSdkApiConstraint(apis)
        }
      }
    }

    override fun and(other: ApiConstraint?): ApiConstraint {
      assert(other !== UNKNOWN)
      other ?: return this
      when (other) {
        is SdkApiConstraint -> {
          // "and" is symmetric, and we've already implemented it with the SdkApiConstraint on the
          // left hand side
          return other and this
        }
        is MultiSdkApiConstraint -> {
          val anded = mutableListOf<SdkApiConstraint>()
          for (api in apis) {
            val match = other.findSdk(api.sdkId)
            if (match != null) {
              anded.add(SdkApiConstraint(api.intervals and match.intervals, api.sdkId))
            }
          }

          return create(anded, anyOf = false)
        }
      }
    }

    override fun serialize(): String {
      return "{${sdkConstraints.joinToString(",") {
                "${it.always?.serialize() ?: ""}:${it.sometimes?.serialize() ?: ""}"
            }}}"
    }

    override fun toString(): String {
      return toString(null)
    }

    override fun toString(sdkRegistry: ExtensionSdkRegistry?): String {
      // return apis.joinToString(if (anyOf) " or " else " and ") { it.toString(sdkRegistry) }
      val andItems = sdkConstraints.mapNotNull { it.always }
      val orItems = sdkConstraints.mapNotNull { it.sometimes }
      val andString = andItems.joinToString(" and ") { it.toString(sdkRegistry) }
      val orString = orItems.joinToString(" or ") { it.toString(sdkRegistry) }
      return if (andItems.isNotEmpty()) {
        if (orItems.isNotEmpty()) {
          if (orItems.size == 1) "$andString and optionally $orString"
          else "$andString and any of ($orString)"
        } else {
          andString
        }
      } else {
        orString
      }
    }

    override fun findSdk(sdkId: Int, certain: Boolean): SdkApiConstraint? {
      val sdkConstraints = sdkConstraints.firstOrNull { it.sdkId == sdkId } ?: return null
      return if (certain) sdkConstraints.always else sdkConstraints.lowest()
    }

    internal fun findSdks(sdkId: Int): SdkApiConstraints? {
      return sdkConstraints.firstOrNull { it.sdkId == sdkId }
    }

    override fun getSdk(): Int = -1

    override fun getConstraints(): List<SdkApiConstraint> {
      return sdkConstraints.map { it.lowest() }
    }

    companion object {
      fun create(apis: List<SdkApiConstraint>, anyOf: Boolean): ApiConstraint {
        return if (apis.size == 1) {
          apis[0]
        } else if (apis.isEmpty()) {
          return NONE
        } else {
          MultiSdkApiConstraint(apis, anyOf)
        }
      }

      fun create(description: String, anyOf: Boolean = true): MultiSdkApiConstraint {
        // Example: "0:34,1000000:4,33:4" creates an ExtensionApiConstraint with 3 nested ranges:
        //   SDK 0, atLeast(34)
        //   SDK 100000, atLeast(4)
        //   SDK 33, atLeast(4)
        val constraints =
          description.split(",").map { s: String ->
            val index = s.indexOf(':')
            if (index == -1) {
              error("Invalid extension constraint descriptor $s (in $description)")
            }
            val apiLevel = s.substring(index + 1).toInt()
            val sdk = s.substring(0, index).toInt()
            if (sdk == ANDROID_SDK_ID) get(apiLevel)
            else SdkApiConstraint.createConstraint(fromInclusive = apiLevel, sdkId = sdk)
          }
        return MultiSdkApiConstraint(constraints, anyOf = anyOf)
      }

      /**
       * Produces a string-description of this [MultiSdkApiConstraint] matching the format found in
       * api-versions.xml files (and parsed via [create])
       */
      fun describe(constraint: ApiConstraint): String {
        return when (constraint) {
          is SdkApiConstraint -> "0:${constraint.minString()}"
          is MultiSdkApiConstraint ->
            constraint.sdkConstraints.joinToString(",") { "${it.sdkId}:${it.lowest().minString()}" }
        }
      }

      fun deserialize(s: String): ApiConstraint {
        val apis =
          s.removeSurrounding("{", "}")
            .split(",")
            .map { t ->
              val index = t.indexOf(':')
              val always =
                if (index > 0) SdkApiConstraint.deserialize(t.substring(0, index)) else null
              val sometimes =
                if (index < t.length - 1) SdkApiConstraint.deserialize(t.substring(index + 1))
                else null
              SdkApiConstraints(always?.sdkId ?: sometimes!!.sdkId, always, sometimes)
            }
            .toList()
        return MultiSdkApiConstraint(apis)
      }
    }
  }
}

private fun String.isNumber(): Boolean = this.isNotEmpty() && Character.isDigit(this[0])

private fun String.getApiLevel(): Int {
  return if (isBlank()) -1
  else if (isNumber()) this.toInt() else SdkVersionInfo.getApiByPreviewName(this, true)
}
