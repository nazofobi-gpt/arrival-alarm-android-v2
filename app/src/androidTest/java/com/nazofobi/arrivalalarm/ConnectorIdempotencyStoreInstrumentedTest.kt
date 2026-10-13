package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectorIdempotencyStoreInstrumentedTest {
    @Test fun sharedPreferencesJournalSurvivesStoreRecreationOnApi36() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(
            "connector_idempotency_g153_test",
            Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()

        val firstStore = SharedPreferencesConnectorIdempotencyStore(prefs)
        firstStore.begin(
            ConnectorIdempotencyEntry(
                idempotencyKey = "command-persisted",
                commandType = "ArmArrivalAlarm",
                stateVersionBefore = 7,
            )
        )
        firstStore.complete(
            ConnectorCommandReceipt(
                idempotencyKey = "command-persisted",
                commandType = "ArmArrivalAlarm",
                status = ConnectorCommandStatus.APPLIED,
                message = "armed",
                stateVersionBefore = 7,
                stateVersionAfter = 8,
                actionId = "action-1",
            )
        )

        val recreatedStore = SharedPreferencesConnectorIdempotencyStore(prefs)
        val completed = recreatedStore.get("command-persisted")
        assertEquals(ConnectorCommandStatus.APPLIED, completed?.receipt?.status)
        assertEquals("action-1", completed?.receipt?.actionId)

        recreatedStore.begin(
            ConnectorIdempotencyEntry(
                idempotencyKey = "command-in-flight",
                commandType = "SetDestination",
                stateVersionBefore = 8,
            )
        )

        val afterSecondRecreation = SharedPreferencesConnectorIdempotencyStore(prefs)
        val inFlight = afterSecondRecreation.get("command-in-flight")
        assertEquals("SetDestination", inFlight?.commandType)
        assertNull(inFlight?.receipt)

        prefs.edit().clear().commit()
    }

    @Test fun corruptJournalMustNotBecomeAnEmptyJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(
            "connector_idempotency_g191_corruption_test",
            Context.MODE_PRIVATE,
        )
        val key = "connector_idempotency_v1"
        val validClaim = """{"idempotency_key":"already-claimed","command_type":"ArmArrivalAlarm","state_version_before":7}"""
        val corruptPayloads = listOf(
            "{not-json",
            "[null]",
            """[{"idempotency_key":"already-claimed"}]""",
            "[$validClaim,$validClaim]",
            """[{"idempotency_key":"already-claimed","command_type":"ArmArrivalAlarm","state_version_before":7,"receipt":{"idempotency_key":"different","command_type":"ArmArrivalAlarm","status":"APPLIED","message":"ok","state_version_before":7,"state_version_after":8}}]""",
        )

        try {
            for (payload in corruptPayloads) {
                assertTrue(prefs.edit().putString(key, payload).commit())
                val store = SharedPreferencesConnectorIdempotencyStore(prefs)
                val readFailure = runCatching { store.get("already-claimed") }.exceptionOrNull()
                assertTrue("Corrupted claims must not be discarded", readFailure is IllegalStateException)
                val writeFailure = runCatching {
                    store.begin(
                        ConnectorIdempotencyEntry(
                            idempotencyKey = "already-claimed",
                            commandType = "ArmArrivalAlarm",
                            stateVersionBefore = 7,
                        )
                    )
                }.exceptionOrNull()
                assertTrue("A corrupted journal must reject new claims", writeFailure is IllegalStateException)
                assertEquals("Recovery must not overwrite the journal", payload, prefs.getString(key, null))
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
