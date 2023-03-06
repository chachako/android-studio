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
package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.getErrorLines
import java.io.File
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.intellij.lang.annotations.Language
import org.junit.Test

class LintTomlParserTest {
  @Test
  fun testSimple() {
    // Test case from https://toml.io/en/v1.0.0#comment
    val source =
      // language=toml
      """
            # This is a full-line comment
            key = "value"  # This is a comment at the end of a line
            another = "# This is not a comment"
            """
        .trimIndent()
    checkToml(source) {
      val document = it.describe()
      assertEquals(
        """
                key="value", String = value
                  key = "value"  # This is a comment at the end of a line
                  ~~~   ~~~~~~~
                another="# This is not a comment", String = # This is not a comment
                  another = "# This is not a comment"
                  ~~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~
                """
          .trimIndent(),
        document.trim()
      )
    }
  }

  @Test
  fun testKeys() {
    // Test case from https://toml.io/en/v1.0.0#keys
    val source =
      // language=toml
      """
            key = "value"
            bare_key = "value"
            bare-key = "value"
            1234 = "value"
            "127.0.0.1" = "value"
            "character encoding" = "value"
            "ʎǝʞ" = "value"
            'key2' = "value"
            'quoted "value"' = "value"
            "" = "blank"     # VALID but discouraged
            name = "Orange"
            physical.color = "orange"
            physical.shape = "round"
            site."google.com" = true
            site."with \"quoted\"" = false
            fruit . flavor = "banana"   # same as fruit.flavor
            """
        .trimIndent()
    checkToml(source) { result ->
      val described = result.describe()
      assertEquals(
        """
                key="value", String = value
                  key = "value"
                  ~~~   ~~~~~~~
                bare_key="value", String = value
                  bare_key = "value"
                  ~~~~~~~~   ~~~~~~~
                bare-key="value", String = value
                  bare-key = "value"
                  ~~~~~~~~   ~~~~~~~
                1234="value", String = value
                  1234 = "value"
                  ~~~~   ~~~~~~~
                127.0.0.1="value", String = value
                  "127.0.0.1" = "value"
                  ~~~~~~~~~~~   ~~~~~~~
                character encoding="value", String = value
                  "character encoding" = "value"
                  ~~~~~~~~~~~~~~~~~~~~   ~~~~~~~
                ʎǝʞ="value", String = value
                  "ʎǝʞ" = "value"
                  ~~~~~   ~~~~~~~
                key2="value", String = value
                  'key2' = "value"
                  ~~~~~~   ~~~~~~~
                quoted "value"="value", String = value
                  'quoted "value"' = "value"
                  ~~~~~~~~~~~~~~~~   ~~~~~~~
                ="blank", String = blank
                  "" = "blank"     # VALID but discouraged
                  ~~   ~~~~~~~
                name="Orange", String = Orange
                  name = "Orange"
                  ~~~~   ~~~~~~~~
                physical.color="orange", String = orange
                  physical.color = "orange"
                  ~~~~~~~~~~~~~~   ~~~~~~~~
                physical.shape="round", String = round
                  physical.shape = "round"
                  ~~~~~~~~~~~~~~   ~~~~~~~
                site.google.com=true, Boolean = true
                  site."google.com" = true
                  ~~~~~~~~~~~~~~~~~   ~~~~
                site.with "quoted"=false, Boolean = false
                  site."with \"quoted\"" = false
                  ~~~~~~~~~~~~~~~~~~~~~~   ~~~~~
                fruit.flavor="banana", String = banana
                  fruit . flavor = "banana"   # same as fruit.flavor
                  ~~~~~~~~~~~~~~   ~~~~~~~~
                """
          .trimIndent(),
        described.trim()
      )
      // Here above we've flattened site."google.com" into site.google.com when pretty printing the
      // key,
      // but in the model result's treated properly; check that
      val document = result.document
      assertNull(document.getValue("site.google.com"))
      assertEquals(true, document.getValue(listOf("site", "google.com"))?.getActualValue())
      assertEquals(true, document.getValue("site.\"google.com\"")?.getActualValue())
      assertEquals(false, document.getValue("site.\"with \\\"quoted\\\"\"")?.getActualValue())
    }
  }

