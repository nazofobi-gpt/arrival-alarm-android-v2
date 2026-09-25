package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistRealtimeImageTest {
    @Test fun emitsRealtimeImageConversationItem() {
        val json = ScreenAssistRealtimeImage(maxFrameBytes = 16).buildConversationItem(byteArrayOf(1, 2, 3))
        val root = JSONObject(json)
        assertEquals("conversation.item.create", root.getString("type"))
        val item = root.getJSONObject("item")
        assertEquals("user", item.getString("role"))
        val image = item.getJSONArray("content").getJSONObject(0)
        assertEquals("input_image", image.getString("type"))
        assertTrue(image.getString("image_url").startsWith("data:image/jpeg;base64,"))
    }

    @Test fun rejectsOversizedFrame() {
        var failed = false
        try {
            ScreenAssistRealtimeImage(maxFrameBytes = 2).buildConversationItem(byteArrayOf(1, 2, 3))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test fun rejectsEmptyFrame() {
        var failed = false
        try {
            ScreenAssistRealtimeImage(maxFrameBytes = 16).buildConversationItem(byteArrayOf())
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
