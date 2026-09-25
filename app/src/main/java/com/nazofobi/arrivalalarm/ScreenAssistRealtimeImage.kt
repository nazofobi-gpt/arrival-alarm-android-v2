package com.nazofobi.arrivalalarm

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class ScreenAssistRealtimeImage(private val maxFrameBytes: Int = 512_000) {
    init { require(maxFrameBytes in 1..2_000_000) }

    fun buildConversationItem(jpeg: ByteArray): String {
        require(jpeg.isNotEmpty()) { "Empty frame" }
        require(jpeg.size <= maxFrameBytes) { "Frame exceeds bounded transport size" }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray().put(JSONObject().put("type", "input_image").put("image_url", dataUrl))
        val item = JSONObject().put("type", "message").put("role", "user").put("content", content)
        return JSONObject().put("type", "conversation.item.create").put("item", item).toString()
    }
}
