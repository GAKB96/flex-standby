package nz.gaurav.flexstandby

import android.content.Context

/** Small wrapper around SharedPreferences for the user's settings. */
object Prefs {
    private const val FILE = "flex_standby"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun accent(c: Context): String = sp(c).getString("accent", "#F28A1F") ?: "#F28A1F"
    fun setAccent(c: Context, hex: String) = sp(c).edit().putString("accent", hex).apply()

    fun alarmOn(c: Context): Boolean = sp(c).getBoolean("alarm_on", false)
    fun alarmHour(c: Context): Int = sp(c).getInt("alarm_hour", 6)
    fun alarmMinute(c: Context): Int = sp(c).getInt("alarm_minute", 30)
    fun setAlarm(c: Context, on: Boolean, hour: Int, minute: Int) =
        sp(c).edit().putBoolean("alarm_on", on).putInt("alarm_hour", hour).putInt("alarm_minute", minute).apply()

    fun watcherOn(c: Context): Boolean = sp(c).getBoolean("watcher_on", false)
    fun setWatcherOn(c: Context, on: Boolean) = sp(c).edit().putBoolean("watcher_on", on).apply()
}
