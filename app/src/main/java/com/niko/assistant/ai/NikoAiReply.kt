package com.niko.assistant.ai

data class NikoWebSource(val title: String, val url: String)

data class NikoAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<NikoWebSource>,
    val evidence: String = "",
)
