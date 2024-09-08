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

import com.android.tools.lint.detector.api.ApiConstraint.Companion.deserialize
import com.android.tools.lint.detector.api.ApiConstraint.SdkApiConstraint.Companion.deserialize

/**
 * Represents a series of half-open intervals, such as `[10, 12), [14, 25), [30,∞)`, and operations
 * for taking intersections of these, checking for membership, and so on. Note that the lowest
 * possible starting point is 1 rather than negative infinity. This is because of its primary
 * intended use case: representing API constraints.
 */
sealed class Intervals {
  override fun toString(): String {
    return toString(compact = false)
  }

  /**
   * The starting point of the interval, inclusive. Note that this is only the major part of the
   * number; the minor part can be looked up via [fromInclusiveMinor]. So an interval starting at
   * "42.5" would return "42" from this method, and "5" from the other.
   */
  abstract fun fromInclusive(): Int

  /**
   * The *minor part* of the starting point of the interval, inclusive. Note that this is only the
   * minor part of the number; the major part can be looked up via [fromInclusive]. So an interval
   * starting at "42.5" would return "5" from this method, and "42" from the other.
   */
  abstract fun fromInclusiveMinor(): Int

  /**
   * The ending point of the interval, exclusive. Note that this is only the major part of the
   * number; the minor part can be looked up via [toExclusiveMinor]. So an interval ending at "42.5"
   * would return "42" from this method, and "5" from the other.
   */
  abstract fun toExclusive(): Int

  /**
   * The *minor part* of the ending point of the interval, exclusive. Note that this is only the
   * minor part of the number; the major part can be looked up via [toExclusive]. So an interval
   * ending at "42.5" would return "5" from this method, and "42" from the other.
   */
  abstract fun toExclusiveMinor(): Int

  /**
   * Represents this interval as a string. For example, the interval starting at 3 and ending after
   * 6 would be shown as "x ≥ 10 and x < 12" , or "10 ≤ x < 12" in [compact] mode. The [variable]
   * can be customized, for example, [ApiConstraint] uses "API level" instead.
   */
  abstract fun toString(variable: String = "x", compact: Boolean = false): String

  /** Intersects to intervals */
  abstract infix fun and(other: Intervals): Intervals

  /** Returns the union of two intervals */
  abstract infix fun or(other: Intervals): Intervals

  /** Returns the inverse of the current interval */
  abstract operator fun not(): Intervals

  /** Returns true if this interval is equal to another. */
  abstract override fun equals(other: Any?): Boolean

  /** Returns the hashcode of this interval. */
  abstract override fun hashCode(): Int

  /** Returns true if this interval is empty. */
  abstract fun isEmpty(): Boolean

  /** Returns true if this interval is **not** empty. */
  fun isNotEmpty() = !isEmpty()

  /**
   * Returns true if this interval includes everything (e.g. from lowest possible starting point up
   * to infinity.)
   */
  abstract fun isAll(): Boolean

  /** Returns true if the given value is included within this interval */
  abstract fun contains(value: Int): Boolean

  /**
   * Serializes this constraint into a String, which can later be retrieved by calling
   * [deserialize].
   */
  abstract fun serialize(): String

  /** Is this [Intervals] at least as high as the given [other] intervals ? */
  abstract fun isAtLeast(other: Intervals): Boolean

  /**
   * Will this API level or anything higher always match this constraint?
   *
   * For example, if we know from minSdkVersion that SDK_INT >= 32, and we see a check if SDK_INT
   * is >= 21, that check will always be true. That's what this method is for; this [ApiConstraint]
   * is the SDK_INT check, and the passed in [minSdk] represents the minimum value of SDK_INT (the
   * known constraint from minSdkVersion).
   */
  fun alwaysAtLeast(minSdk: Intervals): Boolean {
    return minSdk and this == minSdk
  }

  fun alwaysAtLeast(apiLevel: Int): Boolean {
    val minSdk = atLeast(apiLevel)
    return minSdk and this == minSdk
  }

