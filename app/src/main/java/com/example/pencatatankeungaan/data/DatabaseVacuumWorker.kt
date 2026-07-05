package com.example.pencatatankeungaan.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseVacuumWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("DatabaseVacuumWorker", "Starting database VACUUM optimization...")
            val db = AppDatabase.getInstance(applicationContext)
            db.openHelper.writableDatabase.execSQL("VACUUM")
            Log.d("DatabaseVacuumWorker", "Database VACUUM completed successfully!")
            Result.success()
        } catch (e: Exception) {
            Log.e("DatabaseVacuumWorker", "Error executing database VACUUM", e)
            Result.retry()
        }
    }
}
