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
package com.android.fakeadbserver

/**
 * Parser for ADB service requests where each part is separated with a ":", e.g.
 * "host-serial:emulator-5554:get-serialno"
 */
internal class ServiceRequest(private val original: String) {

    private var request: String = original

    private var token: String = ""

    fun peekToken(): String {
        val separatorIndex = request.indexOf(SEPARATOR)
        return if (separatorIndex == -1) request else request.substring(0, separatorIndex)
    }

    fun nextToken(): String {
        val separatorIndex = request.indexOf(SEPARATOR)
        if (separatorIndex == -1) {
            token = request
            request = ""
            return token
        }
        token = request.substring(0, separatorIndex)
        request = request.substring(separatorIndex + 1)
        return token
    }

    fun currToken(): String {
        return token
    }

    fun remaining(): String {
        return request
    }

    fun original(): String {
        return original
    }

    companion object {

        private const val SEPARATOR = ':'
    }
}
