package com.google.test.inspectors.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.google.test.inspectors.grpc.GrpcClient
import com.google.test.inspectors.grpc.GrpcClient.ChannelBuilderType.MANAGED_FOR_ADDRESS

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
