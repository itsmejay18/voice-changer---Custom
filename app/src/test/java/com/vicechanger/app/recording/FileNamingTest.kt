package com.vicechanger.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FileNamingTest {

    private val day = LocalDate.of(2026, 10, 8)

    @Test
    fun `file names follow the documented pattern`() {
        assertEquals("ViceChanger_2026-10-08_001.wav", AudioFileStore.buildFileName(day, 1))
        assertEquals("ViceChanger_2026-10-08_012.wav", AudioFileStore.buildFileName(day, 12))
        assertEquals("ViceChanger_2026-10-08_999.wav", AudioFileStore.buildFileName(day, 999))
    }

    @Test
    fun `the next index starts at one for an empty folder`() {
        assertEquals(1, AudioFileStore.nextIndex(emptyList(), day))
    }

    @Test
    fun `the next index continues from the highest used today`() {
        val existing = listOf(
            "ViceChanger_2026-10-08_001.wav",
            "ViceChanger_2026-10-08_007.wav",
            "ViceChanger_2026-10-08_003.wav",
        )
        assertEquals(8, AudioFileStore.nextIndex(existing, day))
    }

    @Test
    fun `files from other days and other apps are ignored`() {
        val existing = listOf(
            "ViceChanger_2026-10-07_045.wav",
            "ViceChanger_2026-10-09_045.wav",
            "OtherApp_2026-10-08_099.wav",
            "recording.wav",
            "ViceChanger_2026-10-08_note.txt",
        )
        assertEquals(1, AudioFileStore.nextIndex(existing, day))
    }

    @Test
    fun `malformed names do not break numbering`() {
        val existing = listOf(
            "ViceChanger_2026-10-08_abc.wav",
            "ViceChanger_2026-10-08_999.wav",
            "ViceChanger_2026-10-08_1000.wav",
        )
        assertEquals(1000, AudioFileStore.nextIndex(existing, day))
    }

    @Test
    fun `media folder and mime type are the documented values`() {
        assertEquals("VICECHANGER", AudioFileStore.MEDIA_FOLDER)
        assertEquals("audio/x-wav", AudioFileStore.MIME_WAV)
        assertEquals("ViceChanger", AudioFileStore.FILE_PREFIX)
    }
}
