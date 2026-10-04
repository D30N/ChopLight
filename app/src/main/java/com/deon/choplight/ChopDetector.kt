package com.deon.choplight

import kotlin.math.sqrt

/**
 * Pure-Kotlin chop-chop gesture detector. No Android dependencies, so it can be
 * unit-tested on the JVM.
 *
 * Pipeline: raw accelerometer -> low-pass gravity estimate -> linear
 * acceleration magnitude -> peak detection -> peak-pair timing -> trigger.
 */
class ChopDetector(
    /** Linear-accel magnitude (m/s^2) a motion peak must exceed. */
    var peakThreshold: Float = DEFAULT_THRESHOLD,
    var minPairGapMs: Long = 300,
    var maxPairGapMs: Long = 900,
    var cooldownMs: Long = 2500,
    var refractoryMs: Long = 150
) {
    companion object {
        const val DEFAULT_THRESHOLD = 12f
        /** Gravity low-pass filter constant. */
        const val GRAVITY_ALPHA = 0.8f
    }

    private val gravity = FloatArray(3)
    private var prevMag = 0f
    private var rising = false
    private var lastPeakTime = Long.MIN_VALUE / 2
    private var firstPeakTime = -1L
    private var lastTriggerTime = Long.MIN_VALUE / 2

    /** Magnitude of the most recent linear-acceleration sample (for the live meter). */
    var lastLinearMagnitude = 0f
        private set

    fun reset() {
        gravity[0] = 0f; gravity[1] = 0f; gravity[2] = 0f
        prevMag = 0f
        rising = false
        lastPeakTime = Long.MIN_VALUE / 2
        firstPeakTime = -1L
        lastTriggerTime = Long.MIN_VALUE / 2
        lastLinearMagnitude = 0f
    }

    /**
     * Feed one accelerometer sample. Returns true exactly when a chop-chop
     * pair completes (two peaks inside [minPairGapMs, maxPairGapMs]).
     */
    fun process(ax: Float, ay: Float, az: Float, nowMs: Long): Boolean {
        // Low-pass filter -> gravity estimate; subtract -> linear acceleration.
        gravity[0] = GRAVITY_ALPHA * gravity[0] + (1 - GRAVITY_ALPHA) * ax
        gravity[1] = GRAVITY_ALPHA * gravity[1] + (1 - GRAVITY_ALPHA) * ay
        gravity[2] = GRAVITY_ALPHA * gravity[2] + (1 - GRAVITY_ALPHA) * az
        val lx = ax - gravity[0]
        val ly = ay - gravity[1]
        val lz = az - gravity[2]
        val mag = sqrt(lx * lx + ly * ly + lz * lz)
        lastLinearMagnitude = mag

        // A stale first peak can never pair anymore.
        if (firstPeakTime >= 0 && nowMs - firstPeakTime > maxPairGapMs) {
            firstPeakTime = -1L
        }

        var triggered = false
        if (mag > peakThreshold && mag >= prevMag) {
            rising = true
        } else if (rising && mag < prevMag) {
            // prevMag was the peak value.
            rising = false
            if (nowMs - lastPeakTime >= refractoryMs) {
                lastPeakTime = nowMs
                if (firstPeakTime < 0) {
                    firstPeakTime = nowMs
                } else {
                    val gap = nowMs - firstPeakTime
                    firstPeakTime = nowMs // slide the window for a possible third chop
                    if (gap in minPairGapMs..maxPairGapMs &&
                        nowMs - lastTriggerTime >= cooldownMs
                    ) {
                        lastTriggerTime = nowMs
                        firstPeakTime = -1L
                        triggered = true
                    }
                }
            }
        }
        prevMag = mag
        return triggered
    }
}
