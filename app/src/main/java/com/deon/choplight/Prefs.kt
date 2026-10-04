package com.deon.choplight

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences wrapper for ChopLight settings. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("choplight", Context.MODE_PRIVATE)

    var autostart: Boolean
        get() = sp.getBoolean("autostart", false)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    /** SeekBar progress 0..100. Default 57 -> ~12 m/s^2 threshold. */
    var sensitivity: Int
        get() = sp.getInt("sensitivity", 57)
        set(v) = sp.edit().putInt("sensitivity", v.coerceIn(0, 100)).apply()

    /** Maps sensitivity 0..100 to peak threshold 20..6 m/s^2. */
    val peakThreshold: Float
        get() = 20f - 14f * (sensitivity / 100f)

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)
}
