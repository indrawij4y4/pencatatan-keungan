package com.example.pencatatankeungaan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: run {
                    // Force SQLCipher libraries to load
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
                    
                    // Secure key generation using hardware KeyStore
                    val passphrase = KeyStoreHelper.getOrCreatePassphrase(context.applicationContext)
                    val factory = SupportFactory(passphrase)

                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "secure_cashflow.db"
                    )
                        .openHelperFactory(factory)
                        .build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
