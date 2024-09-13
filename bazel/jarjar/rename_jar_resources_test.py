#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os
import unittest
import zipfile
from absl.testing import absltest
from textwrap import dedent
from tools.base.bazel.jarjar import rename_jar_resources


class TestFile:
    def __init__(self, name, content, shaded_name=None, shaded_content=None):
        self._name = name
        self._shaded_name = shaded_name
        self._content = content
        self._shaded_content = shaded_content

    def name(self):
        return self._name
    def shaded_name(self):
        return self._name if self._shaded_name is None else self._shaded_name
    def content(self):
        return self._content
    def shaded_content(self):
        return self._content if self._shaded_content is None else self._shaded_content

def get_path(name):
    return os.path.join(os.getenv("TEST_TMPDIR"), name)

def write_test_data(jar_path, rules_path, rules_content, files):
    with zipfile.ZipFile(jar_path, mode="w", compression=zipfile.ZIP_STORED) as archive:
        for i in files:
            archive.writestr(i.name(), i.content())

    with open(rules_path, "w") as f:
        f.write(rules_content)

def make_rules(rules):
    return "\n".join(rules)

class RenameJarTestBase(absltest.TestCase):
    def setUp(self):
        self.in_jar_path = get_path("test_in.jar")
        self.rules_path = get_path("test_rules.txt")
        self.out_jar_path = get_path("test_out.jar")
        self.rename_services = True
        self.native_classloader = "io.netty.util.internal.NativeLibraryLoader"

    def run_shade_resources(self, rules, test_files):
        write_test_data(
            jar_path=self.in_jar_path,
            rules_path=self.rules_path,
            rules_content="\n".join(rules),
            files=test_files)

        rename_jar_resources.shade_resources(
            jar_file=self.in_jar_path,
            rules_file=self.rules_path,
            output=self.out_jar_path,
            rename_services=self.rename_services,
            native_classloader=self.native_classloader)

    def assert_files_exactly(self, files):
        with zipfile.ZipFile(self.out_jar_path, mode="r", compression=zipfile.ZIP_STORED) as archive:
            self.assertEqual(
                sorted(archive.namelist()),
                sorted([i.shaded_name() for i in files]))

    def assert_file_content_matches(self, files):
        with zipfile.ZipFile(self.out_jar_path, mode="r", compression=zipfile.ZIP_STORED) as archive:
            for test_file in files:
                self.assertEqual(
                    archive.read(test_file.shaded_name()).decode(encoding="utf-8").strip().split("\n"),
                    test_file.shaded_content().strip().split("\n"))

