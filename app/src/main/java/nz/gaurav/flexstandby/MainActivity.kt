package nz.gaurav.flexstandby

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/** Simple setup screen: permissions, alarm time, and the half-fold watcher switch. */
class MainActivity : ComponentActivity() {

    private lateinit var overlayBtn: Button
    private lateinit var alarmBtn: Button
    private lateinit var alarmSwitch: Switch
    private lateinit var watchSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        col.addView(text("Flex Standby", 28f, bold = true))
        col.addView(text("Stand your Fold like a tent. The outside screen becomes a clock and calendar, and the inside screen glows when the alarm rings.", 16f, muted = true))

        col.addView(section("1. Permissions"))
        overlayBtn = button("Allow \"display over other apps\"") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        col.addView(overlayBtn)
        if (Build.VERSION.SDK_INT >= 33) {
            col.addView(button("Allow notifications") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
        }

        col.addView(section("2. Alarm"))
        alarmSwitch = Switch(this).apply {
            text = "Alarm on"
            setTextColor(Color.WHITE)
            textSize = 17f
            isChecked = Prefs.alarmOn(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setAlarm(this@MainActivity, on, Prefs.alarmHour(this@MainActivity), Prefs.alarmMinute(this@MainActivity))
                AlarmScheduler.scheduleNext(this@MainActivity)
            }
        }
        col.addView(alarmSwitch)
        alarmBtn = button("") {
            TimePickerDialog(this, { _, h, m ->
                Prefs.setAlarm(this, true, h, m)
                alarmSwitch.isChecked = true
                AlarmScheduler.scheduleNext(this)
                refresh()
            }, Prefs.alarmHour(this), Prefs.alarmMinute(this), false).show()
        }
        col.addView(alarmBtn)

        col.addView(section("3. Half-fold button"))
        watchSwitch = Switch(this).apply {
            text = "Show the clock button when half-folded"
            setTextColor(Color.WHITE)
            textSize = 17f
            isChecked = Prefs.watcherOn(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setWatcherOn(this@MainActivity, on)
                if (on) HingeWatchService.start(this@MainActivity) else HingeWatchService.stop(this@MainActivity)
            }
        }
        col.addView(watchSwitch)
        col.addView(text("When on, a round button appears in the bottom-right corner whenever the phone is half-folded. Tap it to start the bedside clock.", 14f, muted = true))

        col.addView(section("Try it"))
        col.addView(button("Open standby now") {
            startActivity(Intent(this, StandbyActivity::class.java))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0C"))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (Prefs.watcherOn(this)) HingeWatchService.start(this)
    }

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        overlayBtn.text = if (ok) "Display over other apps: allowed ✓" else "Allow \"display over other apps\""
        val h = Prefs.alarmHour(this); val m = Prefs.alarmMinute(this)
        val h12 = if (h % 12 == 0) 12 else h % 12
        alarmBtn.text = "Alarm time: %d:%02d %s  (tap to change)".format(h12, m, if (h < 12) "AM" else "PM")
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) { /* the notifications button stays visible */ }
    }

    // ---------- tiny UI helpers ----------

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false, muted: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(if (muted) Color.parseColor("#A3A3A3") else Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun section(s: String) = text(s, 13f, bold = true).apply {
        setTextColor(Color.parseColor("#F28A1F"))
        setPadding(0, dp(24), 0, dp(4))
        letterSpacing = 0.06f
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        minHeight = dp(52)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#1A1A1C"))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }
}
