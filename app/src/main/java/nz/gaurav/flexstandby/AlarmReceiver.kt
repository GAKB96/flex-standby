package nz.gaurav.flexstandby

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Fires when the alarm time arrives: opens the standby screen in "ringing" mode. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = Intent(context, StandbyActivity::class.java)
            .putExtra(StandbyActivity.EXTRA_RINGING, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Full-screen notification: the reliable way for an alarm to take over the screen.
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            context, 1, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Alarm")
            .setContentText("Tap to open")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)

        // With "display over other apps" allowed we can also open it directly.
        try { context.startActivity(ring) } catch (_: Exception) { }

        // Line up tomorrow's alarm.
        AlarmScheduler.scheduleNext(context)
    }

    companion object {
        const val CHANNEL = "alarm"
        const val NOTIF_ID = 7
    }
}