  /** Returns true if this interval contains any numbers higher than the given level. */
  fun everHigher(apiLevel: Int): Boolean {
    val minSdk = atLeast(apiLevel + 1)
    return (minSdk and this).isNotEmpty()
  }

  /**
   * Returns true if this is an "open ended" constraint, e.g. it goes up to infinity. True for
   * something like "SDK_INT >= 31", and false for "SDK_INT < 24".
   */
  fun isOpenEnded(): Boolean {
    return isInfinity(toExclusive())
  }

  /**
   * Encoding of `major.minor` integer numbers into a single primitive integer. This simplifies
   * various range checking etc.
   */
  @JvmInline
  private value class MajorMinor(val bits: Int) : Comparable<MajorMinor> {
    constructor(major: Int, minor: Int) : this((major shl MAJOR_SHIFT) + minor)

    val major: Int
      get() = if (this == INFINITY) Integer.MAX_VALUE else bits shr MAJOR_SHIFT

    val minor: Int
      get() = bits and MINOR_MASK

    override fun compareTo(other: MajorMinor): Int {
      return bits.compareTo(other.bits)
    }

    override fun toString(): String {
      return if (minor > 0) {
        "$major.$minor"
      } else {
        major.toString()
      }
    }
  }

  /**
   * Represents a single half-open interval from `[fromInclusive.fromInclusiveMinor,
   * toExclusive.toExclusiveMinor)`
   */
  private class Span(
    /**
     * The start of the span, encoded as the major version, left shifted [MAJOR_SHIFT] bits and then
     * the minor version added in. From is inclusive, and to is exclusive.
     */
    val from: MajorMinor,
    val to: MajorMinor,
  ) : Intervals() {
    constructor(
      fromInclusive: Int,
      fromInclusiveMinor: Int,
      toExclusive: Int,
      toExclusiveMinor: Int,
    ) : this(
      MajorMinor(fromInclusive, fromInclusiveMinor),
      MajorMinor(toExclusive, toExclusiveMinor),
    )

    override fun fromInclusive(): Int =
      if (isEmpty()) {
        -1
      } else from.major

    override fun fromInclusiveMinor(): Int = from.minor

    override fun toExclusive(): Int = to.major

    override fun toExclusiveMinor(): Int = to.minor

    override fun isEmpty(): Boolean {
      return from == to
    }

    private fun Span.intersects(other: Span): Boolean {
      return (this.from <= other.to) && (other.from <= this.to)
    }

    override fun contains(value: Int): Boolean {
      return MajorMinor(value, 0) in from..<to
    }

    override fun isAtLeast(other: Intervals): Boolean {
      return when (other) {
        is Span -> from >= other.from
        is Spans -> from >= other.spans.first().from
      }
    }

    override fun serialize(): String {
      if (isEmpty()) {
        return "0"
      }
      val sb = StringBuilder()
      val fromInclusive = fromInclusive()
      val fromInclusiveMinor = fromInclusiveMinor()

      sb.append(fromInclusive.toString())
      if (fromInclusiveMinor > 0) {
        sb.append('.').append(fromInclusiveMinor.toString())
      }
      sb.append('-')
      if (to == INFINITY) {
        sb.append('∞')
      } else {
        val toExclusive = toExclusive()
        val toExclusiveMinor = toExclusiveMinor()
        sb.append(toExclusive.toString())
        if (toExclusiveMinor > 0) {
          sb.append('.').append(toExclusiveMinor.toString())
        }
      }
      return sb.toString()
    }

    override fun not(): Intervals {
      return when {
        this == ALL -> NONE
        this == NONE -> ALL
        from == ZERO -> Span(to, INFINITY)
        to == INFINITY -> Span(ZERO, from)
        else -> Spans(listOf(Span(ZERO, from), Span(to, INFINITY)))
      }
    }

    override fun and(other: Intervals): Intervals {
      when (other) {
        is Span -> {
          val start = kotlin.math.max(this.from.bits, other.from.bits)
          val end = kotlin.math.min(this.to.bits, other.to.bits)
          return when {
            start >= end -> NONE
            start == this.from.bits && end == this.to.bits -> this
            start == other.from.bits && end == other.to.bits -> other
            else -> Span(MajorMinor(start), MajorMinor(end))
          }
        }
        is Spans -> return other.and(this)
      }
    }

    override fun or(other: Intervals): Intervals {
      when (other) {
        is Span -> {
          if (this.intersects(other)) {
            // If they intersect, merge into a new range
            val start = kotlin.math.min(this.from.bits, other.from.bits)
            val end = kotlin.math.max(this.to.bits, other.to.bits)
            return when {
              start >= end -> NONE
              start == this.from.bits && end == this.to.bits -> this
              start == other.from.bits && end == other.to.bits -> other
              else -> Span(MajorMinor(start), MajorMinor(end))
            }
          } else {
            val first: Intervals
            val second: Intervals
            if (this.from < other.from) {
              first = this
              second = other
            } else {
              first = other
              second = this
            }
            return Spans(listOf(first, second))
          }
        }
        is Spans -> return other.or(this)
      }
    }

    override fun toString(variable: String, compact: Boolean): String {
      val fromString = from.toString()
      val toString = to.toString()
      return when {
        adjacent(from, to) -> "$variable = $fromString"
        to <= from -> "No ${variable}s"
        from == ZERO ->
          if (to == INFINITY) {
            "All ${variable}s"
          } else {
            "$variable < $toString"
          }
        to == INFINITY -> "$variable ≥ $fromString"
        else ->
          if (compact) {
            "$fromString ≤ $variable < $toString"
          } else {
            "$variable ≥ $fromString and $variable < $toString"
          }
      }
    }

    override fun isAll(): Boolean {
      return from == ZERO && to == INFINITY
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Span

      // Treat all empty spans as equal
      if (from >= to) return other.from >= other.to

      return this.from == other.from && this.to == other.to
    }

    override fun hashCode(): Int {
      return 31 * fromInclusive() + toExclusive()
    }
  }

