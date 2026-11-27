package com.denalal.expensevision.core.database.di

import android.content.Context
import androidx.room3.Room
import com.denalal.expensevision.core.database.AppDatabase
import com.denalal.expensevision.core.database.dao.ReceiptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "expense_vision.db").build()

    @Provides
    fun provideReceiptDao(db: AppDatabase): ReceiptDao = db.receiptDao()
}