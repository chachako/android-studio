package com.google.test.inspectors.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
  tableName = "settings",
)
internal data class Settings(
  @PrimaryKey val name: String,
  val value: String,
)