  private class Spans(val spans: List<Span>) : Intervals() {
    override fun fromInclusive(): Int {
      if (spans.isEmpty()) {
        return 1
      }
      return spans.first().fromInclusive()
    }

    override fun fromInclusiveMinor(): Int {
      if (spans.isEmpty()) {
        return 0
      }
      return spans.first().fromInclusiveMinor()
    }

    override fun toExclusive(): Int {
      if (spans.isEmpty()) {
        return Int.MAX_VALUE
      }
      return spans.last().toExclusive()
    }

    override fun toExclusiveMinor(): Int {
      if (spans.isEmpty()) {
        return 0
      }
      return spans.last().toExclusiveMinor()
    }

    override fun isEmpty(): Boolean {
      // We should never end up in this state; when and-ing and ending up with an empty region
      // we should have returned a concrete SimpleSpan
      return false
    }

    override fun isAll(): Boolean {
      // We should never end up in this state; when and-ing and ending up with a full (1 to
      // MAX_VALUE) region we should have returned a concrete SimpleSpan
      return false
    }

    override fun contains(value: Int): Boolean {
      return spans.any { it.contains(value) }
    }

    override fun isAtLeast(other: Intervals): Boolean {
      return when (other) {
        is Span -> spans.first().from >= other.from
        is Spans -> spans.first().from >= other.spans.first().from
      }
    }

    override fun toString(variable: String, compact: Boolean): String {
      // Special case: if these intervals cover `{(1, X), [X+1,∞)}`, meaning everything except X,
      // special case that to just show "x != X" instead of these intervals.
      if (spans.size == 2) {
        val left = spans[0]
        val right = spans[1]
        if (isNotXInterval(left, right)) {
          return "$variable ≠ ${left.to}"
        }
      }
      return spans.joinToString(" or ") { it.toString(variable, compact) }
    }

    override infix fun or(other: Intervals): Intervals {
      return when (other) {
        is Span -> Spans(joinIntervals(this.spans, listOf(other)))
        is Spans -> Spans(joinIntervals(this.spans, other.spans))
      }
    }

    override infix fun and(other: Intervals): Intervals {
      val merged =
        when (other) {
          is Span -> intersectIntervals(this.spans, listOf(other))
          is Spans -> intersectIntervals(this.spans, other.spans)
        }
      return create(merged)
    }

