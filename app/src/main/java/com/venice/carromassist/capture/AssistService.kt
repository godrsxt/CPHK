package com.venice.carromassist.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.FrameLayout
import com.venice.carromassist.*
import com.venice.carromassist.core.*
import com.venice.carromassist.core.GameState
import com.venice.carromassist.input.CarromAccessibilityService
import com.venice.carromassist.overlay.ControlPanel
import com.venice.carromassist.overlay.OverlayView
import com.venice.carromassist.physics.PhysParams
import com.venice.carromassist.solver.Solver
import com.venice.carromassist.vision.DiscDetector
import com.venice.carromassist.vision.Warp
import java.util.concurrent.atomic.AtomicBoolean

class AssistService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val CHAN = "carrom_assist"

        @Volatile var instance: AssistService? = null
        @Volatile var lastFrame: Bitmap? = null
        @Volatile var lastState: GameState? = null
        @Volatile var lastShot: Solver.Shot? = null
        @Volatile var status: String = ""
    }

    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val running = AtomicBoolean(false)

    private var wm: WindowManager? = null
    private var overlay: OverlayView? = null
    private var panel: ControlPanel? = null

    private lateinit var params: PhysParams
    private lateinit var solver: Solver

    private var lastMotion = 0L
    private var lastShotAt = 0L
    private var lastFramePixels: IntArray? = null
    private var lastBoard: IntArray? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        params = AssistApp.prefs.params
        solver = Solver(params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (code == Activity.RESULT_OK && data != null) startCapture(code, data)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHAN) == null) {
            nm.createNotificationChannel(NotificationChannel(CHAN, "CarromAssist", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, CHAN)
            .setContentTitle("CarromAssist running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    private fun startCapture(code: Int, data: Intent) {
        if (running.get()) return
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(code, data) ?: return
        projection = proj

        val metrics = resources.displayMetrics
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels
        val dpi = metrics.densityDpi

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))

        reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        vd = proj.createVirtualDisplay(
            "carrom-cap", sw, sh, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )

        addOverlays()
        running.set(true)
        thread = HandlerThread("carrom-loop").also { it.start() }
        handler = Handler(thread!!.looper)
        handler!!.post(loop)
        status = "Running — calibrate the board"
    }

    // ---------------- overlays ----------------

    private fun addOverlays() {
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        overlay = OverlayView(this)
        wm?.addView(overlay, overlayParams)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 160 }

        panel = ControlPanel(this).also {
            it.onToggleAuto = { on ->
                AssistApp.prefs.autoShoot = on; status = if (on) "AUTOPLAY ON" else "AUTOPLAY OFF"
            }
            it.onToggleQueen = { AssistApp.prefs.chaseQueen = !AssistApp.prefs.chaseQueen }
            it.onHide = { overlay?.visible = !(overlay?.visible ?: true); it.invalidate() }
            it.onMove = { dx, dy ->
                panelParams.x += dx; panelParams.y += dy
                wm?.updateViewLayout(panel, panelParams)
            }
        }
        wm?.addView(panel, panelParams)
    }

    private fun removeOverlays() {
        overlay?.let { runCatching { wm?.removeView(it) } }
        panel?.let { runCatching { wm?.removeView(it) } }
        overlay = null; panel = null
    }

    // ---------------- main loop ----------------

    private val loop = object : Runnable {
        override fun run() {
            if (!running.get()) return
            try { tick() } catch (t: Throwable) { status = "err: ${t.message}" }
            handler?.postDelayed(this, 90)
        }
    }

    private fun tick() {
        val img = reader?.acquireLatestImage() ?: return
        val plane = img.planes[0]
        val buf = plane.buffer
        val pxStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPad = rowStride - pxStride * img.width
        val bmpW = img.width + rowPad / pxStride
        val bmp = Bitmap.createBitmap(bmpW, img.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        val w = img.width; val h = img.height
        img.close()

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        if (bmpW != w) bmp.recycle()

        lastFrame = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)

        // motion gate — only think when the board has settled
        val prev = lastFramePixels
        var motion = 0
        if (prev != null && prev.size == pixels.size) {
            var i = 0
            while (i < pixels.size) {
                val a = pixels[i]; val b = prev[i]
                if (kotlin.math.abs(((a and 0xFF) - (b and 0xFF))) > 22 ||
                    kotlin.math.abs((((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))) > 22) motion++
                i += 11
            }
        }
        lastFramePixels = pixels
        val stable = motion < 40
        if (!stable) { lastMotion = SystemClock.uptimeMillis(); return }
        if (SystemClock.uptimeMillis() - lastMotion < 420) return

        val pr = AssistApp.prefs
        BoardSpec.applyScales(pr.coinScale, pr.strikerScale, pr.pocketScale)
        DiscDetector.minDist = pr.detectSens
        params = pr.params
        solver = Solver(params)

        val H = AssistApp.prefs.homography
        if (H == null) { status = "No calibration — run Calibrate"; return }

        val board = Warp.extract(pixels, w, h, H)
        lastBoard = board
        val det = DiscDetector.detect(board)
        val state = GameState(
            det.discs,
            if (AssistApp.prefs.myColorIsWhite) DiscType.WHITE else DiscType.BLACK,
            AssistApp.prefs.ourBaselineBottom
        )
        lastState = state

        val striker = state.discs.getOrNull(state.strikerIndex)
        if (striker == null) { status = "No striker detected"; return }

        val shot = solver.solve(state, AssistApp.prefs.chaseQueen, true)
        lastShot = shot
        if (shot == null) { status = "No shot found"; overlay?.set(state, null, H, params); return }
        status = "aim %.1f°  power %d%%  score %.0f".format(
            Math.toDegrees(kotlin.math.atan2(shot.aimDir.y.toDouble(), shot.aimDir.x.toDouble())),
            (shot.power * 100).toInt(), shot.score
        )
        overlay?.set(state, shot, H, params)

        // ---- autoplay ----
        if (AssistApp.prefs.autoShoot && CarromAccessibilityService.connected &&
            SystemClock.uptimeMillis() - lastShotAt > 2600) {
            val strikerOnOurBase = if (AssistApp.prefs.ourBaselineBottom)
                striker.pos.y > BoardSpec.PLAY * 0.5f else striker.pos.y < BoardSpec.PLAY * 0.5f
            if (strikerOnOurBase) {
                fire(shot, H)
                lastShotAt = SystemClock.uptimeMillis()
            }
        }
    }

    private fun fire(shot: Solver.Shot, screenToBoard: FloatArray) {
        val boardToScreen = Homography.invert(screenToBoard)
        val p0 = Homography.apply(boardToScreen, shot.strikerPos)
        // map the aim vector into screen space using two board points
        val p1 = Homography.apply(boardToScreen, shot.strikerPos + shot.aimDir * 120f)
        var dir = (p1 - p0).norm()
        if (AssistApp.prefs.invertDrag) dir = -dir

        val maxDrag = AssistApp.prefs.maxDragPx * resources.displayMetrics.heightPixels
        val len = maxDrag * shot.power.coerceIn(0.08f, 1f)
        val end = p0 - dir * len

        CarromAccessibilityService.instance?.swipe(p0.x, p0.y, end.x, end.y, 210)
    }

    // ---------------- lifecycle ----------------

    override fun onDestroy() {
        running.set(false)
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        removeOverlays()
        vd?.release(); vd = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        instance = null
        super.onDestroy()
    }
}
