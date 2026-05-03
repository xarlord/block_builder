package com.architectai.core.data.di

import android.content.Context
import androidx.room.Room
import com.architectai.core.data.local.AppDatabase
import com.architectai.core.data.local.CompositionDao
import com.architectai.core.data.llm.LLMClient
import com.architectai.core.data.llm.OkHttpLLMClient
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.data.repository.CompositionRepositoryImpl
import com.architectai.core.data.template.TemplateLoader
import com.architectai.core.domain.model.TemplateEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindCompositionRepository(
        impl: CompositionRepositoryImpl
    ): CompositionRepository

    @Binds
    @Singleton
    abstract fun bindLLMClient(
        impl: OkHttpLLMClient
    ): LLMClient

    companion object {
        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "architect-ai-db"
            ).build()
        }

        @Provides
        fun provideCompositionDao(database: AppDatabase): CompositionDao {
            return database.compositionDao()
        }

        @Provides
        @Singleton
        fun provideTemplateEngine(): TemplateEngine {
            return TemplateEngine()
        }

        @Provides
        @Singleton
        fun provideOkHttpLLMClient(templateEngine: TemplateEngine): OkHttpLLMClient {
            val client = OkHttpLLMClient(baseUrl = "http://10.0.2.2:8080")
            // Set the template catalog for LLM prompting
            client.templateCatalog = templateEngine.getCatalogText()
            return client
        }

        @Provides
        @Singleton
        fun provideTemplateLoader(
            @ApplicationContext context: Context,
            templateEngine: TemplateEngine
        ): TemplateLoader {
            return TemplateLoader(context, templateEngine)
        }
    }
}
