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

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsDatabaseModule {

  @Singleton
  @Provides
  fun provideDataBase(@ApplicationContext context: Context): SettingsDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        SettingsDatabase::class.java,
        "settings.db",
      )
      .setAutoCloseTimeout(5, TimeUnit.SECONDS)
      .build()
  }

  @Provides fun provideSettingsDao(database: SettingsDatabase): SettingsDao = database.settingsDao()
}
