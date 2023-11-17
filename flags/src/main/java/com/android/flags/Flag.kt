/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.flags

/**
 * A flag is a setting with an unique ID and some value. Flags are often used to gate features (e.g.
 * start with the feature disabled or enabled) or initialize a feature with some default value (e.g.
 * how much memory to initialize a system with, what mode a system should use by default).
 */
class Flag<T> private constructor(
  /** Returns the [FlagGroup] that this flag is part of.  */
  val group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: T,
  valueConverter: ValueConverter<T>
) {

  /**
   * Returns a unique ID for this flag. It will be composed of the group's name prefixed to this
   * flag's name.
   */
  val id: String
  /** Returns a user-friendly display name for this flag.  */
  val displayName: String
  /** Returns a user-friendly description for what feature this flag gates.  */
  val description: String
  private val valueConverter: ValueConverter<T>
  private val defaultValue: String
  private val originalDefaultValue: T

  /** Use one of the `Flag#create` convenience methods to construct this class.  */
  init {
    id = group.name + "." + name
    this.displayName = displayName
    this.description = description
    this.valueConverter = valueConverter
    this.defaultValue = valueConverter.serialize(defaultValue)
    originalDefaultValue = defaultValue
    group.flags.register(this)
  }

  /** Verifies that this flag has valid information  */
  fun validate() {
    group.validate()
    verifyDefaultValue(originalDefaultValue, defaultValue, valueConverter)
    verifyFlagIdFormat(id)
    verifyDisplayTextFormat(displayName)
    verifyDisplayTextFormat(description)
  }

  /** Returns the value of this flag.  */
  fun get(): T {
    val flags = group.flags
    var strValue = flags.getOverriddenValue(this)
    if (strValue == null) {
      strValue = defaultValue
    }
    return try {
      valueConverter.deserialize(strValue)
    } catch (e: Exception) {
      valueConverter.deserialize(defaultValue)
    }
  }

  /**
   * Override the value of this flag at runtime.
   *
   *
   * This method does not modify this flag definition directly, but instead adds an entry into
   * its parent [Flags.getOverrides] collection.
   */
  fun override(overrideValue: T) {
    group.flags.overrides.put(this, valueConverter.serialize(overrideValue))
  }

  /** Clear any override previously set by [.override].  */
  fun clearOverride() {
    group.flags.overrides.remove(this)
  }

  val isOverridden: Boolean
    get() = group.flags.overrides[this] != null

  /**
   * Simple interface for converting a value to and from a String. This is useful as all flags are
   * really strings underneath, although it's convenient to expose, say, boolean flags to users
   * instead.
   */
  private interface ValueConverter<T> {
    fun serialize(value: T): String
    fun deserialize(strValue: String): T
  }

  companion object {
    private val BOOL_CONVERTER: ValueConverter<Boolean> = object : ValueConverter<Boolean> {
      override fun serialize(value: Boolean): String {
        return value.toString()
      }

      override fun deserialize(strValue: String): Boolean {
        return strValue.toBoolean()
      }
    }
    private val INT_CONVERTER: ValueConverter<Int> = object : ValueConverter<Int> {
      override fun serialize(value: Int): String {
        return value.toString()
      }

      override fun deserialize(strValue: String): Int {
        return strValue.toInt()
      }
    }
    private val LONG_CONVERTER: ValueConverter<Long> = object : ValueConverter<Long> {
      override fun serialize(value: Long): String {
        return value.toString()
      }

      override fun deserialize(strValue: String): Long {
        return strValue.toLong()
      }
    }
    private val PASSTHRU_CONVERTER: ValueConverter<String> = object : ValueConverter<String> {
      override fun serialize(value: String): String {
        return value
      }

      override fun deserialize(strValue: String): String {
        return strValue
      }
    }
      ///**
      // * Creates a {@link ValueConverter} for the given enum class. Values are stored using their
      // * names, to make it easier to override them using JVM properties (lower-case names are also
      // * recognized).
      // *
      // * @see Enum#name()
      // */
      //private static <T extends Enum<T>> ValueConverter<T> enumConverter(Class<T> enumClass) {
      //    return new ValueConverter<T>() {
      //
      //        @NonNull
      //        @Override
      //        public String serialize(@NonNull T value) {
      //            return value.name();
      //        }
      //
      //        @NonNull
      //        @Override
      //        public T deserialize(@NonNull String strValue) {
      //            return Enum.valueOf(enumClass, strValue.toUpperCase(Locale.US));
      //        }
      //    };
      //}

    /**
     * Verify that a flag's ID is correctly formatted, i.e. consisting of only lower-case letters,
     * numbers, and periods. Furthermore, the first character of an ID must be a letter and cannot
     * end with one.
     */
    fun verifyFlagIdFormat(id: String) {
      require(id.matches("[a-z][a-z0-9]*(\\.[a-z0-9]+)*".toRegex())) { "Invalid id: $id" }
    }

    /** Verify that display text is correctly formatted.  */
    fun verifyDisplayTextFormat(name: String) {
      require(!(name.isEmpty() || name[0] == ' ' || name[name.length - 1] == ' ')) { "Invalid name: $name" }
    }

    private fun <T> verifyDefaultValue(
      defaultValue: T,
      stringDefaultValue: String,
      converter: ValueConverter<T>
    ) {
      val deserialized: T
      deserialized = try {
        converter.deserialize(stringDefaultValue)
      } catch (e: Exception) {
        throw IllegalArgumentException("Default value cannot be deserialized.")
      }
      require(deserialized == defaultValue) { "Default value cannot be deserialized." }
    }

    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Boolean
    ): Flag<Boolean> {
      return Flag(group, name, displayName, description, defaultValue, BOOL_CONVERTER)
    }

    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Int
    ): Flag<Int> {
      return Flag(group, name, displayName, description, defaultValue, INT_CONVERTER)
    }

    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Long
    ): Flag<Long> {
      return Flag(group, name, displayName, description, defaultValue, LONG_CONVERTER)
    }

    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: String
    ): Flag<String> {
      return Flag(group, name, displayName, description, defaultValue, PASSTHRU_CONVERTER)
    }

    fun <T : Enum<T>?> create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: T
    ): Flag<T> {
      return Flag<T>(
        group,
        name,
        displayName,
        description,
        defaultValue,
        enumConverter(defaultValue.javaClass as Class<T>?)
      )
    }
  }
}
