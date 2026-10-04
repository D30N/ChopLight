package com.deon.choplight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Single source of truth for the torch state, shared by UI and service. */
object TorchState {
    @Volatile var isOn: Boolean = false
    private val listeners = mutableSetOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) = synchronized(listeners) { listeners.add(l) }
    fun removeListener(l: () -> Unit) = synchronized(listeners) { listeners.remove(l) }

    fun notifyChanged() {
        val copy: List<() -> Unit> = synchronized(listeners) { listeners.toList() }
        mainHandler.post { copy.forEach { it.invoke() } }
    }
}

/** CameraManager torch wrapper with graceful error handling. */
object TorchController {

    private fun cameraIdWithFlash(context: Context): String? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        }
    }

    private fun toastOnMain(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun setTorch(context: Context, on: Boolean) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cameraIdWithFlash(context)
        if (id == null) {
            toastOnMain(context, "No flashlight found on this device.")
            return
        }
        try {
            cm.setTorchMode(id, on)
            TorchState.isOn = on
            TorchState.notifyChanged()
        } catch (e: SecurityException) {
            toastOnMain(context, "Camera permission needed for the flashlight.")
        } catch (e: CameraAccessException) {
            toastOnMain(context, "Camera is busy right now. Try again.")
        } catch (e: IllegalArgumentException) {
            toastOnMain(context, "Flashlight not available.")
        }
    }

    fun toggle(context: Context) = setTorch(context, !TorchState.isOn)
}
