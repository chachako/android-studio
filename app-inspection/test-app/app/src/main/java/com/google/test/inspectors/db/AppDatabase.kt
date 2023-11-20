package com.google.test.inspectors.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      Settings::class,
    ],
  version = 1,
  exportSchema = false,
)
internal abstract class AppDatabase : RoomDatabase() {

  abstract fun settingsDao(): SettingsDao
}
