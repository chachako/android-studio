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

import com.google.common.truth.Truth.assertThat
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
    fun testParallelCounter() {
        val cache = object : TestCache(networkEnabled = true) {
            fun getCounter() = findDataParallelism
            fun getLockMapSize() = locks.size
            fun loadExtraArtifact() = findData("artifactExt.xml")
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true)
        }
        cache.loadArtifact()
        assertEquals(cache.getCounter(), 0)
        assertEquals(cache.getLockMapSize(), 1)

        cache.loadExtraArtifact()
        assertEquals(cache.getCounter(), 0)
        assertEquals(cache.getLockMapSize(), 0) // cleaned
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
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { fail("No network calls expected") }
        }

        cache.loadArtifact()
    }

    @Test
    fun testNetworkEnabledDeprecatedOverride() {
        var networkCalls = 0
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { networkCalls++ }
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

            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(null, true).also { fail("No network calls expected") }

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

            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(ByteArray(5) { i -> i.toByte() }, true)
        }

        val data = networkEnabledCacheWithMiss.loadArtifact()!!
        val lastModifiedFile = Files.getLastModifiedTime(networkEnabledCacheWithMiss.artifactPath)
        assertTrue(lastModifiedFile.toMillis() > 42)
        assertContentEquals(data.readBytes().also { data.close() }, ByteArray(5) { i -> i.toByte() })
    }

    @Test
    fun testKeyPrefixOfAnotherKey() {
        val servedContent = mapOf(
            "" to """root content""",
            "directory/" to """directory content""",
            "directory/file" to """file in directory content"""
        )
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(servedContent[url]?.toByteArray(Charsets.UTF_8), true)
        }
        assertThat(networkEnabledCache.getContentAsString("")).isEqualTo("root content")
        assertThat(networkEnabledCache.getContentAsString("directory")).isNull()
        assertThat(networkEnabledCache.getContentAsString("directory/")).isEqualTo("directory content")
        assertThat(networkEnabledCache.getContentAsString("directory/file")).isEqualTo("file in directory content")
    }

    @Test
    fun testTreatAsDirectory() {
        val servedContent = mapOf(
            "" to """root content""",
            "directory" to """directory content""",
            "directory/file" to """file in directory content"""
        )
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(servedContent[url]?.toByteArray(Charsets.UTF_8), true)
        }
        assertThat(networkEnabledCache.getContentAsString("")).isEqualTo("root content")
        assertThat(networkEnabledCache.getContentAsString("directory/")).isNull()
        assertThat(networkEnabledCache.getContentAsString("directory", treatAsDirectory = true)).isEqualTo("directory content")
        assertThat(networkEnabledCache.getContentAsString("directory/file")).isEqualTo("file in directory content")
    }

    @Test
    fun testPathologicalUrls() {
        val justNull = "\u0000"
        val windows = "con/prn/aux/nul/com0/com1/com2/com3/com4/com5/com6/com7/com8/com9/com¹/com²/com³/lpt0/lpt1/lpt2/lpt3/lpt4/lpt5/lpt6/lpt7/lpt8/lpt9/lpt¹/lpt²/lpt³/CON/PRN/AUX/NUL/COM0/COM1/COM2/COM3/COM4/COM5/COM6/COM7/COM8/COM9/COM¹/COM²/COM³/LPT0/LPT1/LPT2/LPT3/LPT4/LPT5/LPT6/LPT7/LPT8/LPT9/LPT¹/LPT²/LPT³/a"
        val servedContent = mapOf(
            justNull to """null content""",
            windows to """windows"""
        )
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
                ReadUrlDataResult(servedContent[url]?.toByteArray(Charsets.UTF_8), true)
        }
        assertThat(networkEnabledCache.getContentAsString(justNull)).isEqualTo("null content")
        assertThat(networkEnabledCache.getContentAsString(windows)).isEqualTo("windows")
    }


    private abstract class TestCache(
            cacheDir: Path = Files.createTempDirectory(""),
            networkEnabled: Boolean
    ) : NetworkCache("", "cacheKey", cacheDir, networkEnabled = networkEnabled) {
        fun loadArtifact() = findData("artifact.xml")

        fun getContentAsString(relative: String, treatAsDirectory: Boolean=false): String? = findData(relative, treatAsDirectory)?.use { it.readBytes().toString(Charsets.UTF_8) }

        override fun readDefaultData(relative: String): InputStream? = null

        override fun error(throwable: Throwable, message: String?) =
                fail("No error calls expected")
    }

}

