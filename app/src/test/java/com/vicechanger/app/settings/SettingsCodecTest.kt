package com.vicechanger.app.settings

import com.vicechanger.app.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    private fun readerFrom(map: Map<String, String>): (String) -> String? = { key -> map[key] }

    @Test
    fun `defaults are used when nothing is stored`() {
        val settings = SettingsCodec.decode(readerFrom(emptyMap()))
        assertEquals(AppSettings().defaultPresetId, settings.defaultPresetId)
        assertEquals(AppSettings().themeMode, settings.themeMode)
        assertTrue(settings.echoReduction)
        assertTrue(settings.lowLatencyMode)
        assertEquals(-1, settings.preferredInputDeviceId)
    }

    @Test
    fun `encode then decode round trips every field`() {
        val original = AppSettings(
            defaultPresetId = "robot_girl",
            inputGainDb = 6.5f,
            outputVolume = 0.42f,
            noiseSuppression = 0.71f,
            echoReduction = false,
            echoReductionAmountDb = 9f,
            lowLatencyMode = false,
            themeMode = ThemeMode.LIGHT,
            preferredInputDeviceId = 12,
            preferredOutputDeviceId = 34,
            customVoiceDraft = "draft",
        )
        val decoded = SettingsCodec.decode(readerFrom(SettingsCodec.encode(original)))
        assertEquals(original, decoded)
    }

    @Test
    fun `out of range values are clamped on read`() {
        val settings = SettingsCodec.decode(
            readerFrom(
                mapOf(
                    SettingsCodec.KEY_INPUT_GAIN to "999",
                    SettingsCodec.KEY_OUTPUT_VOLUME to "5",
                    SettingsCodec.KEY_NOISE_SUPPRESSION to "-3",
                    SettingsCodec.KEY_ECHO_AMOUNT to "400",
                ),
            ),
        )
        assertEquals(18f, settings.inputGainDb, 0f)
        assertEquals(1f, settings.outputVolume, 0f)
        assertEquals(0f, settings.noiseSuppression, 0f)
        assertEquals(30f, settings.echoReductionAmountDb, 0f)
    }

    @Test
    fun `garbage values fall back to the defaults`() {
        val settings = SettingsCodec.decode(
            readerFrom(
                mapOf(
                    SettingsCodec.KEY_INPUT_GAIN to "not-a-number",
                    SettingsCodec.KEY_THEME to "NEON",
                    SettingsCodec.KEY_LOW_LATENCY to "maybe",
                    SettingsCodec.KEY_OUTPUT_DEVICE to "abc",
                ),
            ),
        )
        assertEquals(AppSettings().inputGainDb, settings.inputGainDb, 0f)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertTrue(settings.lowLatencyMode)
        assertEquals(-1, settings.preferredOutputDeviceId)
    }

    @Test
    fun `the manager persists and exposes changes`() {
        val store = InMemoryKeyValueStore()
        val manager = SettingsManager(store)

        manager.setDefaultPreset("cute_girl")
        manager.setInputGainDb(4f)
        manager.setLowLatencyMode(false)

        assertEquals("cute_girl", manager.current.defaultPresetId)
        assertEquals(4f, manager.current.inputGainDb, 0f)
        assertFalse(manager.current.lowLatencyMode)

        val reloaded = SettingsManager(store)
        assertEquals("cute_girl", reloaded.current.defaultPresetId)
        assertEquals(4f, reloaded.current.inputGainDb, 0f)
        assertFalse(reloaded.current.lowLatencyMode)
    }

    @Test
    fun `theme selection is stored as a name and read back`() {
        val store = InMemoryKeyValueStore()
        val manager = SettingsManager(store)
        manager.setThemeMode(ThemeMode.SYSTEM)
        assertEquals("SYSTEM", store.getString(SettingsCodec.KEY_THEME))
        assertEquals(ThemeMode.SYSTEM, SettingsManager(store).current.themeMode)
    }

    @Test
    fun `device selection round trips through the store`() {
        val store = InMemoryKeyValueStore()
        val manager = SettingsManager(store)
        manager.setPreferredInputDevice(7)
        manager.setPreferredOutputDevice(9)
        val reloaded = SettingsManager(store)
        assertEquals(7, reloaded.current.preferredInputDeviceId)
        assertEquals(9, reloaded.current.preferredOutputDeviceId)
    }
}
