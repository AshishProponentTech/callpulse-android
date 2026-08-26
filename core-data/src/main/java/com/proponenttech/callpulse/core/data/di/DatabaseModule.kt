package com.proponenttech.callpulse.core.data.di

import android.content.Context
import androidx.room.Room
import com.proponenttech.callpulse.core.data.db.CallPulseDatabase
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.session.SecureStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * §11.3 — call data is never stored on the device unencrypted. The passphrase is generated
     * once and kept in EncryptedSharedPreferences behind the Android Keystore, so it never
     * appears in the APK or in any file an attacker with the database could also read.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secureStore: SecureStore,
    ): CallPulseDatabase {
        System.loadLibrary("sqlcipher")

        val factory = SupportOpenHelperFactory(
            secureStore.databasePassphrase.toByteArray(Charsets.UTF_8),
        )

        return Room.databaseBuilder(context, CallPulseDatabase::class.java, CallPulseDatabase.NAME)
            .openHelperFactory(factory)
            // Deliberately NO fallbackToDestructiveMigration — see §19.
            .build()
    }

    @Provides
    @Singleton
    fun provideCallSessionDao(database: CallPulseDatabase): CallSessionDao = database.callSessionDao()
}
