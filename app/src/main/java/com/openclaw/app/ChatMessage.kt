package com.openclaw.app

data class ChatMessage(
    val role: String, // "user" | "assistant" | "system"
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
    val audioUrl: String? = null,
    val ttsText: String? = null,
    val imagePath: String? = null,
    val videoPath: String? = null,
    val transcriptText: String? = null,
    val transcriptVisible: Boolean = false,
)
