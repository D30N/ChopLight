package com.deon.choplight

/** Which chop gesture toggles the flashlight. */
enum class GestureMode { SINGLE, DOUBLE }

/**
 * Owns the chop-gesture toggle decision. Pure Kotlin (no Android APIs) so the
 * exact logic the service runs can be unit-tested on the JVM.
 *
 * - SINGLE: one accepted peak (above threshold, past the refractory period)
 *   toggles the torch.
 * - DOUBLE: two peaks inside the detector's pair window toggle the torch;
 *   a lone single peak does nothing.
 * - After any toggle, [ChopDetector.cooldownMs] must elapse before the next
 *   toggle. The mode is read live via [mode], so switching modes takes
 *   effect immediately.
 */
class ChopGestureHandler(
    val detector: ChopDetector = ChopDetector(),
    private val mode: () -> GestureMode,
    private val doToggle: () -> Unit,
) {
    private var lastToggleMs = Long.MIN_VALUE / 2

    init {
        detector.onPeak = { nowMs -> onPeak(nowMs) }
    }

    /** Feed one accelerometer sample; call from the sensor callback. */
    fun onSample(ax: Float, ay: Float, az: Float, nowMs: Long) {
        val pairDone = detector.process(ax, ay, az, nowMs)
        if (mode() == GestureMode.DOUBLE && pairDone &&
            nowMs - lastToggleMs >= detector.cooldownMs
        ) {
            toggle(nowMs)
        }
    }

    private fun onPeak(nowMs: Long) {
        if (mode() == GestureMode.SINGLE && nowMs - lastToggleMs >= detector.cooldownMs) {
            toggle(nowMs)
        }
    }

    private fun toggle(nowMs: Long) {
        doToggle()
        lastToggleMs = nowMs
        detector.noteToggle(nowMs)
    }
}
