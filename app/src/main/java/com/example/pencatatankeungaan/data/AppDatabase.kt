package com.example.pencatatankeungaan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

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
                    
                    try {
                        buildDatabase(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // If database building or opening fails, delete corrupted database files & passphrase file, and rebuild
                        deleteDatabaseFiles(context)
                        buildDatabase(context)
                    }
                }
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val passphrase = KeyStoreHelper.getOrCreatePassphrase(context.applicationContext)
            val factory = SupportFactory(passphrase)

            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "secure_cashflow.db"
            )
                .openHelperFactory(factory)
                .build()

            // Trigger a database open to verify if the passphrase works and KeyStore decryptions are valid
            db.openHelper.writableDatabase
            return db
        }

        private fun deleteDatabaseFiles(context: Context) {
            // Delete KeyStore encrypted passphrase file
            val passFile = File(context.filesDir, "db_pass_encrypted")
            if (passFile.exists()) {
                passFile.delete()
            }
            // Delete database files
            context.deleteDatabase("secure_cashflow.db")
        }
    }
}
