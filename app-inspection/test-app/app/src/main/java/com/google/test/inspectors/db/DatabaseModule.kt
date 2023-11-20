package com.google.test.inspectors.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

  @Singleton
  @Provides
  fun provideDataBase(@ApplicationContext context: Context): AppDatabase {
    return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "database.db")
      .build()
  }

  @Provides fun provideSettingsDao(database: AppDatabase): SettingsDao = database.settingsDao()
}