    override fun not(): Intervals {
      val spans = mutableListOf<Span>()
      var prevTo = ZERO
      for (span in this.spans) {
        if (span.from > prevTo) {
          spans.add(Span(prevTo, span.from))
        }
        prevTo = span.to
      }
      if (prevTo != INFINITY) {
        spans.add(Span(prevTo, INFINITY))
      }
      return create(spans)
    }

    override fun serialize(): String {
      return spans.joinToString(",") { it.serialize() }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Spans

      return spans == other.spans
    }

    override fun hashCode(): Int {
      return fromInclusive()
    }
  }

  companion object {
    // number of bits to reserve for the minor part
    private const val MAJOR_SHIFT = 6

    // Not using Integer.MAX_VALUE since we're shifing this thing around
    private val INFINITY = MajorMinor(Integer.MAX_VALUE)

    /** Zero (smallest value) */
    private val ZERO = MajorMinor(1, 0)

    /** The increment from one major API level to the next in the combined bitmasks. */
    private const val API_LEVEL_DELTA = 1 shl MAJOR_SHIFT

    /** The mask covering the bits for the minor version. */
    private const val MINOR_MASK = (1 shl MAJOR_SHIFT) - 1

    /** This value, all the way up to the next value. */
    fun exactly(major: Int): Intervals = Span(major, 0, major + 1, 0)

    /*
     * When you specify a minor version, it's picking just this minor version, not other minor versions
     * for the same API level.
     */
    fun exactly(major: Int, minor: Int): Intervals = Span(major, minor, major, minor + 1)

    fun atLeast(major: Int, minor: Int = 0): Intervals = Span(MajorMinor(major, minor), INFINITY)

    fun atMost(toExclusive: Int): Intervals = Span(1, 0, toExclusive + 1, 0)

    fun below(toExclusive: Int, minor: Int = 0): Intervals = Span(1, 0, toExclusive, minor)

    fun range(fromInclusive: Int, toExclusive: Int): Intervals =
      Span(fromInclusive, 0, toExclusive, 0)

    fun range(
      fromInclusive: Int,
      fromInclusiveMinor: Int,
      toExclusive: Int,
      toExclusiveMinor: Int,
    ): Intervals = Span(fromInclusive, fromInclusiveMinor, toExclusive, toExclusiveMinor)

    fun deserialize(s: String): Intervals {
      if (s.contains(',')) {
        val spans = s.split(',').map { deserialize(it) as Span }
        return create(spans)
      }
      if (s == "0") {
        return NONE
      } else if (s.endsWith("-∞")) {
        val fromDot = s.indexOf('.')
        val from: Int
        val fromMinor: Int
        if (fromDot != -1) {
          from = s.substring(0, fromDot).toInt()
          fromMinor = s.substring(fromDot + 1, s.length - 2).toInt()
        } else {
          from = s.substring(0, s.length - 2).toInt()
          fromMinor = 0
        }
        return Span(MajorMinor(from, fromMinor), INFINITY)
      }
      val separator = s.indexOf('-')
      val fromInclusive: Int
      val fromInclusiveMinor: Int
      val toExclusive: Int
      val toExclusiveMinor: Int
      val fromDot = s.indexOf('.')
      val toDot = s.indexOf('.', separator + 1)

      if (fromDot != -1 && fromDot < separator) {
        fromInclusive = s.substring(0, fromDot).toInt()
        fromInclusiveMinor = s.substring(fromDot + 1, separator).toInt()
      } else {
        fromInclusive = s.substring(0, separator).toInt()
        fromInclusiveMinor = 0
      }
      if (toDot != -1) {
        toExclusive = s.substring(separator + 1, toDot).toInt()
        toExclusiveMinor = s.substring(toDot + 1).toInt()
      } else {
        toExclusive = s.substring(separator + 1).toInt()
        toExclusiveMinor = 0
      }
      return Span(fromInclusive, fromInclusiveMinor, toExclusive, toExclusiveMinor)
    }

    val NONE: Intervals = Span(ZERO, ZERO)
    val ALL: Intervals = Span(ZERO, INFINITY)

    private fun create(spans: List<Span>): Intervals {
      return when {
        spans.isEmpty() -> NONE
        spans.size == 1 -> spans.first()
        else -> Spans(spans)
      }
    }

    /** Merge two lists of spans, using union semantics. */
    private fun joinIntervals(intervals1: List<Span>, intervals2: List<Span>): List<Span> {
      val mergedIntervals = mutableListOf<Span>()
      var i = 0
      var j = 0

      while (i < intervals1.size || j < intervals2.size) {
        if (i == intervals1.size) {
          mergedIntervals.add(intervals2[j++])
          continue
        }
        if (j == intervals2.size) {
          mergedIntervals.add(intervals1[i++])
          continue
        }

        val current1 = intervals1[i]
        val current2 = intervals2[j]

        if (current1.to <= current2.from) {
          mergedIntervals.add(current1)
          i++
        } else if (current2.to <= current1.from) {
          mergedIntervals.add(current2)
          j++
        } else {
          val newFrom = minOf(current1.from, current2.from)
          val newTo = maxOf(current1.to, current2.to)
          mergedIntervals.add(Span(newFrom, newTo))

          if (current1.to < current2.to) {
            i++
          } else {
            j++
          }
        }
      }

      return mergedIntervals
    }

    /** Merge two lists of spans, using intersection semantics. */
    private fun intersectIntervals(intervals1: List<Span>, intervals2: List<Span>): List<Span> {
      val intersectedIntervals = mutableListOf<Span>()
      var i = 0
      var j = 0

      while (i < intervals1.size && j < intervals2.size) {
        val current1 = intervals1[i]
        val current2 = intervals2[j]

        if (current1.to > current2.from && current2.to > current1.from) {
          val newFrom = maxOf(current1.from, current2.from)
          val newTo = minOf(current1.to, current2.to)
          intersectedIntervals.add(Span(newFrom, newTo))
        }

        if (current1.to < current2.to) {
          i++
        } else {
          j++
        }
      }

      return intersectedIntervals
    }

    /**
     * Returns true if the given level (typically returned from [fromInclusive] or [toExclusive]
     * represents infinity, e.g. it's an open-ended interval such as `x >= 10`, represented as the
     * interval `[x, ∞)`.
     */
    fun isInfinity(level: Int): Boolean {
      return level == INFINITY.bits
    }

    /**
     * Returns true if these two spans represent the intervals that include everything except value
     * "X". For example, for `exactly(21).not()`, we have the intervals 1 up to 21, and 22 up to
     * infinity.
     *
     * In short, this identifies the scenario where we have this exact span list: `{(1,
     * X), [X+1,∞)}`, meaning everything except X. We use this to identify this scenario to for
     * example display this as just "x != X" instead of these more complicated intervals.
     */
    private fun isNotXInterval(first: Span, adjacent: Span): Boolean {
      return first.from == ZERO && adjacent.to == INFINITY && adjacent(first.to, adjacent.from)
    }

    /**
     * Returns true if these version numbers are right next to each other. Normally, this is as
     * simple as "is the value one less than the neighbor?". But with minor versions, there is some
     * ambiguity. Consider the expression "x = 12". If we take inverse of this, we have the spans 0
     * up to and not including 12. But for the other side, do we take 12.1 and up, or do we take 13
     * and up? For now, we generally operate on whole numbers, unless the version is specifically
     * constructed with a minor version.
     *
     * Sometimes we want to reverse this, for example, when we have the intervals corresponding to
     * every value except 12, we want to display this as just "x != 12". This method handles
     * checking for this adjacency.
     */
    private fun adjacent(left: MajorMinor, right: MajorMinor): Boolean {
      // It's unclear whether  !(x < N && x > N) should be limited to just the minor version .0 or
      // the entire range of N to N+1. For now simplify both to "x != N".
      return (left.bits + 1 == right.bits ||
        left.bits + API_LEVEL_DELTA == right.bits && left.minor == 0 && right.minor == 0)
    }
  }
}
