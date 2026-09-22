package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidanceReliabilityInstrumentedTest {
    private class RecordingSpeaker : GuidanceSpeaker {
        val spoken = mutableListOf<GuidanceUtterance>()
        override fun speak(utterance: GuidanceUtterance): Boolean { spoken += utterance; return true }
    }

    @Test fun sharedPreferencesRestoresPendingAcrossProcessStyleRecreationOnApi36() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("guidance_g133_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = SharedPreferencesGuidanceStateStore(prefs)
        val speaker = RecordingSpeaker()

        val first = GuidanceReliabilityController(speaker, store)
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        first.requestGuidance(GuidanceUtterance("stop:api36", "Sıradaki durak Am Brill", "tr-TR"))
        assertEquals("stop:api36", first.state.pending?.key)

        val recreated = GuidanceReliabilityController(speaker, SharedPreferencesGuidanceStateStore(prefs))
        assertEquals("stop:api36", recreated.state.pending?.key)
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals(1, speaker.spoken.size)
        assertEquals("stop:api36", recreated.state.lastSpokenKey)

        val second = GuidanceReliabilityController(speaker, SharedPreferencesGuidanceStateStore(prefs))
        assertFalse(second.requestGuidance(GuidanceUtterance("stop:api36", "Sıradaki durak Am Brill", "tr-TR")))
        assertEquals(1, speaker.spoken.size)
        prefs.edit().clear().commit()
    }
}
