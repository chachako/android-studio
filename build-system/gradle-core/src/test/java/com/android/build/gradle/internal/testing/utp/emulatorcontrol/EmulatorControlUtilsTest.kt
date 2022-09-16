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

package com.android.build.gradle.internal.testing.utp

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.jwt.JwkSetConverter
import com.google.crypto.tink.jwt.JwtPublicKeyVerify
import com.google.crypto.tink.jwt.JwtSignatureConfig
import com.google.crypto.tink.jwt.JwtValidator
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

class EmulatorControlUtilsTest {

    @get:Rule
    val testDir = TemporaryFolder()
    private val iss = "gradle-utp-emulator-control"

    @Test
    fun createEmptyConfig() {
        val jwtConfig = createJwtConfig(setOf(), 20, iss, null)
        assertThat(jwtConfig.token).isEmpty()
        assertThat(jwtConfig.jwkPath).isEmpty()
    }

    @Test
    fun setsValues() {
        val jwtConfig = createJwtConfig(setOf(), 20, iss, testDir.root.absolutePath)
        assertThat(jwtConfig.token).isNotEmpty()
        assertThat(jwtConfig.jwkPath).isNotEmpty()
        assertThat(File(jwtConfig.jwkPath).exists()).isTrue()
    }

    @Test
    fun writesAJwkWithOneKey() {
        val jwtConfig = createJwtConfig(setOf(), 20, iss, testDir.root.absolutePath)
        assertThat(jwtConfig.jwkPath).isNotEmpty()
        val jwt = File(jwtConfig.jwkPath).readText(Charsets.UTF_8)
        val handle = JwkSetConverter.toPublicKeysetHandle(jwt)
        assertThat(handle.size()).isEqualTo(1)
    }

    @Test
    fun createsAJwtToken() {
        val jwtConfig = createJwtConfig(setOf(), 20, iss, testDir.root.absolutePath)
        assertThat(jwtConfig.token).isNotEmpty()
    }

    @Test
    fun canDecodeATokenWithTheKey() {
        JwtSignatureConfig.register()

        val jwtConfig = createJwtConfig(setOf(), 20, iss, testDir.root.absolutePath)
        val signedToken = jwtConfig.token
        val jwt = File(jwtConfig.jwkPath).readText(Charsets.UTF_8)
        val publicKeysetHandle = JwkSetConverter.toPublicKeysetHandle(jwt)

        val validator = JwtValidator.newBuilder().ignoreAudiences().expectIssuer("gradle-utp-emulator-control").build()
        val verifier = publicKeysetHandle.getPrimitive(JwtPublicKeyVerify::class.java)
        val verifiedJwt = verifier.verifyAndDecode(signedToken, validator)
        val seconds = ChronoUnit.SECONDS.between(Instant.now(), verifiedJwt.expiration)
        assertThat(seconds).isGreaterThan(0)
    }

    @Test
    fun audiencesInTheToken() {
        JwtSignatureConfig.register()

        val aud = setOf("a", "b")
        val jwtConfig = createJwtConfig(aud, 20, iss, testDir.root.absolutePath)
        val publicKeysetHandle =
            JwkSetConverter.toPublicKeysetHandle(File(jwtConfig.jwkPath).readText(Charsets.UTF_8))

        val validator = JwtValidator.newBuilder().ignoreAudiences().expectIssuer(iss).build()
        val verifier = publicKeysetHandle.getPrimitive(JwtPublicKeyVerify::class.java)
        val verifiedJwt = verifier.verifyAndDecode(jwtConfig.token, validator)

        assertThat(verifiedJwt.audiences.size).isEqualTo(2)
        assertThat(verifiedJwt.audiences).contains("a")
        assertThat(verifiedJwt.audiences).contains("b")
    }

    @Test
    fun idInTheToken() {
        JwtSignatureConfig.register()

        val aud = setOf("a", "b")
        val jwtConfig = createJwtConfig(aud, 20, iss, testDir.root.absolutePath)
        val publicKeysetHandle =
            JwkSetConverter.toPublicKeysetHandle(File(jwtConfig.jwkPath).readText(Charsets.UTF_8))

        val validator = JwtValidator.newBuilder().ignoreAudiences().expectIssuer(iss).build()
        val verifier = publicKeysetHandle.getPrimitive(JwtPublicKeyVerify::class.java)
        val verifiedJwt = verifier.verifyAndDecode(jwtConfig.token, validator)
        assertThat(verifiedJwt.jwtId).isNotEmpty()
    }
}