  @Test
  fun testStrings() {
    // Test cases from https://toml.io/en/v1.0.0#string
    val source =
      // language=toml
      """
            str1 = ""${'"'}
            Roses are red
            Violets are blue""${'"'}
            str3 = "Roses are red\r\nViolets are blue"
            str2 = ""${'"'}
            The quick brown \


              fox jumps over \
                the lazy dog.""${'"'}
            str4 = ""${'"'}Here are two quotation marks: "". Simple enough.""${'"'}
            # What you see is what you get.
            winpath  = 'C:\Users\nodejs\templates'
            winpath2 = '\\ServerX\admin${'$'}\system32\'
            quoted   = 'Tom "Dubs" Preston-Werner'
            regex    = '<\i\c*\s*>'

            regex2 = '''I [dw]on't need \d{2} apples'''
            lines  = '''
            The first newline is
            trimmed in raw strings.
               All other whitespace
               is preserved.
            '''

            quot15 = '''Here are fifteen quotation marks: ""${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}${'"'}'''

            # apos15 = '''Here are fifteen apostrophes: ''''''''''''''''''  # INVALID
            apos15 = "Here are fifteen apostrophes: '''''''''''''''"

            # 'That,' she said, 'is still pointless.'
            str = ''''That,' she said, 'is still pointless.''''
            """
        .trimIndent()
    checkToml(source) {
      val document = it.describe()
      assertEquals(
        // not using raw string since we have trailing whitespace
        "" +
          "str1=\"\"\"\n" +
          "Roses are red\n" +
          "Violets are blue\"\"\", String = Roses are red\n" +
          "Violets are blue\n" +
          "  str1 = \"\"\"\n" +
          "  ~~~~   ^\n" +
          "str3=\"Roses are red\\r\\nViolets are blue\", String = Roses are red\r\n" +
          "Violets are blue\n" +
          "  str3 = \"Roses are red\\r\\nViolets are blue\"\n" +
          "  ~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "str2=\"\"\"\n" +
          "The quick brown \\\n" +
          "\n" +
          "\n" +
          "  fox jumps over \\\n" +
          "    the lazy dog.\"\"\", String = The quick brown fox jumps over the lazy dog.\n" +
          "  str2 = \"\"\"\n" +
          "  ~~~~   ^\n" +
          "str4=\"\"\"Here are two quotation marks: \"\". Simple enough.\"\"\", String = Here are two quotation marks: \"\". Simple enough.\n" +
          "  str4 = \"\"\"Here are two quotation marks: \"\". Simple enough.\"\"\"\n" +
          "  ~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "winpath='C:\\Users\\nodejs\\templates', String = C:\\Users\\nodejs\\templates\n" +
          "  winpath  = 'C:\\Users\\nodejs\\templates'\n" +
          "  ~~~~~~~    ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "winpath2='\\\\ServerX\\admin\$\\system32\\', String = \\\\ServerX\\admin\$\\system32\\\n" +
          "  winpath2 = '\\\\ServerX\\admin\$\\system32\\'\n" +
          "  ~~~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "quoted='Tom \"Dubs\" Preston-Werner', String = Tom \"Dubs\" Preston-Werner\n" +
          "  quoted   = 'Tom \"Dubs\" Preston-Werner'\n" +
          "  ~~~~~~     ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "regex='<\\i\\c*\\s*>', String = <\\i\\c*\\s*>\n" +
          "  regex    = '<\\i\\c*\\s*>'\n" +
          "  ~~~~~      ~~~~~~~~~~~~\n" +
          "regex2='''I [dw]on't need \\d{2} apples''', String = I [dw]on't need \\d{2} apples\n" +
          "  regex2 = '''I [dw]on't need \\d{2} apples'''\n" +
          "  ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "lines='''\n" +
          "The first newline is\n" +
          "trimmed in raw strings.\n" +
          "   All other whitespace\n" +
          "   is preserved.\n" +
          "''', String = The first newline is\n" +
          "trimmed in raw strings.\n" +
          "   All other whitespace\n" +
          "   is preserved.\n" +
          "\n" +
          "  lines  = '''\n" +
          "  ~~~~~    ^\n" +
          "quot15='''Here are fifteen quotation marks: \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"''', String = Here are fifteen quotation marks: \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\n" +
          "  quot15 = '''Here are fifteen quotation marks: \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"'''\n" +
          "  ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "apos15=\"Here are fifteen apostrophes: '''''''''''''''\", String = Here are fifteen apostrophes: '''''''''''''''\n" +
          "  apos15 = \"Here are fifteen apostrophes: '''''''''''''''\"\n" +
          "  ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "str=''''That,' she said, 'is still pointless.'''', String = 'That,' she said, 'is still pointless.'\n" +
          "  str = ''''That,' she said, 'is still pointless.''''\n" +
          "  ~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
        document.trim()
      )
    }
  }

