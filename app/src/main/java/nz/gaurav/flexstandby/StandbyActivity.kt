package nz.gaurav.flexstandby

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.window.core.ExperimentalWindowApi
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The bedside clock.
 *
 * Goal: OUTSIDE screen shows the clock/calendar (and Snooze/Stop when ringing),
 * INSIDE screen is only a light that glows in the accent colour when the alarm rings.
 *
 * Android lets an app show content on the "other" screen of a foldable through
 * Jetpack WindowManager's rear-display presentation. Which screen this activity
 * opened on decides who goes where:
 *  - opened on the outside screen -> this window = clock, other screen = glow
 *  - opened on the inside screen  -> this window = glow, other screen = clock
 * If the phone doesn't support showing on the other screen, the clock stays here
 * and the clock screen itself pulses with the glow colour.
 */
@OptIn(ExperimentalWindowApi::class)
class StandbyActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var glowHere: View          // used when this window is the inside screen
    private var glowThere: View? = null          // used when the glow is on the other screen
    private var session: WindowAreaSessionPresenter? = null
    private var pulse: ValueAnimator? = null
    private var ringtone: Ringtone? = null
    private var ringing = false
    private var onOutside = true
    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Outside screen is wide (about 1.58:1); inside screen is close to square (about 1.32:1).
        val dm = resources.displayMetrics
        val ratio = maxOf(dm.widthPixels, dm.heightPixels).toFloat() / minOf(dm.widthPixels, dm.heightPixels)
        onOutside = ratio > 1.45f

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    if (ringing) js("setRinging(true)")
                }
            }
            loadUrl("file:///android_asset/standby.html")
        }
        glowHere = View(this).apply { setBackgroundColor(Color.BLACK) }

        root = FrameLayout(this)
        setContentView(root)

        if (onOutside) {
            root.addView(web)
        } else {
            // This window is the inside screen: keep it black and dim; try to put the clock outside.
            root.addView(glowHere)
            setBrightness(0.01f)
            openOtherScreen { s -> s.setContentView(web) }
            // Fallback: if the other screen isn't available, show the clock here.
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1500)
                if (session == null && web.parent == null) {
                    root.removeAllViews(); root.addView(web); setBrightness(-1f); onOutside = true
                }
            }
        }

        if (intent.getBooleanExtra(EXTRA_RINGING, false)) startRinging()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RINGING, false)) startRinging()
    }

    override fun onResume() { super.onResume(); isShowing = true }
    override fun onPause() { super.onPause(); isShowing = false }

    override fun onDestroy() {
        stopSound()
        pulse?.cancel()
        session?.close()
        super.onDestroy()
    }

    // Volume keys stop the alarm too (handy if the outside screen can't take touches).
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ringing && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            stopAlarm(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- alarm ----------

    private fun startRinging() {
        if (ringing) return
        ringing = true
        if (pageReady) js("setRinging(true)")
        playSound()
        startGlow()
    }

    private fun stopAlarm() {
        ringing = false
        stopSound()
        stopGlow()
        js("setRinging(false)")
        getSystemService(android.app.NotificationManager::class.java).cancel(AlarmReceiver.NOTIF_ID)
    }

    private fun playSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            isLooping = true
            play()
        }
        val vib = getSystemService(VibratorManager::class.java).defaultVibrator
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0))
    }

    private fun stopSound() {
        ringtone?.stop(); ringtone = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
    }

    // ---------- glow on the inside screen ----------

    private fun accentColor(): Int = Color.parseColor(Prefs.accent(this))

    private fun startGlow() {
        if (!onOutside) {
            // We ARE the inside screen.
            setBrightness(1f)
            animateGlow(glowHere)
            return
        }
        // We're on the outside screen: light up the inside screen if the phone allows it.
        openOtherScreen { s ->
            val v = View(this).apply { setBackgroundColor(accentColor()) }
            glowThere = v
            s.setContentView(v)
            animateGlow(v)
        }
    }

    private fun stopGlow() {
        pulse?.cancel(); pulse = null
        if (!onOutside) {
            glowHere.setBackgroundColor(Color.BLACK); glowHere.alpha = 1f
            setBrightness(0.01f)
        } else {
            session?.close(); session = null; glowThere = null
        }
    }

    private fun animateGlow(v: View) {
        v.setBackgroundColor(accentColor())
        pulse?.cancel()
        pulse = ValueAnimator.ofFloat(0.55f, 1f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { v.alpha = it.animatedValue as Float }
            start()
        }
    }

    /** Opens a presentation on the other screen, if this phone supports it. */
    private fun openOtherScreen(onReady: (WindowAreaSessionPresenter) -> Unit) {
        if (session != null) { onReady(session!!); return }
        lifecycleScope.launch {
            try {
                val controller = WindowAreaController.getOrCreate()
                val infos = withTimeoutOrNull(1000) { controller.windowAreaInfos.first() } ?: return@launch
                val area = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } ?: return@launch
                val cap = area.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                if (cap?.status != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) return@launch
                controller.presentContentOnWindowArea(
                    area.token, this@StandbyActivity, mainExecutor,
                    object : WindowAreaPresentationSessionCallback {
                        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                            this@StandbyActivity.session = session
                            onReady(session)
                        }
                        override fun onSessionEnded(t: Throwable?) {
                            this@StandbyActivity.session = null
                        }
                        override fun onContainerVisibilityChanged(isVisible: Boolean) {}
                    }
                )
            } catch (_: Throwable) {
                // Not supported on this device: glow stays on the clock screen (handled in the page).
            }
        }
    }

    private fun setBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    /** Functions the web page can call. */
    inner class Bridge {
        @JavascriptInterface fun getAccent(): String = Prefs.accent(this@StandbyActivity)
        @JavascriptInterface fun setAccent(hex: String) = runOnUiThread {
            Prefs.setAccent(this@StandbyActivity, hex)
            glowThere?.setBackgroundColor(accentColor())
            if (ringing && !onOutside) glowHere.setBackgroundColor(accentColor())
        }
        @JavascriptInterface fun getAlarm(): String {
            val c = this@StandbyActivity
            if (!Prefs.alarmOn(c)) return ""
            val h = Prefs.alarmHour(c); val m = Prefs.alarmMinute(c)
            val h12 = if (h % 12 == 0) 12 else h % 12
            return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
        }
        @JavascriptInterface fun glowElsewhere(): Boolean = session != null || !onOutside
        @JavascriptInterface fun testAlarm() = runOnUiThread { startRinging() }
        @JavascriptInterface fun snooze() = runOnUiThread {
            AlarmScheduler.snooze(this@StandbyActivity); stopAlarm()
        }
        @JavascriptInterface fun stop() = runOnUiThread { stopAlarm() }
        @JavascriptInterface fun exit() = runOnUiThread { finish() }
    }

    companion object {
        const val EXTRA_RINGING = "ringing"
        @Volatile var isShowing = false
    }
}
