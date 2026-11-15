package ai.hey.phone.notify

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.Poller
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 15-minute fallback poll (WorkManager's minimum period): new friend messages and today's diary, while the app process may be dead. */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = HeyPhoneApp.from(applicationContext)
        if (app.tokens.load() == null) return Result.success()
        val poller = Poller(
            api = app.api, seen = app.seen, sink = { Notifier.handle(app, it) },
            today = { LocalDate.now(ZoneId.of("Asia/Seoul")).toString() }, nowIso = { java.time.Instant.now().toString() },
        )
        return try {
            poller.poll()
            Result.success()
        } catch (e: ApiException) {
            if (e.unauthorized) Result.success() else Result.retry()
        } catch (_: IOException) {
            Result.retry()
        }
    }
}

object Schedule {
    private const val POLL = "hey-poll"
    /** D-52: the daily check-in is gone; an older install may still have its one-shot queued. */
    private const val LEGACY_CHECKIN = "hey-checkin"

    fun poll(context: Context) {
        val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(POLL, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Drops the check-in work an older version queued (its worker class no longer exists). */
    fun dropLegacy(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(LEGACY_CHECKIN)

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(POLL)
        dropLegacy(context)
    }
}