  @Test
  fun testNumbersAndDates() {
    // Test cases from https://toml.io/en/v1.0.0#integer and https://toml.io/en/v1.0.0#float and
    // https://toml.io/en/v1.0.0#boolean
    val source =
      // language=toml
      """
            int1 = +99
            int2 = 42
            int3 = 0
            int4 = -17
            int5 = 1_000
            int6 = 5_349_221
            int7 = 53_49_221  # Indian number system grouping
            int8 = 1_2_3_4_5  # VALID but discouraged
            # hexadecimal with prefix `0x`
            hex1 = 0xDEADBEEF
            hex2 = 0xdeadbeef
            hex3 = 0xdead_beef

            # octal with prefix `0o`
            oct1 = 0o01234567
            oct2 = 0o755 # useful for Unix file permissions

            # binary with prefix `0b`
            bin1 = 0b11010110
            # fractional
            flt1 = +1.0
            flt2 = 3.1415
            flt3 = -0.01

            # exponent
            flt4 = 5e+22
            flt5 = 1e06
            flt6 = -2E-2

            # both
            flt7 = 6.626e-34
            flt8 = 224_617.445_991_228
            # infinity
            sf1 = inf  # positive infinity
            sf2 = +inf # positive infinity
            sf3 = -inf # negative infinity

            # not a number
            sf4 = nan  # actual sNaN/qNaN encoding is implementation-specific
            sf5 = +nan # same as `nan`
            sf6 = -nan # valid, actual encoding is implementation-specific
            bool1 = true
            bool2 = false

            # https://toml.io/en/v1.0.0#offset-date-time
            odt1 = 1979-05-27T07:32:00Z
            odt2 = 1979-05-27T00:32:00-07:00
            odt3 = 1979-05-27T00:32:00.999999-07:00
            ld1 = 1979-05-27
            ldt1 = 1979-05-27T07:32:00
            ldt2 = 1979-05-27T00:32:00.999999
            lt1 = 07:32:00
            lt2 = 00:32:00.999999
            """
        .trimIndent()
    checkToml(source) {
      val document = it.describe(includeSources = false)
      assertEquals(
        """
                int1=+99, Integer = 99
                int2=42, Integer = 42
                int3=0, Integer = 0
                int4=-17, Integer = -17
                int5=1_000, Integer = 1000
                int6=5_349_221, Integer = 5349221
                int7=53_49_221, Integer = 5349221
                int8=1_2_3_4_5, Integer = 12345
                hex1=0xDEADBEEF, Long = 3735928559
                hex2=0xdeadbeef, Long = 3735928559
                hex3=0xdead_beef, Long = 3735928559
                oct1=0o01234567, Long = 342391
                oct2=0o755, Long = 493
                bin1=0b11010110, Long = 214
                flt1=+1.0, Double = 1.0
                flt2=3.1415, Double = 3.1415
                flt3=-0.01, Double = -0.01
                flt4=5e+22, Double = 4.9999999999999996E22
                flt5=1e06, Double = 1000000.0
                flt6=-2E-2, Double = -0.02
                flt7=6.626e-34, Double = 6.626E-34
                flt8=224_617.445_991_228, Double = 224617.445991228
                sf1=inf, Double = Infinity
                sf2=+inf, Double = Infinity
                sf3=-inf, Double = -Infinity
                sf4=nan, Double = NaN
                sf5=+nan, Double = NaN
                sf6=-nan, Double = NaN
                bool1=true, Boolean = true
                bool2=false, Boolean = false
                odt1=1979-05-27T07:32:00Z, Instant = 1979-05-27T07:32:00Z
                odt2=1979-05-27T00:32:00-07:00, Instant = 1979-05-27T07:32:00Z
                odt3=1979-05-27T00:32:00.999999-07:00, Instant = 1979-05-27T07:32:00.999999Z
                ld1=1979-05-27, LocalDate = 1979-05-27
                ldt1=1979-05-27T07:32:00, LocalDateTime = 1979-05-27T07:32
                ldt2=1979-05-27T00:32:00.999999, LocalDateTime = 1979-05-27T00:32:00.999999
                lt1=07:32:00, LocalTime = 07:32
                lt2=00:32:00.999999, LocalTime = 00:32:00.999999
                """
          .trimIndent(),
        document.trim()
      )
    }
  }

  @Test
  fun testArrayWithInlineTableToString() {
    val source =
      // language=toml
      """
            [bundles]
            groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
            """
        .trimIndent()
    checkToml(source) {
      val document = it.describe()
      assertEquals(
        """
                bundles.groovy[0]="groovy-core", String = groovy-core
                  groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
                            ~~~~~~~~~~~~~
                bundles.groovy[1]="groovy-json", String = groovy-json
                  groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
                                           ~~~~~~~~~~~~~
                bundles.groovy[2].name="groovy-nio", String = groovy-nio
                  groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
                                                            ~~~~   ~~~~~~~~~~~~
                bundles.groovy[2].version="3.14", String = 3.14
                  groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
                                                                                 ~~~~~~~   ~~~~~~
                """
          .trimIndent(),
        document.trim()
      )
    }
  }

  @Test
  fun testInlineTables() {
    // Test case from https://toml.io/en/v1.0.0#inline-table
    val source =
      // language=toml
      """
            name = { first = "Tom", last = "Preston-Werner" }
            point = { x = 1, y = 2 }
            animal = { type.name = "pug" }
            """
        .trimIndent()
    checkToml(source) { result ->
      val dump = result.describe()
      assertEquals(
        """
                name.first="Tom", String = Tom
                  name = { first = "Tom", last = "Preston-Werner" }
                           ~~~~~   ~~~~~
                name.last="Preston-Werner", String = Preston-Werner
                  name = { first = "Tom", last = "Preston-Werner" }
                                          ~~~~   ~~~~~~~~~~~~~~~~
                point.x=1, Integer = 1
                  point = { x = 1, y = 2 }
                            ~   ~
                point.y=2, Integer = 2
                  point = { x = 1, y = 2 }
                                   ~   ~
                animal.type.name="pug", String = pug
                  animal = { type.name = "pug" }
                             ~~~~~~~~~   ~~~~~
                """
          .trimIndent(),
        dump.trim()
      )
      val document = result.document
      assertEquals(2, document.getValue(listOf("point", "y"))?.getActualValue())
      assertEquals("pug", document.getValue("animal.type.name")?.getActualValue())
      val value = document.getValue(listOf("animal", "type", "name"))!!
      assertEquals("pug", value.getActualValue())
      // spot check ranges too
      assertEquals(
        "type.name",
        document.getSource().substring(value.getKeyStartOffset(), value.getKeyEndOffset())
      )
      assertEquals(
        "\"pug\"",
        document.getSource().substring(value.getStartOffset(), value.getEndOffset())
      )
    }
  }

