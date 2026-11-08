package nz.gaurav.flexstandby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart: put the alarm back and restart the hinge watcher if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.scheduleNext(context)
        if (Prefs.watcherOn(context)) HingeWatchService.start(context)
    }
}
