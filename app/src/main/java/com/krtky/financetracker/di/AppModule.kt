package com.krtky.financetracker.di

import android.content.Context
import androidx.room.Room
import com.krtky.financetracker.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "finance_tracker.db")
            .addMigrations(
                // v1 is unsupported: there is no 1→2. Schema export starts at v10.
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
            )
            // If a newer build raised the version and an older APK is installed later,
            // wipe rather than crash with "migration from X to Y was required".
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
}
