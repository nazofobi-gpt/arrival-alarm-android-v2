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


    @Test fun completionMustMatchClaimAndCannotRewriteReceipt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("connector_idempotency_completion_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = SharedPreferencesConnectorIdempotencyStore(prefs)
            store.begin(ConnectorIdempotencyEntry("cmd-1", "SetDestination", 7))
            val pendingRaw = prefs.getString("connector_idempotency_v1", null)
            val mismatched = ConnectorCommandReceipt(
                idempotencyKey = "cmd-1",
                commandType = "ArmArrivalAlarm",
                status = ConnectorCommandStatus.APPLIED,
                message = "mismatch",
                stateVersionBefore = 7,
                stateVersionAfter = 8,
            )
            try {
                store.complete(mismatched)
                fail("Mismatched command receipt must not be persisted")
            } catch (_: IllegalStateException) { }
            assertEquals(pendingRaw, prefs.getString("connector_idempotency_v1", null))
            assertNull(store.get("cmd-1")?.receipt)

            val valid = mismatched.copy(commandType = "SetDestination", message = "applied")
            store.complete(valid)
            val committedRaw = prefs.getString("connector_idempotency_v1", null)
            assertEquals("SetDestination", store.get("cmd-1")?.receipt?.commandType)
            try {
                store.complete(valid.copy(message = "overwritten"))
                fail("Completed receipt must not be overwritten")
            } catch (_: IllegalStateException) { }
            assertEquals(committedRaw, prefs.getString("connector_idempotency_v1", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test fun inMemoryStoreRejectsMismatchedAndRepeatedCompletion() {
        val store = InMemoryConnectorIdempotencyStore()
        store.begin(ConnectorIdempotencyEntry("cmd-memory", "SetDestination", 7))
        val receipt = ConnectorCommandReceipt(
            idempotencyKey = "cmd-memory",
            commandType = "SetDestination",
            status = ConnectorCommandStatus.APPLIED,
            message = "applied",
            stateVersionBefore = 7,
            stateVersionAfter = 8,
        )
        try {
            store.complete(receipt.copy(stateVersionBefore = 6))
            fail("Mismatched version must not complete an in-memory claim")
        } catch (_: IllegalStateException) { }
        assertNull(store.get("cmd-memory")?.receipt)
        store.complete(receipt)
        assertEquals(receipt, store.get("cmd-memory")?.receipt)
        try {
            store.complete(receipt.copy(message = "overwritten"))
            fail("Completed in-memory receipt must be immutable")
        } catch (_: IllegalStateException) { }
        assertEquals(receipt, store.get("cmd-memory")?.receipt)
    }


    @Test fun malformedClaimsAreRejectedBeforeInMemoryOrDurableMutation() {
        val invalid = listOf(
            ConnectorIdempotencyEntry("", "SetDestination", 7),
            ConnectorIdempotencyEntry("invalid-type", "", 7),
            ConnectorIdempotencyEntry("invalid-version", "SetDestination", -1),
        )
        val inMemory = InMemoryConnectorIdempotencyStore()
        invalid.forEach { claim ->
            try {
                inMemory.begin(claim)
                fail("Invalid in-memory claim must be rejected")
            } catch (_: IllegalStateException) { }
            assertNull(inMemory.get(claim.idempotencyKey))
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("connector_invalid_claim_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val durable = SharedPreferencesConnectorIdempotencyStore(prefs)
            invalid.forEach { claim ->
                try {
                    durable.begin(claim)
                    fail("Invalid durable claim must be rejected before writing")
                } catch (_: IllegalStateException) { }
                assertNull(prefs.getString("connector_idempotency_v1", null))
            }
            durable.begin(ConnectorIdempotencyEntry("valid", "SetDestination", 7))
            val restored = SharedPreferencesConnectorIdempotencyStore(prefs)
            assertEquals("SetDestination", restored.get("valid")?.commandType)
            assertNull(restored.get("invalid-type"))
            assertNull(restored.get("invalid-version"))
        } finally {
            prefs.edit().clear().commit()
        }
    }

}
