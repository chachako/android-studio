package com.google.test.inspectors.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface SettingsDao {

  @Query("SELECT value FROM settings WHERE name=:name") suspend fun getValue(name: String): String?

  suspend fun getValue(name: String, defaultValue: String) = getValue(name) ?: defaultValue

  suspend fun getValue(name: String, defaultValue: Int) =
    getValue(name)?.toIntOrNull() ?: defaultValue

  @Upsert suspend fun upsert(settings: Settings)
}
