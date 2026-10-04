package com.deon.choplight

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), SensorEventListener {

    private lateinit var prefs: Prefs

    private lateinit var pageHome: View
    private lateinit var pageGestures: View
    private lateinit var pageSettings: View
    private lateinit var navHome: TextView
    private lateinit var navGestures: TextView
    private lateinit var navSettings: TextView

    private lateinit var btnTorch: Button
    private lateinit var tvTorchStatus: TextView
    private lateinit var switchService: Switch
    private var serviceSwitchGuard = false

    private lateinit var seekSensitivity: SeekBar
    private lateinit var tvSensitivityValue: TextView
    private lateinit var motionMeter: MotionMeterView

    private var sensorManager: SensorManager? = null
    private var meterActive = false
    private val meterDetector = ChopDetector()
    private var currentPage = 0

    private val torchListener: () -> Unit = { refreshTorchUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        pageHome = findViewById(R.id.page_home)
        pageGestures = findViewById(R.id.page_gestures)
        pageSettings = findViewById(R.id.page_settings)
        navHome = findViewById(R.id.nav_home)
        navGestures = findViewById(R.id.nav_gestures)
        navSettings = findViewById(R.id.nav_settings)

        btnTorch = findViewById(R.id.btn_torch)
        tvTorchStatus = findViewById(R.id.tv_torch_status)
        switchService = findViewById(R.id.switch_service)
        seekSensitivity = findViewById(R.id.seek_sensitivity)
        tvSensitivityValue = findViewById(R.id.tv_sensitivity_value)
        motionMeter = findViewById(R.id.motion_meter)

        navHome.setOnClickListener { showPage(0) }
        navGestures.setOnClickListener { showPage(1) }
        navSettings.setOnClickListener { showPage(2) }

        btnTorch.setOnClickListener { TorchController.toggle(this) }

        switchService.setOnCheckedChangeListener { _, checked ->
            if (serviceSwitchGuard) return@setOnCheckedChangeListener
            if (checked) startChopService() else stopChopService()
        }

        seekSensitivity.progress = prefs.sensitivity
        updateSensitivityLabel()
        motionMeter.setThreshold(prefs.peakThreshold)
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.sensitivity = progress
                updateSensitivityLabel()
                motionMeter.setThreshold(prefs.peakThreshold)
                meterDetector.peakThreshold = prefs.peakThreshold
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        findViewById<Switch>(R.id.switch_autostart).apply {
            isChecked = prefs.autostart
            setOnCheckedChangeListener { _, checked ->
                prefs.autostart = checked
                Toast.makeText(
                    this@MainActivity,
                    if (checked) "ChopLight will start on boot." else "Autostart off.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<Button>(R.id.btn_battery).setOnClickListener { requestNoBatteryOptimization() }

        findViewById<TextView>(R.id.tv_instagram).setOnClickListener { openInstagram() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        showPage(0)
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        TorchState.addListener(torchListener)
        refreshTorchUi()
        refreshServiceSwitch()
        if (currentPage == 1) startMeter()
    }

    override fun onPause() {
        super.onPause()
        TorchState.removeListener(torchListener)
        stopMeter()
    }

    // ---------- pages ----------

    private fun showPage(index: Int) {
        currentPage = index
        pageHome.visibility = if (index == 0) View.VISIBLE else View.GONE
        pageGestures.visibility = if (index == 1) View.VISIBLE else View.GONE
        pageSettings.visibility = if (index == 2) View.VISIBLE else View.GONE
        styleNav(navHome, index == 0)
        styleNav(navGestures, index == 1)
        styleNav(navSettings, index == 2)
        if (index == 1) startMeter() else stopMeter()
    }

    private fun styleNav(tv: TextView, selected: Boolean) {
        val color = if (selected) Color.parseColor("#FF5449") else Color.parseColor("#888888")
        tv.setTextColor(color)
        tv.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    // ---------- torch UI ----------

    private fun refreshTorchUi() {
        val on = TorchState.isOn
        btnTorch.background = getDrawable(
            if (on) R.drawable.bg_torch_on else R.drawable.bg_torch_off
        )
        btnTorch.text = if (on) "ON" else "OFF"
        btnTorch.setTextColor(if (on) Color.WHITE else Color.parseColor("#888888"))
        tvTorchStatus.text = if (on) "Torch is ON" else "Torch is OFF"
    }

    // ---------- service ----------

    private fun refreshServiceSwitch() {
        serviceSwitchGuard = true
        switchService.isChecked = ChopDetectionService.isRunning
        serviceSwitchGuard = false
    }

    private fun startChopService() {
        val intent = Intent(this, ChopDetectionService::class.java)
            .setAction(ChopDetectionService.ACTION_START)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start listening: ${e.message}", Toast.LENGTH_LONG).show()
            refreshServiceSwitch()
            return
        }
        // isRunning flips async; reflect the intent immediately.
        serviceSwitchGuard = true
        switchService.isChecked = true
        serviceSwitchGuard = false
    }

    private fun stopChopService() {
        val intent = Intent(this, ChopDetectionService::class.java)
            .setAction(ChopDetectionService.ACTION_STOP)
        startService(intent)
        serviceSwitchGuard = true
        switchService.isChecked = false
        serviceSwitchGuard = false
    }

    // ---------- sensitivity ----------

    private fun updateSensitivityLabel() {
        val t = prefs.peakThreshold
        tvSensitivityValue.text = String.format("%.0f m/s^2", t)
    }

    // ---------- live meter ----------

    private fun startMeter() {
        if (meterActive) return
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        meterDetector.peakThreshold = prefs.peakThreshold
        meterDetector.reset()
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        meterActive = true
    }

    private fun stopMeter() {
        if (!meterActive) return
        sensorManager?.unregisterListener(this)
        meterActive = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!meterActive || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val v = event.values
        meterDetector.process(v[0], v[1], v[2], System.currentTimeMillis())
        motionMeter.setLevel(meterDetector.lastLinearMagnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---------- instagram ----------

    private fun openInstagram() {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/deepak.deon"))
        try {
            startActivity(Intent(web).setPackage("com.instagram.android"))
        } catch (e: ActivityNotFoundException) {
            startActivity(web)
        }
    }

    // ---------- permissions / battery ----------

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1001)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 &&
            permissions.contains(Manifest.permission.CAMERA) &&
            grantResults.getOrNull(permissions.indexOf(Manifest.permission.CAMERA))
                != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Camera permission is needed for the flashlight.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNoBatteryOptimization() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Open Settings > Battery and whitelist ChopLight manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