  @Test
  fun testArrayOfTables() {
    // Test case from https://toml.io/en/v1.0.0#array-of-tables
    val source =
      // language=toml
      """
            [[products]]
            name = "Hammer"
            sku = 738594937

            [[products]]  # empty table within the array

            [[products]]
            name = "Nail"
            sku = 284758393

            color = "gray"
            """
        .trimIndent()

    /* From https://toml.io/en/v1.0.0#array-of-tables
       {
         "products": [
           { "name": "Hammer", "sku": 738594937 },
           { },
           { "name": "Nail", "sku": 284758393, "color": "gray" }
         ]
       }
    */
    val expected =
      mapOf(
        "products" to
          listOf(
            mapOf("name" to "Hammer", "sku" to 738594937),
            mutableMapOf(),
            mapOf("name" to "Nail", "sku" to 284758393, "color" to "gray")
          )
      )
    doTest(source, expected)
  }

  @Test
  fun testGradleVersionCatalog() {
    val source =
      // language=toml
      """
            [versions]
            activityCompose = "1.7.0-alpha02"
            appCompat = "1.5.1"
            hiltNavigationCompose = "1.0.0"

            [libraries]
            androidx-activity-activityCompose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
            androidx-hilt-hiltNavigationCompose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
            androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }

            [bundles]
            androidx = [
                "androidx-activity-activityCompose",
                "androidx-appCompat",
            ]
            """
        .trimIndent()
    checkToml(source) {
      val dump = it.describe()
      assertEquals(
        """
                versions.activityCompose="1.7.0-alpha02", String = 1.7.0-alpha02
                  activityCompose = "1.7.0-alpha02"
                  ~~~~~~~~~~~~~~~   ~~~~~~~~~~~~~~~
                versions.appCompat="1.5.1", String = 1.5.1
                  appCompat = "1.5.1"
                  ~~~~~~~~~   ~~~~~~~
                versions.hiltNavigationCompose="1.0.0", String = 1.0.0
                  hiltNavigationCompose = "1.0.0"
                  ~~~~~~~~~~~~~~~~~~~~~   ~~~~~~~
                libraries.androidx-activity-activityCompose.module="androidx.activity:activity-compose", String = androidx.activity:activity-compose
                  androidx-activity-activityCompose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
                                                        ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                libraries.androidx-activity-activityCompose.version.ref="activityCompose", String = activityCompose
                  androidx-activity-activityCompose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
                                                                                                       ~~~~~~~~~~~   ~~~~~~~~~~~~~~~~~
                libraries.androidx-hilt-hiltNavigationCompose.module="androidx.hilt:hilt-navigation-compose", String = androidx.hilt:hilt-navigation-compose
                  androidx-hilt-hiltNavigationCompose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
                                                          ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                libraries.androidx-hilt-hiltNavigationCompose.version.ref="hiltNavigationCompose", String = hiltNavigationCompose
                  androidx-hilt-hiltNavigationCompose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
                                                                                                            ~~~~~~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~
                libraries.androidx-appCompat.module="androidx.appcompat:appcompat", String = androidx.appcompat:appcompat
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                                         ~~~~~~   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                libraries.androidx-appCompat.version.ref="appCompat", String = appCompat
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                                                                                  ~~~~~~~~~~~   ~~~~~~~~~~~
                bundles.androidx[0]="androidx-activity-activityCompose", String = androidx-activity-activityCompose
                  "androidx-activity-activityCompose",
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                bundles.androidx[1]="androidx-appCompat", String = androidx-appCompat
                  "androidx-appCompat",
                  ~~~~~~~~~~~~~~~~~~~~
                """
          .trimIndent(),
        dump.trim()
      )
    }
  }

