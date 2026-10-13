package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
    @Test fun malformedDurableJournalCannotAuthorizeNewCommand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("connector_idempotency_corruption_test", Context.MODE_PRIVATE)
        val raw = """[{"idempotency_key":"pending","command_type":"ArmArrivalAlarm","state_version_before":7},{}]"""
        try {
            assertEquals(true, prefs.edit().putString("connector_idempotency_v1", raw).commit())
            val store = SharedPreferencesConnectorIdempotencyStore(prefs)
            try {
                store.get("pending")
                fail("Unreadable journal must block lookup")
            } catch (_: IllegalStateException) { }
            try {
                store.begin(ConnectorIdempotencyEntry("new", "SetDestination", 8))
                fail("Unreadable journal must block any new claim")
            } catch (_: IllegalStateException) { }
            assertEquals(raw, prefs.getString("connector_idempotency_v1", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun duplicateClaimIdsCannotBeSilentlyDeduplicated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("connector_idempotency_duplicate_test", Context.MODE_PRIVATE)
        val claim = """{"idempotency_key":"pending","command_type":"SetDestination","state_version_before":7}"""
        val raw = "[$claim,$claim]"
        try {
            assertEquals(true, prefs.edit().putString("connector_idempotency_v1", raw).commit())
            val store = SharedPreferencesConnectorIdempotencyStore(prefs)
            try {
                store.get("pending")
                fail("Duplicate durable claims must fail closed")
            } catch (_: IllegalStateException) { }
            assertEquals(raw, prefs.getString("connector_idempotency_v1", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun mismatchedReceiptCannotAuthorizeReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("connector_idempotency_receipt_test", Context.MODE_PRIVATE)
        val raw = """[{"idempotency_key":"pending","command_type":"SetDestination","state_version_before":7,"receipt":{"idempotency_key":"another","command_type":"SetDestination","status":"APPLIED","message":"done","state_version_before":7,"state_version_after":8}}]"""
        try {
            assertEquals(true, prefs.edit().putString("connector_idempotency_v1", raw).commit())
            val store = SharedPreferencesConnectorIdempotencyStore(prefs)
            try {
                store.get("pending")
                fail("Mismatched receipt must fail closed")
            } catch (_: IllegalStateException) { }
            assertEquals(raw, prefs.getString("connector_idempotency_v1", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }

}
