package nz.gaurav.flexstandby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Runs in the background and reads the hinge-angle sensor.
 * When the phone is half-folded (tent / laptop angle), it shows a round button
 * in the bottom-right corner of whatever screen is on. Tapping it opens the standby clock.
 */
class HingeWatchService : Service(), SensorEventListener {

    private lateinit var sensors: SensorManager
    private lateinit var wm: WindowManager
    private var button: ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        wm = getSystemService(WindowManager::class.java)
        goForeground()

        val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (hinge == null) {
            Toast.makeText(this, "No hinge sensor found on this phone", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        sensors.registerListener(this, hinge, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensors.unregisterListener(this)
        hideButton()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val angle = event.values[0]                 // 0 = closed, 180 = fully open
        val halfFolded = angle in HALF_MIN..HALF_MAX
        if (halfFolded && StandbyActivity.isShowing.not()) showButton() else hideButton()
    }

    private fun showButton() {
        if (button != null) return
        if (!Settings.canDrawOverlays(this)) return

        val size = dp(60)
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_notify)
            setColorFilter(Color.WHITE)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(Prefs.accent(this@HingeWatchService)))
            }
            contentDescription = "Open Flex Standby"
            elevation = dp(6).toFloat()
            setOnClickListener {
                hideButton()
                startActivity(
                    Intent(this@HingeWatchService, StandbyActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(24)
        }
        try {
            wm.addView(view, lp)
            button = view
        } catch (_: Exception) { }
    }

    private fun hideButton() {
        button?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        button = null
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Flex watcher", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Flex Standby is ready")
            .setContentText("Half-fold the phone to get the clock button")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val CHANNEL = "watcher"
        private const val NOTIF_ID = 3
        // Angles counted as "half-folded". Tent mode is usually ~60–120°.
        private const val HALF_MIN = 30f
        private const val HALF_MAX = 150f

        fun start(c: Context) {
            c.startForegroundService(Intent(c, HingeWatchService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, HingeWatchService::class.java))
        }
    }
}
