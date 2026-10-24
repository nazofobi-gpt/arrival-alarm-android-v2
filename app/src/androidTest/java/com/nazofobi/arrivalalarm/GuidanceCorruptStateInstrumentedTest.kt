package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidanceCorruptStateInstrumentedTest {
    private val preferences by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            "guidance_corrupt_state_g181_test",
            Context.MODE_PRIVATE,
        )
    }
    private val spoken = mutableListOf<GuidanceUtterance>()
    private val speaker = object : GuidanceSpeaker {
        override fun speak(utterance: GuidanceUtterance): Boolean {
            spoken += utterance
            return true
        }
    }

    @Before fun setUp() {
        preferences.edit().clear().commit()
        spoken.clear()
    }

    @After fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test fun wrongTypedPersistedFieldsRecoverWithSafeDefaults() {
        preferences.edit()
            .putInt("permission", 3)
            .putLong("route", 12L)
            .putBoolean("last_key", true)
            .putInt("status", 7)
            .putLong("pending_key", 42L)
            .putBoolean("pending_text", true)
            .putInt("pending_locale", 1)
            .putString("pending_since", "not a timestamp")
            .commit()

        val store = SharedPreferencesGuidanceStateStore(preferences)
        val restored = store.load()
        assertNotNull(restored)
        assertEquals(GuidancePermissionState.DENIED, restored!!.permissionState)
        assertEquals(GuidanceAudioRoute.DEVICE, restored.audioRoute)
        assertNull(restored.lastSpokenKey)
        assertNull(restored.pending)
        assertNull(restored.pendingSinceEpochMillis)
        assertEquals("İzinler bekleniyor", restored.status)

        val recreated = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 10_000L })
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(spoken.isEmpty())
        assertNull(recreated.state.pending)
    }

    @Test fun wrongTypedPendingTimestampNeverReplaysOldStop() {
        preferences.edit()
            .putString("permission", GuidancePermissionState.DENIED.name)
            .putString("pending_key", "stop:old")
            .putString("pending_text", "Sıradaki durak Lohne")
            .putString("pending_locale", "tr-TR")
            .putString("pending_since", "1000")
            .commit()

        val store = SharedPreferencesGuidanceStateStore(preferences)
        assertNull(store.load()?.pendingSinceEpochMillis)
        val controller = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 10_000L })
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(spoken.isEmpty())
        assertNull(controller.state.pending)
        assertFalse(preferences.contains("pending_key"))
        assertFalse(preferences.contains("pending_since"))
    }

    @Test fun blankPendingKeyAndMalformedLocaleCannotBecomeSpeech() {
        preferences.edit()
            .putString("permission", GuidancePermissionState.DENIED.name)
            .putString("pending_key", "  ")
            .putString("pending_text", "Sıradaki durak Bremen")
            .putString("pending_locale", "tr-TR")
            .putLong("pending_since", 1_000L)
            .commit()
        assertNull(SharedPreferencesGuidanceStateStore(preferences).load()?.pending)

        preferences.edit()
            .putString("pending_key", "stop:bremen")
            .putInt("pending_locale", 42)
            .commit()
        val controller = GuidanceReliabilityController(
            speaker,
            SharedPreferencesGuidanceStateStore(preferences),
            nowEpochMillis = { 10_000L },
        )
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertTrue(spoken.isEmpty())
        assertNull(controller.state.pending)
    }
}
