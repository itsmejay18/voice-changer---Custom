package com.vicechanger.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches the platform's audio devices and republishes them as plain data for the UI.
 *
 * This is what makes "Bluetooth headset changes" a handled case rather than a crash: when a
 * headset connects or disconnects, the snapshot changes, the settings screen re-renders and
 * the engine can be pointed at the new device while it runs.
 */
class AudioDeviceMonitor(private val context: Context) {

    data class Entry(
        val id: Int,
        val label: String,
        val type: Int,
        val isSource: Boolean,
        val isBluetooth: Boolean,
        val isWired: Boolean,
        val isBuiltIn: Boolean,
    )

    data class Snapshot(
        val inputs: List<Entry> = emptyList(),
        val outputs: List<Entry> = emptyList(),
        val hasHeadset: Boolean = false,
        val hasBluetooth: Boolean = false,
        val lastChange: String? = null,
    )

    private val manager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(refresh())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val names = addedDevices?.joinToString { it.productName?.toString() ?: "device" } ?: "device"
            _state.value = refresh().copy(lastChange = "Connected: $names")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val names = removedDevices?.joinToString { it.productName?.toString() ?: "device" } ?: "device"
            _state.value = refresh().copy(lastChange = "Disconnected: $names")
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        runCatching {
            manager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { manager?.unregisterAudioDeviceCallback(callback) }
        registered = false
    }

    fun refresh(): Snapshot {
        val audio = manager ?: return Snapshot()
        val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.toEntry(true) }
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toEntry(false) }
        val headset = outputs.any { it.isBluetooth || it.isWired } || inputs.any { it.isBluetooth }
        return Snapshot(
            inputs = inputs,
            outputs = outputs,
            hasHeadset = headset,
            hasBluetooth = (inputs + outputs).any { it.isBluetooth },
        )
    }

    fun labelFor(deviceId: Int): String? =
        (state.value.inputs + state.value.outputs).firstOrNull { it.id == deviceId }?.label

    private fun AudioDeviceInfo.toEntry(source: Boolean): Entry = Entry(
        id = id,
        label = if (source) AudioInput.describe(this) else AudioOutput.describe(this),
        type = type,
        isSource = source,
        isBluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            // TYPE_BLE_HEADSET exists from API 31; the constant is inlined at compile time, so
            // comparing the raw value keeps this correct on API 29 and 30 as well.
            type == TYPE_BLE_HEADSET_VALUE,
        isWired = type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET,
        isBuiltIn = type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    )

    companion object {
        /** AudioDeviceInfo.TYPE_BLE_HEADSET, available as a constant only from API 31. */
        private const val TYPE_BLE_HEADSET_VALUE = 26
    }
}
