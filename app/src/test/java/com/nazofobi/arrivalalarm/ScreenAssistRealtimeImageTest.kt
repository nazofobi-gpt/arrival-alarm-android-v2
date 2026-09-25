package com.nazofobi.arrivalalarm

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistRealtimeImageTest {
    private fun subject(maxFrameBytes: Int) = ScreenAssistRealtimeImage(
        maxFrameBytes = maxFrameBytes,
        base64Encoder = { Base64.getEncoder().encodeToString(it) }
    )

    @Test fun emitsRealtimeImageConversationItem() {
        val json = subject(maxFrameBytes = 16).buildConversationItem(byteArrayOf(1, 2, 3))
        val root = JSONObject(json)
        assertEquals("conversation.item.create", root.getString("type"))
        val item = root.getJSONObject("item")
        assertEquals("user", item.getString("role"))
        val image = item.getJSONArray("content").getJSONObject(0)
        assertEquals("input_image", image.getString("type"))
        assertTrue(image.getString("image_url").startsWith("data:image/jpeg;base64,"))
    }

    @Test fun emitsExplicitGuidanceResponseRequest() {
        val json = subject(maxFrameBytes = 16).buildGuidanceResponseRequest()
        val root = JSONObject(json)
        assertEquals("response.create", root.getString("type"))
        val response = root.getJSONObject("response")
        assertEquals("text", response.getJSONArray("output_modalities").getString(0))
        assertTrue(response.getString("instructions").contains("latest shared screen"))
    }

    @Test fun rejectsOversizedFrame() {
        var failed = false
        try { subject(2).buildConversationItem(byteArrayOf(1, 2, 3)) }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun rejectsEmptyFrame() {
        var failed = false
        try { subject(16).buildConversationItem(byteArrayOf()) }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
