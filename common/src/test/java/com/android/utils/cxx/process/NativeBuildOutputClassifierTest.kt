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

package com.android.utils.cxx.process

import com.android.SdkConstants
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Test

class NativeBuildOutputClassifierTest {
    @Test
    fun `check basic compiler error`() {
        assertClassification(
            """
              ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4c2h4t3a/arm64-v8a'
              [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
              FAILED: CMakeFiles/app.dir/app.cpp.o
              /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5:3: error: no matching function for call to 'bar'
                bar(b);
                ^~~
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
              extern void bar(const int*);
                          ^
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:8:1: error: unknown type name 'snake'
              snake
              ^
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:8:6: error: expected unqualified-id
              snake
                   ^
              3 errors generated.
              ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4c2h4t3a/arm64-v8a'
            ----------------
            [CLANG_COMMAND_LINE]
            /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
            ----------------
            [CLANG_COMPILER_ERROR]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5:3: error: no matching function for call to 'bar'
              bar(b);
              ^~~
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
            extern void bar(const int*);
                        ^
            ----------------
            [CLANG_COMPILER_ERROR]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:8:1: error: unknown type name 'snake'
            snake
            ^
            ----------------
            [CLANG_COMPILER_ERROR]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:8:6: error: expected unqualified-id
            snake
                 ^
            ----------------
            [CLANG_ERRORS_GENERATED]
            3 errors generated.
            """.trimIndent()
        )
    }

    @Test
    fun `check basic linker error`() {
        assertClassification(
            """
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
            [2/2] Linking CXX shared library /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so
            FAILED: /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so
            : && /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ld: error: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:5 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            clang++: error: linker command failed with exit code 1 (use -v to see invocation)
            ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            ----------------
            [CLANG_COMMAND_LINE]
            : && /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ----------------
            [CLANG_LINKER_ERROR]
            ld: error: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:5 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            ----------------
            [CLANG_LINKER_ERROR]
            clang++: error: linker command failed with exit code 1 (use -v to see invocation)
            ----------------
            [NINJA_BUILD_STOPPED]
            ninja: build stopped: subcommand failed.
            """.trimIndent()
        )
    }

    @Test
    fun `check basic linker warning`() {
        assertClassification(
            """
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
            [2/2] Linking CXX shared library /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so
            /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ld: warning: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:5 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            ----------------
            [CLANG_COMMAND_LINE]
            /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ----------------
            [CLANG_LINKER_WARNING]
            ld: warning: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:5 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:5)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            ----------------
            [NINJA_BUILD_STOPPED]
            ninja: build stopped: subcommand failed.
            """.trimIndent()
        )
    }

    @Test
    fun `check mixed warning and compiler error`() {
        assertClassification(
            """
                ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a'
                [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
                FAILED: CMakeFiles/app.dir/app.cpp.o
                /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
                /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
                #warning "Simulated warning"
                 ^
                /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
                  bar(b);
                  ^~~
                /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
                extern void bar(const int*);
                            ^
                1 warning and 1 error generated.
                ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a'
            ----------------
            [CLANG_COMMAND_LINE]
            /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
            ----------------
            [CLANG_COMPILER_WARNING]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
            #warning "Simulated warning"
             ^
            ----------------
            [CLANG_COMPILER_ERROR]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
              bar(b);
              ^~~
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
            extern void bar(const int*);
                        ^
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 warning and 1 error generated.
            """.trimIndent()
        )
    }

    @Test
    fun `check mixed warning and linker error`() {
        assertClassification(
            """
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
            #warning "Simulated warning"
             ^
            1 warning generated.
            [2/2] Linking CXX shared library /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so
            FAILED: /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so
            : && /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ld: error: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:6 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            clang++: error: linker command failed with exit code 1 (use -v to see invocation)
            ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4ll474mv/arm64-v8a'
            ----------------
            [CLANG_COMPILER_WARNING]
            /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
            #warning "Simulated warning"
             ^
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 warning generated.
            ----------------
            [CLANG_COMMAND_LINE]
            : && /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -fPIC -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments -shared -Wl,-soname,libapp.so -o /Users/jomof/projects/repro/as-bad-error-context-repro/app/build/intermediates/cxx/Debug/4ll474mv/obj/arm64-v8a/libapp.so CMakeFiles/app.dir/app.cpp.o  -latomic -lm && :
            ----------------
            [CLANG_LINKER_ERROR]
            ld: error: undefined symbol: bar(int const*)
            >>> referenced by app.cpp:6 (/Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6)
            >>>               CMakeFiles/app.dir/app.cpp.o:(foo())
            ----------------
            [CLANG_LINKER_ERROR]
            clang++: error: linker command failed with exit code 1 (use -v to see invocation)
            ----------------
            [NINJA_BUILD_STOPPED]
            ninja: build stopped: subcommand failed.
            """.trimIndent()
        )
    }

