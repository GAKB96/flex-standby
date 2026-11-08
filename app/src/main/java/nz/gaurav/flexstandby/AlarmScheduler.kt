package nz.gaurav.flexstandby

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules the daily alarm and snoozes with AlarmManager.setAlarmClock (shows in the status bar). */
object AlarmScheduler {
    private const val REQ_ALARM = 100

    private fun firePending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, REQ_ALARM, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun showPending(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 0, Intent(c, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAt(c: Context, timeMillis: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, showPending(c)), firePending(c))
    }

    /** Schedule the next occurrence of the saved daily alarm (or cancel it if it's off). */
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (!Prefs.alarmOn(c)) {
            am.cancel(firePending(c))
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Prefs.alarmHour(c))
            set(Calendar.MINUTE, Prefs.alarmMinute(c))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        setAt(c, cal.timeInMillis)
    }

    fun snooze(c: Context, minutes: Int = 9) {
        setAt(c, System.currentTimeMillis() + minutes * 60_000L)
    }
}
