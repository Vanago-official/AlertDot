package com.vanago.alertdot.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.view.Display
import android.util.Log
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vanago.alertdot.R
import com.vanago.alertdot.app
import com.vanago.alertdot.data.Corner
import com.vanago.alertdot.data.DotStyle
import com.vanago.alertdot.ui.MainActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/** Foreground service that keeps a small colored dot on top of all apps. */
class OverlayService : LifecycleService() {
    private lateinit var wm: WindowManager
    /** Attached only while the current level's dot is visible. */
    private var dot: DotView? = null
    private var current: DotStyle? = null
    /** Params last applied to [dot]; used to skip no-op updateViewLayout calls. */
    private var applied: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        lifecycleScope.launch {
            combine(
                app.stateRepo.state.map { it.level },
                app.overlayPreviewLevel,
                // Ignore settings changes that do not touch the overlay (token, rules, ...).
                app.settingsRepo.settings.map { it.overlay }.distinctUntilChanged(),
            ) { level, preview, overlay -> overlay.style(preview ?: level) }
                .distinctUntilChanged()
                .collect { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation moves the camera cutout and the screen edges.
        current?.let { render(it) }
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun render(style: DotStyle) {
        current = style
        if (!style.visible) {
            detach()
            return
        }
        val geometry = geometry(style)
        val params = layoutParams(style, geometry)
        val existing = dot
        if (existing == null) {
            val v = DotView(this)
            // Set before addView, so the first frame is already correct and no invalidate follows.
            v.set(style.color.toInt(), geometry, outline = style.corner != Corner.CAMERA)
            try {
                wm.addView(v, params)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot add overlay", e)
                return
            }
            dot = v
            applied = params
            return
        }
        existing.set(style.color.toInt(), geometry, outline = style.corner != Corner.CAMERA)
        // Only relayout the window when its size, position or alpha actually changed
        // (e.g. a color change or an unrelated configuration change needs no relayout).
        if (applied?.let { sameWindow(it, params) } != true) {
            runCatching { wm.updateViewLayout(existing, params) }
                .onSuccess { applied = params }
                .onFailure { Log.w(TAG, "Cannot update overlay", it) }
        }
    }

    private fun sameWindow(a: WindowManager.LayoutParams, b: WindowManager.LayoutParams) =
        a.width == b.width && a.height == b.height && a.x == b.x && a.y == b.y &&
            a.gravity == b.gravity && a.alpha == b.alpha

    private fun detach() {
        dot?.let { runCatching { wm.removeView(it) } }
        dot = null
        applied = null
    }

    /**
     * Window size/position and the circle inside it. The window can only sit on whole pixels,
     * so for the camera anchor the circle is drawn at a sub-pixel center inside a slightly
     * larger window — this keeps the ring concentric with the camera hole.
     */
    private class Geometry(
        val windowSize: Int,
        /** Absolute window position in screen pixels (gravity TOP|LEFT). */
        val x: Int,
        val y: Int,
        /** Circle center inside the window, sub-pixel. */
        val cx: Float,
        val cy: Float,
        val radius: Float,
    )

    /**
     * One formula for every anchor: compute the exact (fractional) center of the dot on the
     * screen, then place a slightly larger window on whole pixels and draw the circle at the
     * sub-pixel center inside it. Offsets are signed dp: +X right, +Y down.
     */
    private fun geometry(o: DotStyle): Geometry {
        val density = resources.displayMetrics.density
        val diameter = o.sizeDp.coerceAtLeast(1f) * density
        val r = diameter / 2f
        val window = ceil(diameter).toInt() + 2
        val anchor = anchorCenter(o.corner, r)
        val targetX = anchor.x + o.offsetXdp * density
        val targetY = anchor.y + o.offsetYdp * density
        val x = floor(targetX - window / 2f).toInt()
        val y = floor(targetY - window / 2f).toInt()
        return Geometry(window, x, y, targetX - x, targetY - y, r)
    }

    /** Dot center for zero offsets. Edge anchors keep the dot flush with the screen edge. */
    private fun anchorCenter(corner: Corner, r: Float): PointF {
        if (corner == Corner.CAMERA) cameraCenter()?.let { return it }
        val size = screenSize()
        val w = size.x.toFloat()
        val h = size.y.toFloat()
        val cx = when (corner.col) {
            0 -> r
            1 -> w / 2f
            else -> w - r
        }
        val cy = when (corner.row) {
            0 -> r
            1 -> h / 2f
            else -> h - r
        }
        return PointF(cx, cy)
    }

    private fun display(): Display? =
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** Full screen size in pixels for the current rotation (including system bars and cutout). */
    private fun screenSize(): Point {
        val p = Point()
        @Suppress("DEPRECATION")
        display()?.getRealSize(p)
        if (p.x == 0 || p.y == 0) {
            p.x = resources.displayMetrics.widthPixels
            p.y = resources.displayMetrics.heightPixels
        }
        return p
    }

    private fun layoutParams(o: DotStyle, g: Geometry): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            g.windowSize, g.windowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = g.x
            y = g.y
            alpha = o.opacity.coerceIn(0.05f, 1f)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "AlertDot dot"
        }

    /** Exact center of the camera cutout in screen pixels for the current rotation, or null if there is none. */
    private fun cameraCenter(): PointF? {
        val rects = display()?.cutout?.boundingRects.orEmpty().filter { !it.isEmpty }
        // Several cutouts are rare; take the smallest (a punch-hole rather than a wide notch).
        val rect = rects.minByOrNull { it.width() * it.height() } ?: return null
        return PointF(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_dot)
            .setContentTitle("AlertDot активний")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_OVERLAY, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_OVERLAY, notification)
        }
    }

    private class DotView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0x66000000
            strokeWidth = context.resources.displayMetrics.density
        }
        private var cx = 0f
        private var cy = 0f
        private var radius = 0f
        private var outline = true

        fun set(color: Int, g: Geometry, outline: Boolean) {
            if (fill.color == color && cx == g.cx && cy == g.cy && radius == g.radius && this.outline == outline) return
            fill.color = color
            cx = g.cx
            cy = g.cy
            radius = g.radius
            this.outline = outline
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(cx, cy, radius, fill)
            // Thin dark edge keeps a corner dot visible on white screens; not used around the camera.
            if (outline) canvas.drawCircle(cx, cy, radius - outlinePaint.strokeWidth / 2, outlinePaint)
        }
    }

    companion object {
        private const val TAG = "OverlayService"

        fun start(context: Context) {
            // Can throw (e.g. ForegroundServiceStartNotAllowedException when started from the background
            // on Android 12+); a failed start must not crash the caller (boot receiver, UI).
            try {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start overlay service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
