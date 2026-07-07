package com.nishparadox.smriti.notes

enum class SnipStatus { RECORDING, TRANSCRIBING, DONE, FAILED }

/** One captured snip and its lifecycle state (the queue item shown on the home screen). */
data class Snip(
    val id: Long,
    val createdAt: Long,
    val status: SnipStatus,
    val text: String = "",
    val source: String = "",
    val durationS: Int = 0,
    /** Empty = created on this device (this phone writes it to Drive); non-empty = pulled from that device. */
    val device: String = "",
)
