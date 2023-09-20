/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.ide.common.resources.LocaleManager
import com.android.tools.lint.checks.PluralsDatabase.Quantity
import com.google.common.base.Charsets
import com.google.common.base.Objects
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.util.EnumSet
import java.util.Locale
import junit.framework.TestCase

class PluralsDatabaseTest : TestCase() {
  fun testGetRelevant() {
    val db = PluralsDatabase.LATEST
    assertNull(db.getRelevant("unknown"))
    val enRelevant = db.getRelevant("en")
    assertNotNull(enRelevant)
    assertEquals(1, enRelevant!!.size)
    assertSame(Quantity.one, enRelevant.iterator().next())
    val csRelevant = db.getRelevant("cs")
    assertNotNull(csRelevant)
    assertEquals(EnumSet.of(Quantity.few, Quantity.one, Quantity.many), csRelevant)
  }

  fun testFindExamples() {
    val db = PluralsDatabase.LATEST
    assertEquals(
      "1, 101, 201, 301, 401, 501, 601, 701, 1001, \u2026",
      db.findIntegerExamples("sl", Quantity.one)
    )
    assertEquals(
      "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026",
      db.findIntegerExamples("ru", Quantity.one)
    )
  }

  fun testHasMultiValue() {
    val db = PluralsDatabase.LATEST
    assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.one))
    assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.two))
    assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.few))
    assertFalse(db.hasMultipleValuesForQuantity("en", Quantity.many))
    assertTrue(db.hasMultipleValuesForQuantity("br", Quantity.two))
    assertTrue(db.hasMultipleValuesForQuantity("mk", Quantity.one))
    assertTrue(db.hasMultipleValuesForQuantity("lv", Quantity.zero))
  }

  /**
   * If the lint unit test data/ folder contains a plurals.txt database file, this test will parse
   * that file and ensure that our current database produces exactly the same results as those
   * inferred from the file. If not, it will dump out updated data structures for the database.
   *
   * Last update: downloaded icu4c-69_1-data.zip and copied data/misc/plurals.txt into
   * tools/base/lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks/data
   */
  fun testDatabaseAccurate() {
    val languages = LocaleManager.getLanguageCodes()
    languages.sort()
    val db = PluralsTextDatabase.get()
    db.ensureInitialized()
    if (db.getSetName("en") == null) {
      // plurals.txt not found
      println("No plurals.txt database included; not checking consistency")
      return
    }

    // Ensure that the two databases (the plurals.txt backed one and our actual
    // database) fully agree on everything
    val pdb = PluralsDatabase.LATEST
    for (language in languages) {
      if (!Objects.equal(pdb.getRelevant(language), db.getRelevant(language))) {
        dumpDatabaseTables()
        assertEquals(language, pdb.getRelevant(language), db.getRelevant(language))
      }
      if (db.getSetName(language) == null) {
        continue
      }
      for (q in Quantity.values()) {
        val mv1 = pdb.hasMultipleValuesForQuantity(language, q) // binary database
        val mv2 = db.hasMultipleValuesForQuantity(language, q) // text database
        if (mv1 != mv2) {
          dumpDatabaseTables()
          assertEquals("$language with quantity $q", mv1, mv2)
        }
        if (mv2) {
          val e1 = pdb.findIntegerExamples(language, q)
          val e2 = db.findIntegerExamples(language, q)
          if (!Objects.equal(e1, e2)) {
            dumpDatabaseTables()
            assertEquals(language, e1, e2)
          }
        }
      }
    }
  }

  /** Plurals database backed by a plurals.txt file from ICU */
  private class PluralsTextDatabase {
    private lateinit var plurals: MutableMap<String, EnumSet<Quantity>>
    private val multiValueSetNames: MutableMap<Quantity, MutableSet<String?>> =
      Maps.newEnumMap(Quantity::class.java)
    private var ruleSetOffset = 0
    private val descriptions by lazy {
      val stream =
        PluralsDatabaseTest::class.java.getResourceAsStream("data/plurals.txt") ?: return@lazy ""
      stream.use { inputStream ->
        val descriptions = inputStream.readAllBytes().toString(Charsets.UTF_8)
        val ruleSetOffset = descriptions.indexOf("rules{")
        if (ruleSetOffset == -1) {
          if (DEBUG) {
            error("Cannot find rules block in plurals.txt")
          }
          ""
        } else {
          this@PluralsTextDatabase.ruleSetOffset = ruleSetOffset
          descriptions
        }
      }
    }

    private lateinit var setNamePerLanguage: MutableMap<String, String>

    fun getRelevant(language: String): EnumSet<Quantity>? {
      ensureInitialized()
      val relevantSet = plurals[language]
      if (relevantSet == null) {
        val localeData = getLocaleData(language)
        if (localeData == null) {
          plurals[language] = EMPTY_SET
          return null
        }
        // Process each item and look for relevance
        val newSet = EnumSet.noneOf(Quantity::class.java)
        val length = localeData.length
        var offset = 0
        var end: Int
        while (offset < length) {
          while (offset < length) {
            if (!Character.isWhitespace(localeData[offset])) {
              break
            }
            offset++
          }
          val begin = localeData.indexOf('{', offset)
          if (begin == -1) {
            break
          }
          end = findBalancedEnd(localeData, begin)
          if (end == -1) {
            end = length
          }
          if (localeData.startsWith("other{", offset)) {
            // Not included
            offset = end + 1
            continue
          }
          if (localeData.startsWith("one{", offset)) {
            newSet.add(Quantity.one)
          } else if (localeData.startsWith("few{", offset)) {
            newSet.add(Quantity.few)
          } else if (localeData.startsWith("many{", offset)) {
            newSet.add(Quantity.many)
          } else if (localeData.startsWith("two{", offset)) {
            newSet.add(Quantity.two)
          } else if (localeData.startsWith("zero{", offset)) {
            newSet.add(Quantity.zero)
          } else {
            // Unexpected quantity: ignore
            if (DEBUG) {
              assert(false) { localeData.substring(offset, Math.min(offset + 10, length)) }
            }
          }
          offset = end + 1
        }
        plurals[language] = newSet
      }
      return if (relevantSet === EMPTY_SET) null else relevantSet
    }

    fun hasMultipleValuesForQuantity(language: String, quantity: Quantity): Boolean {
      if (quantity == Quantity.one || quantity == Quantity.two || quantity == Quantity.zero) {
        ensureInitialized()
        val setName = getSetName(language)
        if (setName != null) {
          val names = multiValueSetNames[quantity] ?: error(quantity)
          return names.contains(setName)
        }
      }
      return false
    }

    fun ensureInitialized() {
      if (!::plurals.isInitialized) {
        initialize()
      }
    }

    private fun initialize() {
      // Sets where more than a single integer maps to the quantity. Take for example
      // set 10:
      //    set10{
      //        one{
      //            "n % 10 = 1 and n % 100 != 11 @integer 1, 21, 31, 41, 51, 61, 71, 81,"
      //            " 101, 1001, … @decimal 1.0, 21.0, 31.0, 41.0, 51.0, 61.0, 71.0, 81.0"
      //            ", 101.0, 1001.0, …"
      //        }
      //    }
      // Here we see that both "1" and "21" will match the "one" category.
      // Note that this only applies to integers (since getQuantityString only takes integer)
      // whereas the plurals data also covers fractions. I was not sure what to do about
      // set17:
      //    set17{
      //        one{"i = 0,1 and n != 0 @integer 1 @decimal 0.1~1.6"}
      //    }
      // since it looks to me like this only differs from 1 in the fractional part.
      setNamePerLanguage = Maps.newHashMapWithExpectedSize(20)
      val quantities = arrayOf(Quantity.zero, Quantity.one, Quantity.two)
      for (quantity in quantities) {
        multiValueSetNames[quantity] = Sets.newHashSet()
        for (language in LocaleManager.getLanguageCodes()) {
          val examples = findIntegerExamples(language, quantity)
          if (examples != null && examples.indexOf(',') != -1) {
            val setName = getSetName(language)
            assertNotNull(setName)
            val set = multiValueSetNames[quantity]!!
            assertNotNull(set)
            set.add(setName)
          }
        }
      }
      plurals = Maps.newHashMapWithExpectedSize(20)
    }

    fun findIntegerExamples(language: String, quantity: Quantity): String? {
      val data = getQuantityData(language, quantity) ?: return null
      val index = data.indexOf("@integer")
      if (index == -1) {
        return null
      }
      val start = index + "@integer".length
      var end = data.indexOf('@', start)
      if (end == -1) {
        end = data.length
      }
      return data.substring(start, end).trim { it <= ' ' }
    }

    fun getQuantityData(language: String, quantity: Quantity): String? {
      val data = getLocaleData(language) ?: return null
      val quantityDeclaration = quantity.name + "{"
      val quantityStart = data.indexOf(quantityDeclaration)
      if (quantityStart == -1) {
        return null
      }
      val quantityEnd = findBalancedEnd(data, quantityStart)
      if (quantityEnd == -1) {
        return null
      }
      // String s = data.substring(quantityStart + quantityDeclaration.length(), quantityEnd);
      val sb = StringBuilder()
      var inString = false
      for (i in quantityStart + quantityDeclaration.length until quantityEnd) {
        val c = data[i]
        if (c == '"') {
          inString = !inString
        } else if (inString) {
          sb.append(c)
        }
      }
      return sb.toString()
    }

    fun getSetName(language: String): String? {
      var name = setNamePerLanguage[language]
      if (name == null) {
        name = findSetName(language)
        if (name == null) {
          name = "" // Store "" instead of null so we remember search result
        }
        setNamePerLanguage[language] = name
      }
      return name.ifEmpty { null }
    }

    private fun findSetName(language: String): String? {
      val data = descriptions
      var index = data.indexOf("locales{")
      if (index == -1) {
        return null
      }
      val end = data.indexOf("locales_ordinals{", index + 1)
      if (end == -1) {
        return null
      }
      val languageDeclaration = " $language{\""
      index = data.indexOf(languageDeclaration)
      if (index == -1 || index >= end) {
        return null
      }
      val setEnd = data.indexOf('\"', index + languageDeclaration.length)
      return if (setEnd == -1) {
        null
      } else {
        data.substring(index + languageDeclaration.length, setEnd).trim { it <= ' ' }
      }
    }

    fun getLocaleData(language: String): String? {
      val set = getSetName(language) ?: return null
      val data = descriptions
      val setStart = data.indexOf("$set{", ruleSetOffset)
      if (setStart == -1) {
        return null
      }
      val setEnd = findBalancedEnd(data, setStart)
      return if (setEnd == -1) {
        null
      } else {
        data.substring(setStart + set.length + 1, setEnd)
      }
    }

    companion object {
      private const val DEBUG = false
      private val EMPTY_SET = EnumSet.noneOf(Quantity::class.java)
      private val instance = PluralsTextDatabase()

      fun get(): PluralsTextDatabase {
        return instance
      }

      private fun findBalancedEnd(data: String, offset: Int): Int {
        var offset = offset
        var balance = 0
        val length = data.length
        while (offset < length) {
          val c = data[offset]
          if (c == '{') {
            balance++
          } else if (c == '}') {
            balance--
            if (balance == 0) {
              return offset
            }
          }
          offset++
        }
        return -1
      }
    }
  }

  companion object {
    private fun dumpDatabaseTables() {
      val languages = LocaleManager.getLanguageCodes().sorted()
      val db = PluralsTextDatabase.get()
      db.ensureInitialized()
      db.getRelevant("en") // ensure initialized
      val languageMap = mutableMapOf<String, String>()
      val setMap = mutableMapOf<String, EnumSet<Quantity>>()
      for (language in languages) {
        val set = db.getSetName(language) ?: continue
        val quantitySet =
          db.getRelevant(language)
            ?: // No plurals data for this language. For example, in ICU 52, no
            // plurals data for the "nv" language (Navajo).
            continue
        assertNotNull(language, quantitySet)
        setMap[set] = quantitySet
        languageMap[set] = language // Could be multiple
      }
      val setNames = setMap.keys.sorted()

      // Compute uniqueness
      val sameAs = mutableMapOf<String, String>()
      var i = 0
      val n = setNames.size
      while (i < n) {
        for (j in i + 1 until n) {
          var iSetName = setNames[i]
          val jSetName = setNames[j]
          assertNotNull(iSetName)
          assertNotNull(jSetName)
          val iSet = setMap[iSetName]!!
          val jSet = setMap[jSetName]!!
          assertNotNull(iSet)
          assertNotNull(jSet)
          if (iSet == jSet) {
            val alias = sameAs[iSetName]
            if (alias != null) {
              iSetName = alias
            }
            sameAs[jSetName] = iSetName
            break
          }
        }
        i++
      }

      // Multi Value Set names
      val sets = mutableSetOf<String>()
      for (language in languages) {
        val set = db.getSetName(language) ?: continue
        sets.add(set)
        languageMap[set] = language // Could be multiple
      }
      val indices = Maps.newTreeMap<String, Int>()
      var index = 0
      for (set in setNames) {
        indices[set] = index++
      }

      // Language indices
      val languageIndices: MutableMap<String, Int> = Maps.newTreeMap()
      index = 0
      for (language in languages) {
        db.getSetName(language) ?: continue
        languageIndices[language] = index++
      }
      val zero = computeExamples(db, Quantity.zero, sets, languageMap)
      val one = computeExamples(db, Quantity.one, sets, languageMap)
      val two = computeExamples(db, Quantity.two, sets, languageMap)

      val output = buildString {
        appendLine(
          """
          |// GENERATED DATA.
          |// This data is generated by the #testDatabaseAccurate method in PluralsDatasetTest
          |// which will generate the following if it can find an ICU plurals database file
          |// in the unit test data folder.
          |
          |object CLDR41Dataset : PluralsDataset(
          |    languageCodes = arrayOf(
        """
            .trimMargin()
        )
        val printedLanguages = languages.filter { db.getSetName(it) != null }
        val lines = printedLanguages.chunked(10)
        for (line in lines) {
          appendLine("        ${line.joinToString(" ") { "\"$it\"," }}")
        }
        appendLine("    ),")
        appendLine(
          """
          |    languageFlags = intArrayOf(
        """
            .trimMargin()
        )

        for (line in lines) {
          append("        ")
          for (language in line) {
            val setName = db.getSetName(language)

            // Compute flag
            var flag = 0
            val relevant = db.getRelevant(language)
            assertNotNull(relevant)
            if (relevant!!.contains(Quantity.zero)) {
              flag = flag or PluralsDatabase.FLAG_ZERO
            }
            if (relevant.contains(Quantity.one)) {
              flag = flag or PluralsDatabase.FLAG_ONE
            }
            if (relevant.contains(Quantity.two)) {
              flag = flag or PluralsDatabase.FLAG_TWO
            }
            if (relevant.contains(Quantity.few)) {
              flag = flag or PluralsDatabase.FLAG_FEW
            }
            if (relevant.contains(Quantity.many)) {
              flag = flag or PluralsDatabase.FLAG_MANY
            }
            if (zero.containsKey(setName)) {
              flag = flag or PluralsDatabase.FLAG_MULTIPLE_ZERO
            }
            if (one.containsKey(setName)) {
              flag = flag or PluralsDatabase.FLAG_MULTIPLE_ONE
            }
            if (two.containsKey(setName)) {
              flag = flag or PluralsDatabase.FLAG_MULTIPLE_TWO
            }
            append(String.format(Locale.US, "0x%04x, ", flag))
          }
          appendLine()
        }

        appendLine("    )")
        appendLine(") {")
      }

      println(output)

      // Switch statement methods for examples
      printWhen(db, Quantity.zero, languages, languageIndices, indices, zero)
      printWhen(db, Quantity.one, languages, languageIndices, indices, one)
      printWhen(db, Quantity.two, languages, languageIndices, indices, two)
      println("}")
    }

    private fun stripLastComma(s: String): String {
      val stringBuilder = StringBuilder(s)
      stripLastComma(stringBuilder)
      return stringBuilder.toString()
    }

    private fun stripLastComma(sb: StringBuilder) {
      for (i in sb.length - 1 downTo 1) {
        val c = sb[i]
        if (!Character.isWhitespace(c)) {
          if (c == ',') {
            sb.setLength(i)
          }
          break
        }
      }
    }

    private fun computeExamples(
      db: PluralsTextDatabase,
      quantity: Quantity,
      sets: Set<String>,
      languageMap: Map<String, String>
    ): Map<String, String> {
      val setsWithExamples = Maps.newHashMap<String, String>()
      for (set in sets) {
        val language = languageMap[set]
        val examples = db.findIntegerExamples(language!!, quantity)
        if (examples != null && examples.indexOf(',') != -1) {
          setsWithExamples[set] = examples
        }
      }
      return setsWithExamples
    }

    private fun printWhen(
      db: PluralsTextDatabase,
      quantity: Quantity,
      languages: List<String>,
      languageIndices: Map<String, Int>,
      indices: Map<String, Int>,
      setsWithExamples: Map<String, String>
    ) {
      val output = buildString {
        val quantityName =
          quantity.name.let { name -> name[0].uppercaseChar().toString() + name.substring(1) }
        appendLine(
          """
          |    override fun getExampleForQuantity$quantityName(language: String): String? {
          |        return when (getLanguageIndex(language)) {
        """
            .trimMargin()
        )

        for ((set) in indices) {
          if (!setsWithExamples.containsKey(set)) {
            continue
          }
          val example = setsWithExamples[set]!!.replace("…", "\\u2026")
          appendLine("            // $set")
          val relevantLanguages =
            languages.mapNotNull { language ->
              val setName = db.getSetName(language)
              if (set == setName) {
                val languageIndex = languageIndices[language]!!
                languageIndex to language
              } else {
                null
              }
            }
          appendLine(
            """
            |            ${relevantLanguages.joinToString { (i, _) -> i.toString()}} ->
            |                // ${relevantLanguages.joinToString { (_, lang) -> lang }}
            |                "$example"
            """
              .trimMargin()
          )
        }
        append(
          """
          |            else -> null
          |        }
          |    }
          |
          """
            .trimMargin()
        )
      }
      println(output)
    }
  }
}
