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

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var btnCapcut: Button
    private lateinit var btnVideo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#0E0E10"))
        root.setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())

        val title = TextView(this)
        title.text = "Simple Screen Recorder"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER

        status = TextView(this)
        status.text = "Готово до запису"
        status.setTextColor(Color.parseColor("#9A9AA0"))
        status.textSize = 15f
        status.gravity = Gravity.CENTER
        status.setPadding(0, (8 * d).toInt(), 0, (32 * d).toInt())

        val btnRec = Button(this)
        btnRec.text = "ПОЧАТИ ЗАПИС"
        btnRec.textSize = 20f
        btnRec.setTextColor(Color.BLACK)
        btnRec.setBackgroundColor(Color.parseColor("#22C55E"))
        btnRec.setOnClickListener { startFlow() }

        btnVideo = Button(this)
        btnVideo.text = "МОЇ ВІДЕО"
        btnVideo.setTextColor(Color.WHITE)
        btnVideo.setBackgroundColor(Color.parseColor("#2A2A30"))
        btnVideo.setOnClickListener { openLast() }

        btnCapcut = Button(this)
        btnCapcut.text = "ВІДКРИТИ CAPCUT"
        btnCapcut.setTextColor(Color.WHITE)
        btnCapcut.setBackgroundColor(Color.parseColor("#2A2A30"))
        btnCapcut.setOnClickListener { openCapCut() }

        root.addView(title)
        root.addView(status)
        root.addView(btnRec, lp((88 * d).toInt(), (12 * d).toInt()))
        root.addView(btnVideo, lp((56 * d).toInt(), (12 * d).toInt()))
        root.addView(btnCapcut, lp((56 * d).toInt(), (12 * d).toInt()))
        setContentView(root)
    }

    private fun lp(h: Int, top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        p.topMargin = top
        return p
    }

    override fun onResume() {
        super.onResume()
        val last = getSharedPreferences("rec", Context.MODE_PRIVATE).getString("last", null)
        val v = if (last != null) View.VISIBLE else View.GONE
        btnCapcut.visibility = v
        btnVideo.visibility = v
        if (last != null) status.text = "✅ Запис збережено в галерею"
    }

    private fun startFlow() {
        val need = ArrayList<String>()
        need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) askProjection() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) askProjection()
    }

    private fun askProjection() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val i = Intent(this, RecorderService::class.java)
                i.putExtra("code", resultCode)
                i.putExtra("data", data)
                startForegroundService(i)
                status.text = "● ЙДЕ ЗАПИС"
                moveTaskToBack(true)
            } else {
                status.text = "Дозвіл не надано"
            }
        }
    }

    private fun openLast() {
        val last = getSharedPreferences("rec", Context.MODE_PRIVATE).getString("last", null) ?: return
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(Uri.parse(last), "video/mp4")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(i)
    }

    private fun openCapCut() {
        val pkg = "com.lemon.lvoverseas"
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)))
        }
    }
}
