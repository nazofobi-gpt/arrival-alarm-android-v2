package com.nazofobi.arrivalalarm

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class ScreenAssistRealtimeImage(
    private val maxFrameBytes: Int = 512_000,
    private val base64Encoder: (ByteArray) -> String = { bytes ->
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
) {
    init { require(maxFrameBytes in 1..2_000_000) }

    fun buildConversationItem(jpeg: ByteArray): String {
        require(jpeg.isNotEmpty()) { "Empty frame" }
        require(jpeg.size <= maxFrameBytes) { "Frame exceeds bounded transport size" }
        val dataUrl = "data:image/jpeg;base64," + base64Encoder(jpeg)
        val content = JSONArray().put(JSONObject().put("type", "input_image").put("image_url", dataUrl))
        val item = JSONObject().put("type", "message").put("role", "user").put("content", content)
        return JSONObject().put("type", "conversation.item.create").put("item", item).toString()
    }

    fun buildGuidanceResponseRequest(): String {
        val response = JSONObject()
            .put("output_modalities", JSONArray().put("text"))
            .put(
                "instructions",
                "Inspect the latest shared screen image and give one concise, actionable next step. " +
                    "Do not claim to control the device. If the screen is ambiguous, ask the user to confirm.",
            )
        return JSONObject()
            .put("type", "response.create")
            .put("response", response)
            .toString()
    }
}
