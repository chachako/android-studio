/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ide.common.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.test.assertContentEquals

class NetworkCacheTest {
    @Test
    fun testNetworkDisabled() {
        val cache = object : TestCache(networkEnabled = false) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { fail("No network calls expected") }
        }

        cache.loadArtifact()
    }

    @Test
    fun testNetworkEnabled() {
        var networkCalls = 0
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { networkCalls++ }
        }
        networkEnabledCache.loadArtifact()
        assertEquals(1, networkCalls)
    }

    @Test
    fun testCacheHit() {
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            val artifactPath = cacheDir!!.resolve("artifact.xml")

            init {
                Files.createFile(artifactPath)
                Files.write(artifactPath, ByteArray(5) { i -> i.toByte() })
            }

            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { fail("No network calls expected") }

        }
        val data = networkEnabledCache.loadArtifact()!!
        assertContentEquals(data.readBytes(), ByteArray(5) { i -> i.toByte() })
    }

    @Test
    fun testCacheMissNotModifiedSince() {
        var lastModifiedRequest = -1L
        val networkEnabledCacheWithHit = object : TestCache(networkEnabled = true) {
            val artifactPath = cacheDir!!.resolve("artifact.xml")

            init {
                Files.createFile(artifactPath)
                Files.write(artifactPath, ByteArray(5) { i -> i.toByte() })
                Files.setLastModifiedTime(artifactPath, FileTime.fromMillis(42))
            }

            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, false)
                    .also { lastModifiedRequest = lastModified }
        }

        val data = networkEnabledCacheWithHit.loadArtifact()!!
        assertEquals(42, lastModifiedRequest)
        val lastModifiedFile = Files.getLastModifiedTime(networkEnabledCacheWithHit.artifactPath)
        assertTrue(lastModifiedFile.toMillis() > 42)
        assertContentEquals(
            data.readBytes().also { data.close() },
            ByteArray(5) { i -> i.toByte() })
    }

    @Test
    fun testCacheMiss() {
        var lastModifiedRequest = -1L
        val networkEnabledCacheWithHit = object : TestCache(networkEnabled = true) {
            val artifactPath = cacheDir!!.resolve("artifact.xml")
            init {
                Files.createFile(artifactPath)
                Files.write(artifactPath, ByteArray(5) { i -> i.toByte() })
                Files.setLastModifiedTime(artifactPath, FileTime.fromMillis(42))
            }

            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(ByteArray(12) { i -> i.toByte() }, true)
                    .also { lastModifiedRequest = lastModified }
        }

        val data = networkEnabledCacheWithHit.loadArtifact()!!
        assertEquals(42, lastModifiedRequest)
        val lastModifiedFile = Files.getLastModifiedTime(networkEnabledCacheWithHit.artifactPath)
        assertTrue(lastModifiedFile.toMillis() > 42)
        assertContentEquals(data.readBytes().also { data.close() }, ByteArray(12) { i -> i.toByte() })
    }

    @Test
    fun testNetworkDisabledDeprecatedOverride() {
        val cache = object : TestCache(networkEnabled = false) {
            @Deprecated("use method with lastModified")
            override fun readUrlData(url: String, timeout: Int): ByteArray? {
                fail("No network calls expected")
                return null
            }
        }

        cache.loadArtifact()
    }

    @Test
    fun testNetworkEnabledDeprecatedOverride() {
        var networkCalls = 0
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            @Deprecated("use method with lastModified")
            override fun readUrlData(url: String, timeout: Int): ByteArray? {
                networkCalls++
                return null
            }
        }

        networkEnabledCache.loadArtifact()
        assertEquals(1, networkCalls)
    }

    @Test
    fun testCacheHitDeprecatedOverride() {
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            val artifactPath = cacheDir!!.resolve("artifact.xml")

            init {
                Files.createFile(artifactPath)
                Files.write(artifactPath, ByteArray(5) { i -> i.toByte() })
            }

            @Deprecated("use method with lastModified")
            override fun readUrlData(url: String, timeout: Int) =
                null.also { fail("No network calls expected") }

        }
        val data = networkEnabledCache.loadArtifact()!!
        assertContentEquals(data.readBytes(), ByteArray(5) { i -> i.toByte() })
    }

    @Test
    fun testCacheMissDeprecatedOverride() {
        val networkEnabledCacheWithMiss = object : TestCache(networkEnabled = true) {
            val artifactPath = cacheDir!!.resolve("artifact.xml")
            init {
                Files.createFile(artifactPath)
                Files.write(artifactPath, ByteArray(1) { i -> i.toByte() })
                Files.setLastModifiedTime(artifactPath, FileTime.fromMillis(42))
            }

            @Deprecated("use method with lastModified")
            override fun readUrlData(url: String, timeout: Int) = ByteArray(5) { i -> i.toByte() }
        }

        val data = networkEnabledCacheWithMiss.loadArtifact()!!
        val lastModifiedFile = Files.getLastModifiedTime(networkEnabledCacheWithMiss.artifactPath)
        assertTrue(lastModifiedFile.toMillis() > 42)
        assertContentEquals(data.readBytes().also { data.close() }, ByteArray(5) { i -> i.toByte() })
    }

    private abstract class TestCache(
            cacheDir: Path = Files.createTempDirectory(""),
            networkEnabled: Boolean
    ) : NetworkCache("", "cacheKey", cacheDir, networkEnabled = networkEnabled) {
        fun loadArtifact() = findData("artifact.xml")

        override fun readDefaultData(relative: String): InputStream? = null

        override fun error(throwable: Throwable, message: String?) =
                fail("No error calls expected")
    }
}

