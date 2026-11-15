package ai.hey.phone.notify

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.Poller
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** "HH:mm" -> LocalTime, null when it is not a valid time. */
fun parseCheckin(text: String?): LocalTime? = runCatching { LocalTime.parse(text ?: return null, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()

fun formatCheckin(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

/** Delay from [now] to the next [at] (today when still ahead, else tomorrow). Always > 0. */
fun delayUntilNext(at: LocalTime, now: LocalDateTime): Duration {
    var next = now.toLocalDate().atTime(at)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

/** 15-minute fallback poll (WorkManager's minimum period): new friend messages and today's diary, while the app process may be dead. */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = HeyPhoneApp.from(applicationContext)
        if (app.tokens.load() == null) return Result.success()
        val poller = Poller(
            api = app.api, seen = app.seen, sink = { Notifier.handle(app, it) },
            today = { LocalDate.now().toString() }, nowIso = { java.time.Instant.now().toString() },
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

/** Daily "오늘 몇 시에 나가?" nudge; each run re-arms itself for the next day while a time is set. */
class CheckinWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = HeyPhoneApp.from(applicationContext)
        val at = parseCheckin(app.prefs.checkinTime) ?: return Result.success()
        if (app.tokens.load() != null) {
            Notifier.show(applicationContext, NoticeText(Notifier.ID_CHECKIN, Notifier.CH_DAILY, "헤이리", "오늘 몇 시에 나가?"))
        }
        Schedule.checkin(applicationContext, at)
        return Result.success()
    }
}

object Schedule {
    private const val POLL = "hey-poll"
    private const val CHECKIN = "hey-checkin"

    fun poll(context: Context) {
        val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(POLL, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun checkin(context: Context, at: LocalTime) {
        val delay = delayUntilNext(at, LocalDateTime.now(ZoneId.systemDefault()))
        val req = OneTimeWorkRequestBuilder<CheckinWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(CHECKIN, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelCheckin(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(CHECKIN)

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(POLL)
        cancelCheckin(context)
    }
}