  @Test
  fun testErrorHandling() {
    // We're generally fault tolerant
    class Case(
      /* Not annotated since we're passing in broken code -- @Language("toml")*/
      val toml: String,
      val errors: String = "",
      val dump: String? = null,
    )

    fun verify(vararg cases: Case) {
      for (case in cases) {
        val document = parseToml(File("test.toml"), case.toml)
        val errors = document.describeProblems(includeSources = false)
        assertEquals(case.errors, errors)
        if (case.dump != null) {
          val dump = document.describe(includeSources = false, includeProblems = false)
          assertEquals(case.toml, case.dump.trimIndent().trim(), dump.trim())
        }
      }
    }

    @Suppress("ALL")
    verify(
      Case("key = # INVALID", "test.toml: 0:15: Warning: Value missing after ="),
      Case(
        "= \"no key name\" # INVALID\nkey=value",
        "test.toml: 0:1: Warning: Bare key must be non-empty",
        "key=value, String = value"
      ),
      Case(
        "\"\"\"key\"\"\" = \"not allowed\" # INVALID",
        "test.toml: 0:0: Warning: Multi-line strings not allowed in keys"
      ),
      Case("foo", "test.toml: 0:3: Warning: = missing after key `foo`"),
      Case("foo=", "test.toml: 0:4: Warning: Value missing after ="),
      Case("foo=\"", "test.toml: 0:4: Warning: Unterminated string"),
      Case("foo=\"\"\"", "test.toml: 0:4: Warning: Unterminated string"),
      Case("foo=\"\"\"\n\"\"", "test.toml: 0:4: Warning: Unterminated string"),
      Case("foo='", "test.toml: 0:4: Warning: Unterminated string"),
      Case("foo='''", "test.toml: 0:4: Warning: Unterminated string"),
      Case("foo=\"\"\"\n", "test.toml: 0:4: Warning: Unterminated string"),
      Case("[fo", "test.toml: 0:3: Warning: = missing ]`"),
      Case("[[fo]", "test.toml: 0:4: Warning: = missing ]]`"),
      Case(
        "foo=bar\nfoobar",
        "test.toml: 1:6: Warning: = missing after key `foobar`",
        "foo=bar, String = bar"
      ),
      // Recover from (and ignore) key without value in the middle
      Case(
        "foo=bar\nfoobar\nbar=baz",
        "test.toml: 1:6: Warning: Key cannot be alone on a line",
        "foo=bar, String = bar\nbar=baz, String = baz"
      ),
      Case(
        "first = \"Tom\" last = \"Preston-Werner\" # INVALID\n",
        "test.toml: 0:14: Warning: There must be a newline (or EOF) after a key/value pair"
      ),
      Case(
        "name = \"Tom\"\nname = \"Pradyun\"",
        "test.toml: 1:16: Warning: Defining a key (`name`) multiple times is invalid"
      ),
      Case(
        "spelling = \"favorite\"\n\"spelling\" = \"favourite\"\n",
        "test.toml: 1:24: Warning: Defining a key (`spelling`) multiple times is invalid"
      ),
      Case(
        "# This defines the value of fruit.apple to be an integer.\n" +
          "fruit.apple = 1\n" +
          "\n" +
          "# But then this treats fruit.apple like it's a table.\n" +
          "# You can't turn an integer into a table.\n" +
          "fruit.apple.smooth = true",
        "test.toml: 5:25: Warning: Table `fruit` already specified as a value"
      ),
      Case(
        "str5 = \"\"\"Here are three quotation marks: \"\"\".\"\"\"  # INVALID",
        "test.toml: 0:45: Warning: Unexpected content after string terminator"
      ),
      Case(
        "apos15 = '''Here are fifteen apostrophes: ''''''''''''''''''  # INVALID",
        "test.toml: 0:47: Warning: Unexpected content after string terminator"
      ),
      Case(
        "invalid_float_1 = .7",
        "test.toml: 0:18: Warning: The decimal point, if used, must be surrounded by at least one digit on each side"
      ),
      Case(
        "invalid_float_2 = 7.\n",
        "test.toml: 0:18: Warning: The decimal point, if used, must be surrounded by at least one digit on each side"
      ),
      Case(
        "invalid_float_2 = [7.]\n",
        "test.toml: 0:19: Warning: The decimal point, if used, must be surrounded by at least one digit on each side"
      ),
      Case(
        "invalid_float_3 = 3.e+20",
        "test.toml: 0:18: Warning: The decimal point, if used, must be surrounded by at least one digit on each side"
      ),
      Case(
        "[fruit]\n" + "apple = \"red\"\n" + "\n" + "[fruit]\n" + "orange = \"orange\"",
        "test.toml: 3:0: Warning: You cannot define a table (`fruit`) more than once"
      ),
      Case(
        "type.name = \"Nail\"\n" + "type = { edible = false }  # INVALID",
        "test.toml: 1:7: Warning: Inline tables cannot be used to add keys or sub-tables to an already-defined table"
      ),
      Case(
        "# INVALID TOML DOC\n" + "fruits = []\n" + "\n" + "[[fruits]] # Not allowed",
        "test.toml: 3:1: Warning: Attempting to append to a statically defined array is not allowed"
      ),
      Case(
        "# INVALID TOML DOC\n" +
          "[[fruits]]\n" +
          "name = \"apple\"\n" +
          "\n" +
          "[[fruits.varieties]]\n" +
          "name = \"red delicious\"\n" +
          "\n" +
          "# INVALID: This table conflicts with the previous array of tables\n" +
          "[fruits.varieties]",
        "test.toml: 8:0: Warning: You cannot define a table (`fruits.varieties`) more than once\n" +
          "test.toml: 2:0: Warning: Table `fruits` already specified as a value"
      ),
    )
  }

  // Tests adapted from the test cases in
  // com.android.tools.idea.gradle.dsl.parser.toml.TomlDslParserTest

  private fun parseTomlToMap(
    file: File,
    s: String,
    source: Boolean,
    validate: Boolean
  ): Map<String, Any> {
    val result = parseToml(file, s, validate)
    return if (source) {
      result.getSourceValueMap()
    } else {
      result.getValueMap()
    }
  }

