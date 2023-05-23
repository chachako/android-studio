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
package com.android.ide.common.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DependencyTest {
    @Test
    fun testParseEmpty() {
        Dependency.parse("").let {
            assertThat(it.toIdentifier()).isEqualTo("")
            assertThat(it.toString()).isEqualTo("")
            assertThat(it.name).isEqualTo("")
            assertThat(it.group).isNull()
            assertThat(it.version).isNull()
            assertThat(it.classifier).isNull()
            assertThat(it.extension).isNull()
        }
    }

    @Test
    fun testParseName() {
        for (n in listOf("", "name", "1.2.3", "[1.2,3.4)")) {
            Dependency.parse("$n").let {
                assertThat(it.toIdentifier()).isEqualTo("$n")
                assertThat(it.toString()).isEqualTo("$n")
                assertThat(it.group).isNull()
                assertThat(it.name).isEqualTo(n)
                assertThat(it.version).isNull()
                assertThat(it.classifier).isNull()
                assertThat(it.extension).isNull()
            }
        }
    }

    @Test
    fun testParseNameExtension() {
        for (n in listOf("", "name", "n@me", "1.2.3", "[1.2,3.4)")) {
            for (e in listOf("", "aar", ":aar", "aar:")) {
                Dependency.parse("$n@$e").let {
                    assertThat(it.toIdentifier()).isEqualTo("$n@$e")
                    assertThat(it.toString()).isEqualTo("$n@$e")
                    assertThat(it.name).isEqualTo(n)
                    assertThat(it.group).isNull()
                    assertThat(it.version).isNull()
                    assertThat(it.classifier).isNull()
                    assertThat(it.extension).isEqualTo(e)
                }
            }
        }
    }

    @Test
    fun testParseGroupName() {
        for (g in listOf("", "group", "1.2.3", "[1.2,3.4)")) {
            Dependency.parse("$g:valid-name").let {
                assertThat(it.toIdentifier()).isEqualTo("$g:valid-name")
                assertThat(it.toString()).isEqualTo("$g:valid-name")
                assertThat(it.name).isEqualTo("valid-name")
                assertThat(it.group).isEqualTo(g)
                assertThat(it.version).isNull()
                assertThat(it.classifier).isNull()
                assertThat(it.extension).isNull()
            }
        }
    }

    @Test
    fun testParseGroupNameExtension() {
        for (g in listOf("", "gr@up", "1.2.3", "[1.2,3.4)")) {
            for (e in listOf("", "aar", ":aar", "aar:")) {
                Dependency.parse("$g:valid-name@$e").let {
                    assertThat(it.toIdentifier()).isEqualTo("$g:valid-name@$e")
                    assertThat(it.toString()).isEqualTo("$g:valid-name@$e")
                    assertThat(it.name).isEqualTo("valid-name")
                    assertThat(it.group).isEqualTo(g)
                    assertThat(it.version).isNull()
                    assertThat(it.classifier).isNull()
                    assertThat(it.extension).isEqualTo(e)
                }
            }
        }
    }

    @Test
    fun testParseGroupNameVersion() {
        for (v in listOf("", "version", "1.2.3", "1.+", "[1,3)", "+", "[1,3)!!2", "+!!4")) {
            Dependency.parse("valid-group:valid-name:$v").let {
                assertThat(it.toIdentifier()).isEqualTo("valid-group:valid-name:$v")
                assertThat(it.toString()).isEqualTo("valid-group:valid-name:$v")
                assertThat(it.name).isEqualTo("valid-name")
                assertThat(it.group).isEqualTo("valid-group")
                assertThat(it.version).isEqualTo(RichVersion.parse(v))
                assertThat(it.classifier).isNull()
                assertThat(it.extension).isNull()
            }
        }
    }

    @Test
    fun testParseGroupNameVersionExtension() {
        for (v in listOf("", "version", "v@rsion", "1.2.3", "1.+", "[1,3)", "+", "[1,3)!!2", "+!!4")) {
            for (e in listOf("", "aar", ":aar", "aar:")) {
                Dependency.parse("valid-group:valid-name:$v@$e").let {
                    assertThat(it.toIdentifier()).isEqualTo("valid-group:valid-name:$v@$e")
                    assertThat(it.toString()).isEqualTo("valid-group:valid-name:$v@$e")
                    assertThat(it.name).isEqualTo("valid-name")
                    assertThat(it.group).isEqualTo("valid-group")
                    assertThat(it.version).isEqualTo(RichVersion.parse(v))
                    assertThat(it.classifier).isNull()
                    assertThat(it.extension).isEqualTo(e)
                }
            }
        }
    }

    @Test
    fun testParseGroupNameVersionClassifier() {
        for (c in listOf("", "jre8", ":jre8", "jre8:", "1.2.3", "[1.2,3.4)")) {
            Dependency.parse("valid-group:valid-name:valid-version:$c").let {
                assertThat(it.toIdentifier()).isEqualTo("valid-group:valid-name:valid-version:$c")
                assertThat(it.toString()).isEqualTo("valid-group:valid-name:valid-version:$c")
                assertThat(it.name).isEqualTo("valid-name")
                assertThat(it.group).isEqualTo("valid-group")
                assertThat(it.version).isEqualTo(RichVersion.parse("valid-version"))
                assertThat(it.classifier).isEqualTo(c)
                assertThat(it.extension).isNull()
            }
        }
    }

    @Test
    fun testParseGroupNameVersionClassifierExtension() {
        for (c in listOf("", "jre8", "jr@8", ":jre8", "jre8:", "1.2.3", "[1.2,3.4)")) {
            for (e in listOf("", "aar", ":aar", "aar:")) {
                Dependency.parse("valid-group:valid-name:valid-version:$c@$e").let {
                    assertThat(it.toIdentifier()).isEqualTo("valid-group:valid-name:valid-version:$c@$e")
                    assertThat(it.toString()).isEqualTo("valid-group:valid-name:valid-version:$c@$e")
                    assertThat(it.name).isEqualTo("valid-name")
                    assertThat(it.group).isEqualTo("valid-group")
                    assertThat(it.version).isEqualTo(RichVersion.parse("valid-version"))
                    assertThat(it.classifier).isEqualTo(c)
                    assertThat(it.extension).isEqualTo(e)
                }
            }
        }
    }

    @Test
    fun testExplicitlyIncludesPreview() {
        // no explicit preview mention in declaration
        for (v in listOf("+", "[1,2)", "1.2.+", "1.2.3")) {
            for (s in listOf("", "!!", "!!1.2.3")) {
                val dependency = Dependency.parse("com.example:example:$v$s")
                assertThat(dependency.explicitlyIncludesPreview).isFalse()
            }
            // explicit preview mention in prefer
            Dependency.parse("com.example:example:$v!!1.2.3-rc").let { dependency ->
                assertThat(dependency.explicitlyIncludesPreview).isTrue()
            }
            // previews in exclude but not declaration or prefer
            Dependency(
                group = "com.example",
                name = "example",
                version = RichVersion(
                    declaration = RichVersion.Declaration(
                        RichVersion.Kind.REQUIRE,
                        VersionRange.parse("1.2.3")
                    ), exclude = listOf(VersionRange.parse("1.2.4-alpha01"))
                )
            ).let { dependency ->
                assertThat(dependency.explicitlyIncludesPreview).isFalse()
            }

        }
        // explicit preview in declaration
        for (v in listOf("[1.2-alpha01,2)", "[1.2,1.2.4-alpha01)", "1.2-alpha.+", "1.2-alpha01")) {
            for (s in listOf("", "!!", "!!1.2.3")) {
                val dependency = Dependency.parse("com.example:example:$v$s")
                assertThat(dependency.explicitlyIncludesPreview).isTrue()
            }
        }
        // missing version
        Dependency.parse("com.example:example").let { dependency ->
            assertThat(dependency.explicitlyIncludesPreview).isFalse()
        }
    }

    @Test
    fun testHasExplicitDistinctUpperBound() {
        // explicit distinct upper bound
        for (v in listOf("[1,2]", "[1,2)", "(1,2]", "(1,2)", "[,2]", "[,2)", "1.+")) {
            for (s in listOf("", "!!", "!!1.2.3")) {
                val dependency = Dependency.parse("com.example:example:$v")
                assertThat(dependency.hasExplicitDistinctUpperBound).isTrue()
            }
        }
        // no upper bound or singleton version
        for (v in listOf("1.2.3", "+", "[1,]", "[1,)")) {
            for (s in listOf("", "!!", "!!1.2.3")) {
                val dependency = Dependency.parse("com.example:example:$v")
                assertThat(dependency.hasExplicitDistinctUpperBound).isFalse()
            }
        }
        // upper bound provided through exclude (not explicit)
        Dependency(
            group = "com.example",
            name = "example",
            version = RichVersion(
                RichVersion.Declaration(
                    RichVersion.Kind.REQUIRE,
                    VersionRange.parse("[1,]")
                ), exclude = listOf(VersionRange.parse("[2,]"))
            )
        ).let { dependency ->
            // semantically this does have an upper exclusive bound of 2, but not explicitly so.
            assertThat(dependency.hasExplicitDistinctUpperBound).isFalse()
        }
        // missing version
        Dependency.parse("com.example:example").let { dependency ->
            assertThat(dependency.hasExplicitDistinctUpperBound).isFalse()
        }
    }

    @Test
    fun testExplicitSingletonVersion() {
        for (v in listOf("+", "1.+", "[1,2]")) {
            for (s in listOf("", "!!", "!!1.0")) {
                val dependency = Dependency.parse("com.example:example:$v$s")
                assertThat(dependency.explicitSingletonVersion).isNull()
            }
        }
        for (v in listOf("1.0", "[1.0,1.0]")) {
            for (s in listOf("", "!!", "!!1.0")) {
                val dependency = Dependency.parse("com.example:example:$v$s")
                assertThat(dependency.explicitSingletonVersion).isEqualTo(Version.parse("1.0"))
            }
        }
    }

    @Test
    fun testModule() {
        for (name in listOf("", "example", "example-foo")) {
            for (group in listOf("", "com", "com.example")) {
                val m = Module(group, name)
                assertThat(Dependency.parse("$group:$name").module).isEqualTo(m)
                assertThat(Dependency.parse("$group:$name@aar").module).isEqualTo(m)
                for (version in listOf("1", "+", "[2,3]")) {
                    assertThat(Dependency.parse("$group:$name:$version").module).isEqualTo(m)
                    assertThat(Dependency.parse("$group:$name:$version@aar").module).isEqualTo(m)
                }
            }
            for (version in listOf("1", "+", "[2,3]")) {
                assertThat(Dependency(name = name, version = RichVersion.parse(version)).module)
                    .isNull()
            }
        }
    }

    @Test
    fun testInvalidName() {
        for (n in listOf(":", "@", "ab:cd", "ab@cd")) {
            Dependency(name = n).let {
                assertThat(it.toIdentifier()).isNull()
                assertThat(it.toString()).matches("^Dependency\\(.*\\)")
            }
        }
    }

    @Test
    fun testInvalidGroup() {
        for (g in listOf(":", "@", "ab:cd", "ab@cd")) {
            Dependency(group = g, name = "valid-name").let {
                assertThat(it.toIdentifier()).isNull()
                assertThat(it.toString()).matches("^Dependency\\(.*\\)")
            }
        }
        Dependency(name = "valid-name", version = RichVersion.parse("1.2.3")).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        Dependency(name = "valid-name", version = RichVersion.parse("1.2.3"), classifier = "jre8")
            .let {
                assertThat(it.toIdentifier()).isNull()
                assertThat(it.toString()).matches("^Dependency\\(.*\\)")
            }
        Dependency(
            name = "valid-name",
            version = RichVersion.parse("1.2.3"),
            classifier = "jre8",
            extension = "aar"
        ).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
    }

    @Test
    fun testInvalidVersion() {
        // RichVersion with Dependency metacharacter
        Dependency(group = "valid-group", name = "valid-name", version = RichVersion.parse("1:2"))
            .let {
                assertThat(it.toIdentifier()).isNull()
                assertThat(it.toString()).matches("^Dependency\\(.*\\)")
            }
        Dependency(group = "valid-group", name = "valid-name", version = RichVersion.parse("1@2"))
            .let {
                assertThat(it.toIdentifier()).isNull()
                assertThat(it.toString()).matches("^Dependency\\(.*\\)")
            }
        // RichVersion with no identifier
        val version = RichVersion(declaration = RichVersion.Declaration(RichVersion.Kind.REQUIRE, VersionRange.parse("+")), prefer = Version.parse("1.2.3"))
        assertThat(version.toIdentifier()).isNull()
        Dependency(group = "valid-group", name = "valid-name", version = version).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        // null version but classifier (and group) present
        Dependency(group = "valid-group", name = "valid-name", classifier = "jre8").let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        Dependency(
            group = "valid-group",
            name = "valid-name",
            classifier = "jre8",
            extension = "aar"
        ).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
    }

    @Test
    fun testInvalidClassifier() {
        Dependency(
            group = "valid-group",
            name = "valid-name",
            version = RichVersion.parse("valid-version"),
            classifier = "j@e8"
        ).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
    }

    @Test
    fun testInvalidExtension() {
        Dependency(name = "valid-name", extension = "a@r").let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        Dependency(group = "valid-group", name = "valid-name", extension = "a@r").let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        Dependency(
            group = "valid-group",
            name = "valid-name",
            version = RichVersion.parse("valid-version"),
            extension = "a@r"
        ).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
        Dependency(
            group = "valid-group",
            name = "valid-name",
            version = RichVersion.parse("valid-version"),
            classifier = "jre8",
            extension = "a@r"
        ).let {
            assertThat(it.toIdentifier()).isNull()
            assertThat(it.toString()).matches("^Dependency\\(.*\\)")
        }
    }
}
