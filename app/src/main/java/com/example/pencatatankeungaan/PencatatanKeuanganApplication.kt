package com.example.pencatatankeungaan

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pencatatankeungaan.data.DatabaseVacuumWorker
import java.util.concurrent.TimeUnit

import net.sqlcipher.database.SQLiteDatabase

class PencatatanKeuanganApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SQLiteDatabase.loadLibs(this)
        schedulePeriodicVacuum()
    }

    private fun schedulePeriodicVacuum() {
        // Run optimization only when device is idle and battery is healthy
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val vacuumRequest = PeriodicWorkRequestBuilder<DatabaseVacuumWorker>(
            30, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DatabaseVacuumWork",
            ExistingPeriodicWorkPolicy.KEEP,
            vacuumRequest
        )
    }
}
