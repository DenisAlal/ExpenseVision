package com.denalal.expensevision.di

import android.content.Context
import com.denalal.expensevision.core.ocr.OcrEngine
import com.denalal.expensevision.core.ocr.PaddleOcrEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {
    @Provides
    @Singleton
    fun provideOcrEngine(@ApplicationContext context: Context): OcrEngine =
        PaddleOcrEngine(context)
}