  private fun doTest(@Language("TOML") toml: String, expected: Map<String, Any>) {
    val source = false
    for (validate in listOf(false, true)) {
      val map = parseTomlToMap(File("test.toml"), toml.trimIndent(), source, validate)
      assertEquals(expected, map)
    }
  }

  @Test
  fun testSingleLibraryLiteralString() {
    val toml =
      """
          [libraries]
          junit = 'junit:junit:4.13'
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryMultiLineLiteralString() {
    val toml =
      """
          [libraries]
          junit = '''junit:junit:4.13'''
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryBasicString() {
    val singleQuote = "\""
    val singleQuotedJunitWithEscapes =
      "junit:junit:4.13"
        .mapIndexed { i, c ->
          if ((i % 2) == 0) c.toString() else String.format("\\u%04x", c.toInt())
        }
        .joinToString(separator = "", prefix = singleQuote, postfix = singleQuote)
    val toml =
      """
          [libraries]
          junit = $singleQuotedJunitWithEscapes
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryMultiLineBasicString() {
    val tripleQuote = "\""
    val tripleQuotedJunitWithEscapes =
      "junit:junit:4.13"
        .mapIndexed { i, c ->
          if ((i % 2) == 1) c.toString() else String.format("\\u%04x", c.toInt())
        }
        .joinToString(separator = "", prefix = tripleQuote, postfix = tripleQuote)
    val toml =
      """
          [libraries]
          junit = $tripleQuotedJunitWithEscapes
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryMultiLineLiteralStringInitialNewline() {
    // In com.android.tools.idea.gradle.dsl.parser.toml.TomlDslParserTest's
    // _testSingleLibraryMultiLineLiteralStringInitialNewline
    // this is disabled because the TOML unescaper does not handle removal of initial newline
    val toml =
      """
          [libraries]
          junit = '''
          junit:junit:4.13'''
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryMultiLineBasicStringInitialNewline() {
    // In com.android.tools.idea.gradle.dsl.parser.toml.TomlDslParserTest's
    // _testSingleLibraryMultiLineBasicStringInitialNewline
    // this is disabled because the TOML unescaper does not handle removal of initial newline
    val tripleQuote = "\"\"\""
    val junitWithEscapes =
      "junit:junit:4.13"
        .mapIndexed { i, c ->
          if ((i % 2) == 1) c.toString() else String.format("\\u%04x", c.toInt())
        }
        .joinToString(separator = "")
    val toml =
      """
          [libraries]
          junit = $tripleQuote
          $junitWithEscapes$tripleQuote
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testLiteralStringKey() {
    val toml =
      """
          [libraries]
          'junit' = "junit:junit:4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testBasicStringKey() {
    val toml =
      """
          [libraries]
          "junit" = "junit:junit:4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testBasicStringEscapesKey() {
    // In com.android.tools.idea.gradle.dsl.parser.toml.TomlDslParserTest's
    // _testBasicStringEscapesKey
    // this is disabled because the TOML unescaper does not handle removal of initial newline
    val toml =
      """
          [libraries]
          "\u006au\u006ei\u0074" = "junit:junit:4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testEmptyBasicStringKey() {
    val toml =
      """
          [libraries]
          "" = "junit:junit:4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testEmptyLiteralStringKey() {
    val toml =
      """
          [libraries]
          '' = "junit:junit:4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitMap() {
    val toml =
      """
          [libraries]
          junit.module = "junit:junit"
          junit.version = "4.13"
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitMapWithQuotedKeys() {
    val toml =
      """
          [libraries]
          'junit'.module = "junit:junit"
          junit."version" = "4.13"
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testQuotedDottedKeys() {
    val toml =
      """
          [libraries]
          'junit.module' = "junit:junit"
          "junit.version" = "4.13"
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit.module" to "junit:junit", "junit.version" to "4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitTable() {
    val toml =
      """
          [libraries.junit]
          module = "junit:junit"
          version = "4.13"
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitTableQuoted() {
    val toml =
      """
          ['libraries'."junit"]
          module = "junit:junit"
          version = "4.13"
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testQuotedDottedTable() {
    val toml =
      """
          ["libraries.junit"]
          module = "junit:junit"
          version = "4.13"
        """
        .trimIndent()
    val expected = mapOf("libraries.junit" to mapOf("module" to "junit:junit", "version" to "4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testInlineTable() {
    val toml =
      """
          [libraries]
          junit = { module = "junit:junit", version = "4.13" }
        """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testInlineTableWithImplicitTables() {
    val toml =
      """
          [libraries]
          junit = { module = "junit:junit", version.ref = "junit" }
        """
        .trimIndent()

    val expected =
      mapOf(
        "libraries" to
          mapOf("junit" to mapOf("module" to "junit:junit", "version" to mapOf("ref" to "junit")))
      )
    doTest(toml, expected)
  }

  @Test
  fun testArray() {
    val toml =
      """
          [bundles]
          groovy = ["groovy-core", "groovy-json", "groovy-nio"]
        """
        .trimIndent()
    val expected =
      mapOf("bundles" to mapOf("groovy" to listOf("groovy-core", "groovy-json", "groovy-nio")))
    doTest(toml, expected)
  }

  @Test
  fun testArrayWithInlineTable() {
    val toml =
      """
          [bundles]
          groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
        """
        .trimIndent()
    val expected =
      mapOf(
        "bundles" to
          mapOf(
            "groovy" to
              listOf(
                "groovy-core",
                "groovy-json",
                mapOf("name" to "groovy-nio", "version" to "3.14")
              )
          )
      )
    doTest(toml, expected)
  }

  @Test
  fun testInlineTableWithArray() {
    val toml =
      """
          [libraries]
          junit = { module = ["junit", "junit"], version = "4.13" }
        """
        .trimIndent()
    val expected =
      mapOf(
        "libraries" to
          mapOf("junit" to mapOf("module" to listOf("junit", "junit"), "version" to "4.13"))
      )
    doTest(toml, expected)
  }

  @Test
  fun testInvalidResynchronization() {
    // Deliberate TOML error below; not showing as TOML since there isn't a way
    // to turn off this error in nested highlighting for TOML here
    @Language("TEXT")
    val toml =
      """
            [libraries]
            junit = { module = "junit:junit", version = "4.13" } a
            junit5 = { module = "junit:junit", version = "5.14" }
        """
        .trimIndent()
    val expected =
      mapOf(
        "libraries" to
          mapOf(
            "junit" to mapOf("module" to "junit:junit", "version" to "4.13"),
            "junit5" to mapOf("module" to "junit:junit", "version" to "5.14")
          )
      )
    doTest(toml, expected)
  }

  @Test
  fun testInvalidResynchronization2() {
    // Deliberate TOML error below; not showing as TOML since there isn't a way
    // to turn off this error in nested highlighting for TOML here
    @Language("TEXT")
    val toml =
      """
            [libraries]
            junit = { module version }
            junit5 = { module = "junit:junit", version = "5.14" }
        """
        .trimIndent()
    val expected =
      mapOf(
        "libraries" to
          mapOf(
            "junit" to mapOf(),
            "junit5" to mapOf("module" to "junit:junit", "version" to "5.14")
          )
      )
    doTest(toml, expected)
  }

  @Test
  fun testInvalidResynchronization3() {
    // Deliberate TOML error below; not showing as TOML since there isn't a way
    // to turn off this error in nested highlighting for TOML here
    @Language("TEXT")
    val toml =
      """
            [libraries]
            junit = { a = "1", [module], c = "3" }
            junit5 = { module = "junit:junit", version = "5.14" }
        """
        .trimIndent()
    val expected =
      mapOf(
        "libraries" to
          mapOf(
            "junit" to mapOf("a" to "1", "c" to "3"),
            "junit5" to mapOf("module" to "junit:junit", "version" to "5.14")
          )
      )
    doTest(toml, expected)
  }

  @Test
  fun testInvalidResynchronization4() {
    // Deliberate TOML error below; not showing as TOML since there isn't a way
    // to turn off this error in nested highlighting for TOML here
    @Language("TEXT")
    val toml =
      """
            [libraries]
            junit = { a = "1", [[module]], c = "3" }
            junit5 = { module = "junit:junit", version = "5.14" }
        """
        .trimIndent()
    val expected =
      mapOf(
        "libraries" to
          mapOf(
            "junit" to mapOf("a" to "1", "c" to "3"),
            "junit5" to mapOf("module" to "junit:junit", "version" to "5.14")
          )
      )
    doTest(toml, expected)
  }

  @Test
  fun testInvalidFuzzed1() {
    @Language("TEXT")
    val toml =
      """
      [bundles]
      g]r = [[abc]"gr-c", "gr-j", { na = "gr-n" } ]
      """
        .trimIndent()
    parseToml(File("test.toml"), toml, true)
  }

  @Test
  fun testTableElementLocation() {
    val toml =
      """
      [bundles]
      gr = 1
      """
        .trimIndent()
    val res = parseToml(File("test.toml"), toml, false)
    val location = res.document.getRoot()["bundles"]!!.getFullLocation()
    assertEquals(0, location.start!!.offset)
    assertEquals(16, location.end!!.offset)
  }

  @Test
  fun testRootElementLocation() {
    val toml =
      """
      [bundles]
      gr = 1
      """
        .trimIndent()
    val res = parseToml(File("test.toml"), toml, false)
    val location = res.document.getRoot().getFullLocation()
    assertEquals(0, location.start!!.offset)
    assertEquals(16, location.end!!.offset)
  }

  @Test
  fun testNestedArrays() {
    val toml =
      """
        [libraries]
        data = [ [ "delta", "phi" ], [ 3.14 ] ]
      """
        .trimIndent()
    val expected =
      mapOf("libraries" to mapOf("data" to listOf(listOf("delta", "phi"), listOf(3.14))))
    doTest(toml, expected)
  }

  // ------------------------------------------------
  // Test fixtures only below
  // ------------------------------------------------

  private fun checkToml(@Language("TOML") source: String, check: (ParseResult) -> Unit) {
    for (validate in listOf(false, true)) {
      val parseResult = parseToml(File("test.toml"), source, validate)
      check(parseResult)
    }
  }

  private fun parseToml(file: File, contents: String, validate: Boolean = true): ParseResult {
    val problems = mutableListOf<Triple<Severity, Location, String>>()

    val document =
      LintTomlParser().run {
        when (validate) {
          true ->
            parse(file, contents) { severity, location, message ->
              problems.add(Triple(severity, location, message))
            }
          false -> parse(file, contents)
        }
      }

    return ParseResult(document, problems, validate)
  }

  private class ParseResult(
    val document: LintTomlDocument,
    val problems: List<Triple<Severity, Location, String>>,
    val validated: Boolean
  ) {
    fun describe(includeSources: Boolean = true, includeProblems: Boolean = false): String {
      val sb = StringBuilder()
      for ((key, value) in document.getRoot().getMappedValues()) {
        describe(sb, value, key, 0, includeSources)
      }
      if (includeProblems) {
        sb.append(describeProblems(includeSources))
      }
      return sb.toString()
    }

    private fun describe(
      sb: StringBuilder,
      v: Any,
      fullKey: String,
      indent: Int,
      includeSources: Boolean
    ) {
      when (v) {
        is LintTomlArrayValue -> {
          val elements = v.getArrayElements()
          for (i in elements.indices) {
            val c = elements[i]
            describe(sb, c, "$fullKey[$i]", indent + 1, includeSources)
          }
        }
        is LintTomlLiteralValue -> {
          val value = v.getActualValue()
          val valueClass = value?.javaClass?.simpleName
          sb.append("${v.getFullKey()}=${v.getText()}, $valueClass = $value\n")
          if (includeSources) {
            val desc = describeLocation(v)
            if (desc != null) {
              for (line in desc.trimEnd().split("\n")) {
                sb.append("  ").append(line.trimEnd()).append('\n')
              }
            }
          }
        }
        is LintTomlMapValue -> {
          for ((k, v2) in v.getMappedValues()) {
            describe(
              sb,
              v2,
              if (fullKey.isEmpty()) k else "$fullKey.$k",
              indent + 1,
              includeSources
            )
          }
        }
        else -> error("Unexpected map member $v of type ${v.javaClass.name}")
      }
    }

    private fun describeProblem(
      severity: Severity,
      location: Location,
      message: String,
      includeSources: Boolean,
      source: String
    ): String {
      val start = location.start
      val path = location.file.name
      val errorLine =
        path +
          ": " +
          start?.line +
          ":" +
          start?.column +
          ": " +
          severity.description +
          ": " +
          message
      return if (includeSources) {
        errorLine + ("\n" + (location.getErrorLines { source } ?: "")).trimEnd()
      } else {
        errorLine
      }
    }

    fun describeProblems(includeSources: Boolean = true): String {
      return problems.joinToString("\n") { (severity, location, message) ->
        describeProblem(
          severity,
          location,
          message,
          includeSources,
          document.getSource().toString()
        )
      }
    }

    private fun copy(
      map: LintTomlMapValue,
      transformValue: (LintTomlValue) -> Any
    ): Map<String, Any> {
      val copy = mutableMapOf<String, Any>()
      for ((key, value) in map.getMappedValues()) {
        copy[key] = copy(value, transformValue)
      }
      return copy
    }

    private fun copy(value: Any, transformValue: (LintTomlValue) -> Any): Any {
      return when (value) {
        is LintTomlMapValue -> {
          copy(value, transformValue)
        }
        is LintTomlArrayValue -> {
          val list = mutableListOf<Any>()
          for (item in value.getArrayElements()) {
            list.add(copy(item, transformValue))
          }
          list
        }
        is LintTomlLiteralValue -> transformValue(value)
        else -> error("Unexpected map content: $value of type ${value.javaClass}")
      }
    }

    fun getSourceValueMap(): Map<String, Any> {
      return copy(document.getRoot()) { it.getText() }
    }

    fun getValueMap(): Map<String, Any> {
      return copy(document.getRoot()) {
        if (it is LintTomlLiteralValue) it.getActualValue() ?: it.getText() else it.getText()
      }
    }

    private fun describeLocation(v: LintTomlValue): String? {
      val keyLocation = v.getKeyLocation()
      val valueLocation = v.getLocation()
      val valueString =
        valueLocation.getErrorLines { v.getDocument().getSource() }?.trimIndent() ?: return null
      val keyString =
        keyLocation?.getErrorLines { v.getDocument().getSource() }?.trimIndent()
          ?: return valueString
      val keyLines = keyString.split('\n')
      val valueLines = valueString.split('\n')

      if (keyLines[0] != valueLines[0]) {
        return keyString + valueString
      }
      val sb = StringBuilder()
      sb.append(keyLines[0]).append('\n')
      val keyRange = keyLines[1]
      val valueRange = valueLines[1]
      for (i in valueRange.indices) {
        if (i < keyRange.length && !keyRange[i].isWhitespace()) {
          sb.append(keyRange[i])
        } else {
          sb.append(valueRange[i])
        }
      }
      sb.append('\n')
      return sb.toString()
    }
  }
}
