package com.deon.choplight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service that listens to the accelerometer and toggles the
 * flashlight on a chop-chop gesture. Declared as specialUse FGS type
 * (targetSdk 34 requirement).
 */
class ChopDetectionService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "com.deon.choplight.START"
        const val ACTION_STOP = "com.deon.choplight.STOP"
        const val ACTION_TOGGLE_TORCH = "com.deon.choplight.TOGGLE_TORCH"
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "choplight_listen"

        @Volatile var isRunning = false
    }

    private lateinit var prefs: Prefs
    private lateinit var detector: ChopDetector
    private lateinit var gestureHandler: ChopGestureHandler
    private var sensorManager: SensorManager? = null
    private var sensorThread: HandlerThread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sensitivity") detector.peakThreshold = prefs.peakThreshold
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        detector = ChopDetector(peakThreshold = prefs.peakThreshold)
        gestureHandler = ChopGestureHandler(
            detector = detector,
            mode = { prefs.gestureMode },
            doToggle = { TorchController.toggle(this) },
        )
        prefs.registerListener(prefsListener)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_TORCH -> {
                TorchController.toggle(this)
                return START_STICKY
            }
            ACTION_STOP -> {
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // ACTION_START (or null after process death): start listening.
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }

        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChopLight::listen")
                .apply { setReferenceCounted(false) }
        }
        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        } catch (_: SecurityException) { /* WAKE_LOCK missing; keep going */ }

        if (sensorThread == null) {
            sensorThread = HandlerThread("chop-sensor").also { it.start() }
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager?.registerListener(
                    this, accel, SensorManager.SENSOR_DELAY_GAME,
                    sensorThread!!.looper.let { android.os.Handler(it) }
                )
            }
        }
        isRunning = true
    }

    private fun stopListening() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) { }
        sensorManager = null
        sensorThread?.quitSafely()
        sensorThread = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopListening()
        try {
            prefs.unregisterListener(prefsListener)
        } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        val v = event.values
        gestureHandler.onSample(v[0], v[1], v[2], now)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "ChopLight listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while ChopLight listens for the chop-chop gesture." }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, ChopDetectionService::class.java).setAction(ACTION_TOGGLE_TORCH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_torch)
            .setContentTitle("ChopLight")
            .setContentText("Listening for chop-chop")
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(null, "Toggle torch now", toggle).build()
            )
            .setOngoing(true)
            .build()
    }
}
