/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.test.inspectors.settings.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.google.test.inspectors.network.grpc.GrpcClient
import com.google.test.inspectors.network.grpc.GrpcClient.ChannelBuilderType.MANAGED_FOR_ADDRESS

@Dao
internal interface SettingsDao {

  @Query("SELECT value FROM settings WHERE name=:name") suspend fun getValue(name: String): String?

  @Upsert suspend fun upsert(settings: Settings)

  suspend fun getValue(name: String, defaultValue: String) = getValue(name) ?: defaultValue

  suspend fun getValue(name: String, defaultValue: Int) =
    getValue(name)?.toIntOrNull() ?: defaultValue

  suspend fun getHost() = getValue("host") ?: ""

  suspend fun getPort() = getValue("port")?.toIntOrNull() ?: DEFAULT_PORT

  suspend fun getChannelBuilderType() =
    GrpcClient.ChannelBuilderType.valueOf(
      getValue("channelBuilderType") ?: DEFAULT_CHANNEL_BUILDER_TYPE.name
    )

  suspend fun setHost(host: String) {
    upsert(Settings("host", host))
  }

  suspend fun setPort(port: Int) {
    upsert(Settings("port", port.toString()))
  }

  suspend fun setChannelBuilderType(channelBuilderType: GrpcClient.ChannelBuilderType) {
    upsert(Settings("channelBuilderType", channelBuilderType.name))
  }

  companion object {

    const val DEFAULT_PORT = 54321
    val DEFAULT_CHANNEL_BUILDER_TYPE = MANAGED_FOR_ADDRESS
  }
}
