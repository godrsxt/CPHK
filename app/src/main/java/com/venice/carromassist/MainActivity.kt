package com.venice.carromassist

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.venice.carromassist.capture.AssistService
import com.venice.carromassist.input.CarromAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK && r.data != null) {
                val i = Intent(this, AssistService::class.java)
                    .putExtra(AssistService.EXTRA_CODE, r.resultCode)
                    .putExtra(AssistService.EXTRA_DATA, r.data)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                toast("Capture started")
            } else toast("Capture denied")
            refresh()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnAcc).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) { toast("Grant draw-over-apps first"); return@setOnClickListener }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, AssistService::class.java)); refresh()
        }
        findViewById<Button>(R.id.btnCalib).setOnClickListener {
            if (AssistService.lastFrame == null) { toast("Start capture first"); return@setOnClickListener }
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        findViewById<Button>(R.id.btnTune).setOnClickListener {
            startActivity(Intent(this, TuneActivity::class.java))
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val acc = CarromAccessibilityService.connected
        val run = AssistService.instance != null
        status.text = buildString {
            append("overlay permission: ").append(if (ov) "OK" else "MISSING").append('\n')
            append("gesture service: ").append(if (acc) "OK" else "MISSING").append('\n')
            append("capture: ").append(if (run) "RUNNING" else "stopped").append('\n')
            append("calibration: ").append(if (AssistApp.prefs.homography != null) "saved" else "not set").append('\n')
            if (run) append('\n').append(AssistService.status)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
