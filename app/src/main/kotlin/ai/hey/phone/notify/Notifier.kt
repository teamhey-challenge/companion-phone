package ai.hey.phone.notify

import ai.hey.phone.HeyPhoneApp
import ai.hey.phone.MainActivity
import ai.hey.phone.R
import ai.hey.phone.data.PushFrame
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notification texts for the two push kinds (D-52: friend message, trip-end diary = trip_done / diary_ready). Pure, so tests can pin the wording. */
data class NoticeText(val id: Int, val channel: String, val title: String, val body: String)

/** Text of the notification for [frame]; null for frames that never notify (friend_arrived is shown in the app only). */
fun noticeFor(frame: PushFrame): NoticeText? = when (frame) {
    is PushFrame.Message -> NoticeText(Notifier.ID_MESSAGE, Notifier.CH_FRIENDS, "${frame.fromName.ifBlank { "친구" }}의 메시지", frame.text)
    is PushFrame.Arrived -> null
    is PushFrame.TripDone -> NoticeText(Notifier.ID_TRIP, Notifier.CH_FRIENDS, "헤이리", frame.text)
    is PushFrame.DiaryReady -> NoticeText(Notifier.ID_DIARY, Notifier.CH_FRIENDS, "오늘의 일기가 나왔어", "같이 다닌 하루를 보러 와.")
}

object Notifier {
    const val CH_FRIENDS = "friends"
    const val ID_MESSAGE = 10
    const val ID_TRIP = 12
    const val ID_DIARY = 13

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CH_FRIENDS, context.getString(R.string.notif_channel_friends), NotificationManager.IMPORTANCE_HIGH))
    }

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, n: NoticeText) {
        if (!allowed(context)) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, n.id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, n.channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setAutoCancel(true)
            .setContentIntent(open)
        @Suppress("MissingPermission") // allowed() checked above
        NotificationManagerCompat.from(context).notify(n.id, b.build())
    }

    /** Routes a frame through the gate (dedupe + not-while-foreground) and shows it. */
    fun handle(app: HeyPhoneApp, frame: PushFrame) {
        app.events.tryEmit(frame)
        if (app.gate.shouldNotify(frame)) noticeFor(frame)?.let { show(app, it) }
    }
}
