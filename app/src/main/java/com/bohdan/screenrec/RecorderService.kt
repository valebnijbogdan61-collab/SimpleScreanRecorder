package com.bohdan.screenrec

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import kotlin.math.abs

class RecorderService : Service(), SensorEventListener {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    // --- детектор "телефон відпущено": немає руху 10 секунд ---
    private var lastMove = 0L
    private var lx = 0f; private var ly = 0f; private var lz = 0f
    private val idleCheck = object : Runnable {
        override fun run() {
            if (running && SystemClock.elapsedRealtime() - lastMove > 10_000) stopRecording()
            else handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running || intent == null) return START_NOT_STICKY
        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "Запис", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("done", "Готово", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "rec")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Йде запис екрана").setOngoing(true).build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(1, n, type)

        try { startRecording(code, data, mic) } catch (e: Exception) { stopRecording() }
        return START_NOT_STICKY
    }

    private fun startRecording(code: Int, data: Intent, mic: Boolean) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels / 2 * 2
        val h = dm.heightPixels / 2 * 2

        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "rec_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRec")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
        pfd = contentResolver.openFileDescriptor(uri!!, "w")

        val mr = MediaRecorder(this)
        if (mic) mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setOutputFile(pfd!!.fileDescriptor)
        mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (mic) { mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mr.setAudioEncodingBitRate(128_000); mr.setAudioSamplingRate(44100) }
        mr.setVideoSize(w, h)
        mr.setVideoFrameRate(30)
        mr.setVideoEncodingBitRate(8_000_000)
        mr.setMaxDuration(3_600_000) // ліміт 1 година
        mr.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording()
        }
        mr.prepare()
        recorder = mr

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopRecording() }
        }, handler)
        display = projection!!.createVirtualDisplay(
            "rec", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mr.surface, null, null
        )
        mr.start()
        running = true

        lastMove = SystemClock.elapsedRealtime()
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed(idleCheck, 1000)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val d = abs(e.values[0] - lx) + abs(e.values[1] - ly) + abs(e.values[2] - lz)
        if (d > 0.35f) lastMove = SystemClock.elapsedRealtime()
        lx = e.values[0]; ly = e.values[1]; lz = e.values[2]
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // додаток закрили (змахнули з нещодавніх) — запис зупиняється
    override fun onTaskRemoved(rootIntent: Intent?) { stopRecording() }

    private fun stopRecording() {
        val wasRunning = running
        running = false
        handler.removeCallbacks(idleCheck)
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        display?.release(); display = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        pfd?.close(); pfd = null

        uri?.let {
            val cv = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(it, cv, null, null)
            if (wasRunning) {
                getSharedPreferences("rec", MODE_PRIVATE).edit().putString("last", it.toString()).apply()
                notifyDone()
            }
        }
        uri = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // після запису повертаємо в додаток
    private fun notifyDone() {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, "done")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Запис збережено ✅")
            .setContentText("Натисни, щоб відкрити CapCut")
            .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
        try { startActivity(open) } catch (_: Exception) {}
    }
}
