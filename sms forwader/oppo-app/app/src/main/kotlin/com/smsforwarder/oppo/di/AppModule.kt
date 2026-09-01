package com.smsforwarder.oppo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.smsforwarder.oppo.BuildConfig
import com.smsforwarder.oppo.data.local.AppDatabase
import com.smsforwarder.oppo.data.local.dao.FilterRuleDao
import com.smsforwarder.oppo.data.local.dao.PendingMessageDao
import com.smsforwarder.oppo.network.ApiService
import com.smsforwarder.oppo.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt DI module providing application-scoped singletons.
 *
 * SECURITY NOTES:
 * - [EncryptedSharedPreferences] uses AES-256-GCM backed by the MasterKey
 *   in Android Keystore. Device credentials protect the master key.
 * - HTTP logging is DISABLED in release builds to prevent plaintext leakage.
 * - The OkHttp client uses standard Android TLS validation (no bypass).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─────────────────────────────────────────────
    // Encrypted SharedPreferences
    // Stores: deviceId, deviceApiKey, destPublicKeyPem, pairing state
    // ─────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("encrypted")
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "sms_forwarder_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─────────────────────────────────────────────
    // Room Database
    // ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Phase 2: simple strategy; replace with migrations in production
            .build()

    @Provides
    @Singleton
    fun providePendingMessageDao(db: AppDatabase): PendingMessageDao =
        db.pendingMessageDao()

    @Provides
    @Singleton
    fun provideFilterRuleDao(db: AppDatabase): FilterRuleDao =
        db.filterRuleDao()

    // ─────────────────────────────────────────────
    // Networking
    // ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        @Named("encrypted") prefs: SharedPreferences
    ): AuthInterceptor = AuthInterceptor(prefs)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor) // Auth always first

        // SECURITY: logging interceptor only in debug builds.
        // HEADERS only — never logs request/response bodies.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val rawUrl = BuildConfig.BACKEND_BASE_URL
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
