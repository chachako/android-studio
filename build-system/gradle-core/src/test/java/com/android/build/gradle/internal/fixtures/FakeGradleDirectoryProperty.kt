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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File
import java.util.function.BiFunction

class FakeGradleDirectoryProperty(private val directory: Directory?) : DirectoryProperty {

    override fun get(): Directory = directory!!

    override fun getOrNull(): Directory? = directory

    override fun getOrElse(defaultValue: Directory): Directory = directory ?: defaultValue

    override fun <S : Any?> map(transformer: Transformer<out S?, in Directory>): Provider<S> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> flatMap(transformer: Transformer<out Provider<out S>?, in Directory>): Provider<S> {
        TODO("Not yet implemented")
    }

    override fun isPresent(): Boolean {
        TODO("Not yet implemented")
    }

    override fun orElse(value: Directory): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun orElse(provider: Provider<out Directory>): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun forUseAtConfigurationTime(): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun <U : Any?, R : Any?> zip(right: Provider<U>, combiner: BiFunction<in Directory, in U, out R?>): Provider<R> {
        TODO("Not yet implemented")
    }

    override fun finalizeValue() {
        TODO("Not yet implemented")
    }

    override fun finalizeValueOnRead() {
        TODO("Not yet implemented")
    }

    override fun disallowChanges() {
        TODO("Not yet implemented")
    }

    override fun disallowUnsafeRead() {
        TODO("Not yet implemented")
    }

    override fun set(file: File?) {
        TODO("Not yet implemented")
    }

    override fun set(value: Directory?) {
        TODO("Not yet implemented")
    }

    override fun set(provider: Provider<out Directory>) {
        TODO("Not yet implemented")
    }

    override fun value(value: Directory?): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun value(provider: Provider<out Directory>): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun convention(value: Directory?): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun convention(provider: Provider<out Directory>): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun getAsFile(): Provider<File> {
        TODO("Not yet implemented")
    }

    override fun fileValue(file: File?): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun fileProvider(provider: Provider<File>): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun getLocationOnly(): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun getAsFileTree(): FileTree {
        TODO("Not yet implemented")
    }

    override fun dir(path: String): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun dir(path: Provider<out CharSequence>): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun file(path: String): Provider<RegularFile> {
        TODO("Not yet implemented")
    }

    override fun file(path: Provider<out CharSequence>): Provider<RegularFile> {
        TODO("Not yet implemented")
    }

    override fun files(vararg paths: Any?): FileCollection {
        TODO("Not yet implemented")
    }
}