    @Test
    fun `standalone note should be emitted`() {
        assertClassification(
            """
            this line is not a compiler output
            ninja: Entering directory `/path/to/dir'
            /path/to/abc.c(1,1): note: unused variable
            /path/to/abc.c(2,1): error: something is wrong
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/path/to/dir'
            ----------------
            [CLANG_LINKER_INFORMATIONAL]
            /path/to/abc.c(1,1): note: unused variable
            ----------------
            [CLANG_LINKER_ERROR]
            /path/to/abc.c(2,1): error: something is wrong
            """.trimIndent()
        )
    }

    @Test
    fun `error in file included from`() {
        assertClassification(
            """
              ninja: Entering directory `/Users/jomof/AndroidStudioProjects/MyApplication54/app/.cxx/Debug/3t1uj76e/x86_64'
              [1/2] Building CXX object CMakeFiles/myapplication.dir/native-lib.cpp.o
              FAILED: CMakeFiles/myapplication.dir/native-lib.cpp.o
              /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android24 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dmyapplication_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/myapplication.dir/native-lib.cpp.o -MF CMakeFiles/myapplication.dir/native-lib.cpp.o.d -o CMakeFiles/myapplication.dir/native-lib.cpp.o -c /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp
              In file included from /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp:3:
              In file included from /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/include1.h:7:
              /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/include2.h:7:1: error: unknown type name 'snake'
              snake
              ^
              /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp:5:8: error: expected unqualified-id
              extern "C" JNIEXPORT jstring JNICALL
                     ^
              2 errors generated.
              ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/Users/jomof/AndroidStudioProjects/MyApplication54/app/.cxx/Debug/3t1uj76e/x86_64'
            ----------------
            [CLANG_COMMAND_LINE]
            /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android24 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dmyapplication_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/myapplication.dir/native-lib.cpp.o -MF CMakeFiles/myapplication.dir/native-lib.cpp.o.d -o CMakeFiles/myapplication.dir/native-lib.cpp.o -c /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp:3:
            In file included from /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/include1.h:7:
            /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/include2.h:7:1: error: unknown type name 'snake'
            snake
            ^
            ----------------
            [CLANG_COMPILER_ERROR]
            /Users/jomof/AndroidStudioProjects/MyApplication54/app/src/main/cpp/native-lib.cpp:5:8: error: expected unqualified-id
            extern "C" JNIEXPORT jstring JNICALL
                   ^
            ----------------
            [CLANG_ERRORS_GENERATED]
            2 errors generated.
            """.trimIndent()
        )
    }

    /**
     * Some configurations of C/C++ toolchain return relative paths to the CMake build directory.
     * These aren't very useful or readable so convert them to full paths.
     */
    @Test
    fun `ninja - should make relative paths absolute`() {
        assertClassification(
            """
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            ----------------
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            """.trimIndent()
        )
    }

    @Test
    fun `ninja - multiple errors should be consumed`() {
        assertClassification(
            """
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            > Task :app:externalNativeBuildRelease
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            [1/1] Building CXX object blah.cpp.o
            FAILED: blah.cpp.o
            In file included from ../../../../../native/source.cpp:8:
            ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            1 error generated.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            ----------------
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            ----------------
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            ----------------
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            #include "unresolved.h"
                     ^~~~~~~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 error generated.
            """.trimIndent()
        )
    }

    @Test
    fun `tolerate C slash C++ prefix`() {
        assertClassification(
            """
            C/C++: ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            C/C++: In file included from ../../../../../native/source.cpp:8:
            C/C++: ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_COMPILER_ERROR]
            In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
            /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
            """.trimIndent()
        )
    }

    @Test
    fun `ndk-build - simple`() {
        assertClassification(
            """
            Build app_armeabi-v7a
            [armeabi-v7a] Compile++ arm  : app <= app.cpp
            /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
            """.trimIndent(),
            """
            [NDK_BUILD_BEGIN_ABI]
            [armeabi-v7a] Compile++ arm  : app <= app.cpp
            ----------------
            [CLANG_COMPILER_WARNING]
            /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
            """.trimIndent()
        )
    }

