package com.denalal.expensevision.core.database.di

import com.denalal.expensevision.core.model.repository.ReceiptRepository
import com.denalal.expensevision.core.database.repository.ReceiptRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository
}