class RenameJarServicesTest(RenameJarTestBase):
    def setUp(self):
        super().setUp()

        self.files = {
            "service_file_full_rename": TestFile(
                name="META-INF/services/com.example.ServiceProvider",
                shaded_name="META-INF/services/com.android.example.ServiceProvider",
                content=dedent("""
                    com.example.SomeClassA
                    com.example.SomeClassB
                    com.something.else.SomeClassC
                """),
                shaded_content=dedent("""
                    com.android.example.SomeClassA
                    com.android.example.SomeClassB
                    com.something.else.SomeClassC
                """)
            ),
            "service_file_rename_class_only": TestFile(
                name="META-INF/services/com.something.ServiceProvider",
                content=dedent("""
                    com.example.SomeClassD
                """),
                shaded_content=dedent("""
                    com.android.example.SomeClassD
                """)
            ),
            "service_file_double_rename": TestFile(
                name="META-INF/services/com.something.else.foo.bar.ServiceProvider",
                shaded_name="META-INF/services/com.android.something.else.bar.foo.ServiceProvider",
                content=dedent("""
                    com.something.else.foo.bar.SomeClassE
                    com.something.else.foo.SomeClassF
                """),
                shaded_content=dedent("""
                    com.android.something.else.bar.foo.SomeClassE
                    com.something.else.foo.SomeClassF
                """)
            ),
            "service_file_keep_rule": TestFile(
                name="META-INF/services/com.kept.Service",
                content=dedent("""
                    com.kept.package.SomeClassG
                """),
                shaded_content=dedent("""
                    com.kept.package.SomeClassG
                """)
            ),
            "bait_class_file": TestFile(
                name="com/example/BaitClass.class",
                content=dedent("""
                    com.example.BaitClass
                """)
            ),
            "native_file": TestFile(
                name="META-INF/native/file.so",
                content="",
            ),
        }
        self.single_rename_rule = "rule com.example.** com.android.example.@1"
        self.double_rename_rule = "rule com.**.foo.bar.** com.android.@1.bar.foo.@2"
        self.keep_rule = "keep com.kept.package.**"
        self.zap_rule = "zap com.zap.package.**"

    def test_file_and_service_are_renamed(self):
        test_files = [self.files["service_file_full_rename"]]

        self.run_shade_resources(
            rules=[self.single_rename_rule],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_only_class_is_renamed(self):
        test_files = [self.files["service_file_rename_class_only"]]

        self.run_shade_resources(
            rules=[self.single_rename_rule],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_double_rename_rule(self):
        test_files = [self.files["service_file_double_rename"]]

        self.run_shade_resources(
            rules=[self.double_rename_rule],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_bait_file_not_touched(self):
        test_files = [self.files["bait_class_file"]]

        self.run_shade_resources(
            rules=[self.double_rename_rule],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_keep_rule_does_nothing(self):
        test_files = [self.files["service_file_keep_rule"]]

        self.run_shade_resources(
            rules=[self.keep_rule],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_zap_rule_fails(self):
        with self.assertRaisesRegex(ValueError, "zap operation not supported"):
            self.run_shade_resources(
                rules=[self.zap_rule],
                test_files=[])

    def test_full_behavior(self):
        test_files = [self.files[i] for i in self.files]
        self.run_shade_resources(
            rules=[
                self.single_rename_rule,
                self.double_rename_rule,
                self.keep_rule
            ],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

    def test_service_rename_disabled(self):
        self.rename_services = False
        test_files = [
            TestFile(
                name="META-INF/services/com.example.ServiceProvider",
                content=dedent("""
                    com.example.SomeClassA
                    com.example.SomeClassB
                    com.something.else.SomeClassC
                """)
            )
        ]
        self.run_shade_resources(
            rules=[
                self.single_rename_rule,
            ],
            test_files=test_files)

        self.assert_files_exactly(test_files)
        self.assert_file_content_matches(test_files)

class RenameJarNativeTest(RenameJarTestBase):
    def setUp(self):
        super().setUp()

        self.files = {
            "standard_rename": TestFile(
                name="META-INF/native/file.so",
                shaded_name="META-INF/native/a_b_c_file.so",
                content="",
            ),
            "lib_rename": TestFile(
                name="META-INF/native/libfile.jnilib",
                shaded_name="META-INF/native/liba_b_c_file.jnilib",
                content="",
            ),
            "no_rename": TestFile(
                name="META-INF/native/unchanged",
                content="",
            ),
            "service_rename": TestFile(
                name="META-INF/services/io.netty.my.package.Service",
                shaded_name="META-INF/services/a.b.c.io.netty.my.package.Service",
                content="",
            ),
        }
        self.valid_shade = "rule io.netty.** a.b.c.io.netty.@1"             # valid rename by applying prefix
        self.non_prefix_shade = "rule io.netty.** a.b.c.not.netty.@1"       # invalid rename via non-prefix
        self.non_match_shade = "rule original.package.** something.else.@1" # does not rename due to non-match

    def test_native_renamed(self):
        test_files = [
            self.files["standard_rename"],
            self.files["lib_rename"],
        ]

        self.run_shade_resources(
            rules=[self.valid_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

    def test_native_not_renamed_non_prefix(self):
        test_files = [self.files["no_rename"]]

        self.run_shade_resources(
            rules=[self.non_prefix_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

    def test_native_not_renamed_non_match(self):
        test_files = [self.files["no_rename"]]

        self.run_shade_resources(
            rules=[self.non_match_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

    def test_native_not_renamed_empty_classloader(self):
        self.native_classloader = ""
        test_files = [self.files["no_rename"]]

        self.run_shade_resources(
            rules=[self.valid_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

    def test_native_not_renamed_null_classloader(self):
        self.native_classloader = None
        test_files = [self.files["no_rename"]]

        self.run_shade_resources(
            rules=[self.valid_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

    def test_native_and_service_renamed(self):
        test_files = [
            self.files["standard_rename"],
            self.files["lib_rename"],
            self.files["service_rename"],
        ]

        self.run_shade_resources(
            rules=[self.valid_shade],
            test_files=test_files)

        self.assert_files_exactly(test_files)

if __name__ == "__main__":
    absltest.main()