    @Test
    fun `windows - repro bug 124104842`() {
        Assume.assumeTrue(SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS)
        assertClassification(
            """
            ninja: Entering directory `C:\usr\local\google\home\jeff\hello-world\app\.cxx\cmake\debug\arm64-v8a'
            ld: fatal error: ..\..\..\..\build\intermediates\cmake\debug\obj\armeabi-v7a\libcore.so: open: Invalid argument
            clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
            ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `C:/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_FATAL_LINKER_ERROR_BUG_124104842]
            ld: fatal error: C:/usr/local/google/home/jeff/hello-world/app/build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so: open: Invalid argument

            File C:/usr/local/google/home/jeff/hello-world/app/build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so could not be written. This may be caused by insufficient permissions or files being locked by other processes. For example, LLDB may lock .so files while debugging.
            ----------------
            [CLANG_LINKER_ERROR]
            clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
            ----------------
            [NINJA_BUILD_STOPPED]
            ninja: build stopped: subcommand failed.
            """.trimIndent()
        )
    }

    @Test
    fun `repro bug 124104842`() {
        assertClassification(
            """
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ld: fatal error: ../../../../build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so: open: Invalid argument
            clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
            ninja: build stopped: subcommand failed.
            """.trimIndent(),
            """
            [NINJA_ENTERING_DIRECTORY]
            ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
            ----------------
            [CLANG_FATAL_LINKER_ERROR_BUG_124104842]
            ld: fatal error: /usr/local/google/home/jeff/hello-world/app/build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so: open: Invalid argument

            File /usr/local/google/home/jeff/hello-world/app/build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so could not be written. This may be caused by insufficient permissions or files being locked by other processes. For example, LLDB may lock .so files while debugging.
            ----------------
            [CLANG_LINKER_ERROR]
            clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
            ----------------
            [NINJA_BUILD_STOPPED]
            ninja: build stopped: subcommand failed.
            """.trimIndent()
        )
    }

    @Test
    fun `ndk-build - interleaved`() {
        assertClassification(
            """
            Build app_armeabi-v7a
            [armeabi-v7a] Compile++ arm  : app <= app.cpp
            /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
                    some randome code
                    ^~~~~~~~~
            [x86] Compile++ arm  : app2 <= app2.cpp
            /usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong
                    some randome code 2
                    ^~~~~~~~~
            1 warning generated.
            1 warning generated.
            """.trimIndent(),
            """
            [NDK_BUILD_BEGIN_ABI]
            [armeabi-v7a] Compile++ arm  : app <= app.cpp
            ----------------
            [CLANG_COMPILER_WARNING]
            /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
                    some randome code
                    ^~~~~~~~~
            ----------------
            [NDK_BUILD_BEGIN_ABI]
            [x86] Compile++ arm  : app2 <= app2.cpp
            ----------------
            [CLANG_COMPILER_ERROR]
            /usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong
                    some randome code 2
                    ^~~~~~~~~
            ----------------
            [CLANG_ERRORS_GENERATED]
            1 warning generated.
            """.trimIndent()
        )
    }

    @Test
    fun `fuzz case 1`() {
        assertClassification(
            """
            /f.c:1:2: note: b
            """.trimIndent(),
            """
            [CLANG_COMPILER_INFORMATIONAL]
            /f.c:1:2: note: b
            """.trimIndent()
        )
    }

    @Test
    fun `fuzz case 2`() {
        assertClassification(
            """
            ld: fatal error: f.so: open: Invalid argument
            In file included from file.h:1:2:
            """.trimIndent(),
            """
            [CLANG_FATAL_LINKER_ERROR_BUG_124104842]
            ld: fatal error: f.so: open: Invalid argument

            File f.so could not be written. This may be caused by insufficient permissions or files being locked by other processes. For example, LLDB may lock .so files while debugging.
            """.trimIndent()
        )
    }

    private fun assertClassification(input : String, expected : String?) {
        val lines = input.split("\n")
        var result = ""
        NativeBuildOutputClassifier { message ->
            if (result.isNotEmpty()) {
                result += "\n----------------\n"
            }
            result += "[${message.classification}]\n"
            result += message.lines.joinToString("\n")
        }.use { classify ->
            lines.forEach(classify::consume)
        }
        result = result.trim('\n').replace("\\", "/")
        println(result)
        println("\n==============")
        Truth.assertThat(result).isEqualTo(expected)
    }
}
