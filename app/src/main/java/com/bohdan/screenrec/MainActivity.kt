package com.bohdan.screenrec

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var btnCapcut: Button
    private lateinit var btnVideo: Button

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK && r.data != null) {
                val i = Intent(this, RecorderService::class.java)
                    .putExtra("code", r.resultCode)
                    .putExtra("data", r.data)
                startForegroundService(i)
                status.text = "● ЙДЕ ЗАПИС"
                moveTaskToBack(true) // згортаємо — запис іде далі
            } else status.text = "Дозвіл не надано"
        }

    private val permsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E0E10"))
            setPadding(px(24), px(24), px(24), px(24))
        }
        val title = TextView(this).apply {
            text = "Simple Screen Recorder"
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Готово до запису"
            setTextColor(Color.parseColor("#9A9AA0")); textSize = 15f
            gravity = Gravity.CENTER; setPadding(0, px(8), 0, px(32))
        }
        val btnRec = Button(this).apply {
            text = "ПОЧАТИ ЗАПИС"; textSize = 20f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#22C55E"))
            setOnClickListener { startFlow() }
        }
        btnCapcut = Button(this).apply {
            text = "ВІДКРИТИ CAPCUT"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A30"))
            setOnClickListener { openCapCut() }
        }
        btnVideo = Button(this).apply {
            text = "МОЇ ВІДЕО"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A30"))
            setOnClickListener { openLast() }
        }
        val lp = { h: Int -> LinearLayout.LayoutParams(-1, px(h)).apply { topMargin = px(12) } }
        root.addView(title); root.addView(status)
        root.addView(btnRec, lp(88)); root.addView(btnVideo, lp(56)); root.addView(btnCapcut, lp(56))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val last = getSharedPreferences("rec", MODE_PRIVATE).getString("last", null)
        val v = if (last != null) View.VISIBLE else View.GONE
        btnCapcut.visibility = v; btnVideo.visibility = v
        if (last != null) status.text = "✅ Запис збережено в галерею"
    }

    private fun startFlow() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } else permsLauncher.launch(missing.toTypedArray())
    }

    private fun openLast() {
        val last = getSharedPreferences("rec", MODE_PRIVATE).getString("last", null) ?: return
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(last), "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun openCapCut() {
        val pkg = "com.lemon.lvoverseas"
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
    }
}
