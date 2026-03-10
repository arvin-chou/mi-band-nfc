package com.mibandnfc.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mibandnfc.ble.BandService
import com.mibandnfc.data.db.NfcCardDao
import com.mibandnfc.model.CardType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoSwitchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: NfcCardDao,
    private val bandService: BandService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val rules = dao.getSwitchRules().first()
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val dayBitIndex = (currentDayOfWeek + 5) % 7

        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.hour != currentHour || rule.minute != currentMinute) continue
            if ((rule.daysOfWeek and (1 shl dayBitIndex)) == 0) continue

            bandService.setDefaultCard(rule.aid, CardType.fromProto(rule.cardType))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "AutoSwitchWork"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoSwitchWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
