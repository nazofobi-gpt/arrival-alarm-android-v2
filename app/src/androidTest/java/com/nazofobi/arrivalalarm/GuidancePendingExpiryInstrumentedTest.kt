package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidancePendingExpiryInstrumentedTest {
    private val preferences by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            "guidance_pending_expiry_test",
            Context.MODE_PRIVATE,
        )
    }

    @Before fun setUp() {
        preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test fun stalePendingInstructionIsNotReplayedAfterProcessRecreation() {
        val store = SharedPreferencesGuidanceStateStore(preferences)
        val spoken = mutableListOf<GuidanceUtterance>()
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken += utterance
                return true
            }
        }
        var now = 1_000_000L
        val first = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        val stop = GuidanceUtterance("stop:lo", "Sıradaki durak Lohne", "tr-TR")
        assertFalse(first.requestGuidance(stop))
        assertEquals(now, store.load()?.pendingSinceEpochMillis)

        now += GuidanceReliabilityController.MAX_PENDING_AGE_MILLIS + 1L
        val recreated = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))

        assertTrue(spoken.isEmpty())
        assertNull(recreated.state.pending)
        assertNull(store.load()?.pendingSinceEpochMillis)
        assertFalse(preferences.contains("pending_key"))
        assertFalse(preferences.contains("pending_since"))
    }

    @Test fun shortInterruptionRestoresPendingInstructionExactlyOnce() {
        val store = SharedPreferencesGuidanceStateStore(preferences)
        val spoken = mutableListOf<GuidanceUtterance>()
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean {
                spoken += utterance
                return true
            }
        }
        var now = 1_000_000L
        val first = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        first.onPermissions(GuidancePermissionSnapshot(false, false, false))
        val stop = GuidanceUtterance("stop:ach", "Sıradaki durak Achim", "tr-TR")
        assertFalse(first.requestGuidance(stop))

        now += 20_000L
        val recreated = GuidanceReliabilityController(speaker, store, nowEpochMillis = { now })
        recreated.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertEquals(listOf(stop), spoken)
        assertNull(store.load()?.pending)
        assertFalse(recreated.requestGuidance(stop))
        assertEquals(1, spoken.size)
    }

    @Test fun legacyPendingWithoutRecordedAgeIsDiscarded() {
        preferences.edit()
            .putString("permission", GuidancePermissionState.DENIED.name)
            .putString("pending_key", "stop:legacy")
            .putString("pending_text", "Sıradaki durak eski")
            .putString("pending_locale", "tr-TR")
            .commit()
        val store = SharedPreferencesGuidanceStateStore(preferences)
        val speaker = object : GuidanceSpeaker {
            override fun speak(utterance: GuidanceUtterance): Boolean =
                error("Legacy pending guidance must not be spoken")
        }
        val controller = GuidanceReliabilityController(speaker, store, nowEpochMillis = { 1_000_000L })
        controller.onPermissions(GuidancePermissionSnapshot(true, true, true))
        assertNull(controller.state.pending)
        assertFalse(preferences.contains("pending_key"))
    }
}
