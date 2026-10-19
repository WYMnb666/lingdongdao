package com.example.dynamicisland

import android.animation.ValueAnimator
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class IslandService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var view: IslandView
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var expanded = false
    private var currentSongKey = ""
    private var lastAnimator: ValueAnimator? = null

    private val dp get() = resources.displayMetrics.density

    private val sizeMini = Size(176 * dp, 38 * dp, 19 * dp)
    private val sizeExpanded = Size(340 * dp, 200 * dp, 40 * dp)

    private val refreshRunnable = object : Runnable {
        override fun run() {
            syncFromState()
            handler.postDelayed(this, 80)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        createOverlay()
        MediaStateHolder.addListener { syncFromState() }
        handler.post(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        try { wm.removeView(view) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val channelId = "island_fg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "灵动岛",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("灵动岛运行中")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n)
    }

    private fun createOverlay() {
        view = IslandView(this)
        lp = WindowManager.LayoutParams(
            sizeMini.w, sizeMini.h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (12 * dp).toInt()
        }
        view.radiusPx = sizeMini.r
        view.mode = IslandView.Mode.IDLE
        view.setOnClickListener { toggleExpand() }
        wm.addView(view, lp)
    }

    private fun toggleExpand() {
        expanded = !expanded
        animateTo(if (expanded) sizeExpanded else sizeMini)
        view.mode = when {
            expanded -> IslandView.Mode.EXPANDED
            MediaStateHolder.current.title.isNotEmpty() -> IslandView.Mode.MINI
            else -> IslandView.Mode.IDLE
        }
    }

    private fun animateTo(target: Size) {
        lastAnimator?.cancel()
        val curW = lp.width
        val curH = lp.height
        val interp = PathInterpolator(0.34f, 1.15f, 0.64f, 1f)
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = interp
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                lp.width = (curW + (target.w - curW) * f).toInt().coerceAtLeast(1)
                lp.height = (curH + (target.h - curH) * f).toInt().coerceAtLeast(1)
                view.radiusPx = target.r
                try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
            }
            start()
        }
        lastAnimator = a
    }

    private fun syncFromState() {
        val s = MediaStateHolder.current

        val key = "${s.title}|${s.artist}"
        if (key != currentSongKey && s.title.isNotBlank()) {
            currentSongKey = key
            scope.launch { fetchLyrics(s) }
        }

        val lyrics = MediaStateHolder.lyrics
        if (lyrics.isNotEmpty()) {
            val idx = lyrics.indexOfLast { it.timeMs <= s.positionMs }
            MediaStateHolder.setCurrentLine(idx)
        }

        if (!expanded && s.title.isNotBlank() && view.mode == IslandView.Mode.IDLE) {
            view.mode = IslandView.Mode.MINI
            animateTo(sizeMini)
        }
        if (!expanded && s.title.isBlank() && view.mode != IslandView.Mode.IDLE) {
            view.mode = IslandView.Mode.IDLE
            animateTo(sizeMini)
        }

        view.invalidate()
    }

    private fun fetchLyrics(s: MediaStateHolder.Snapshot) {
        scope.launch {
            val lines = KugouLyrics.fetch(s.title, s.artist, s.durationMs)
            MediaStateHolder.updateLyrics(lines)
        }
    }

    data class Size(val w: Int, val h: Int, val r: Float)
}
