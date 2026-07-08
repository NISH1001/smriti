package com.nishparadox.smriti.notes

enum class SnipStatus { RECORDING, TRANSCRIBING, DONE, FAILED }

/**
 * How a smaran was captured — the record's discriminator (see ROADMAP.md schema contract).
 *  - AUDIO   playback heard in a whitelisted app -> Whisper transcript
 *  - VOICE   your own voice recorded -> Whisper transcript
 *  - WEB     text clipped from a web page (carries url + title in [Snip.metadata])
 *  - TEXT    text shared in from another (non-web) app
 *  - NOTE    typed directly in Smriti
 *  - UNKNOWN a type written by a newer/other client we don't recognise (never crash on read)
 */
enum class SmaranType { AUDIO, VOICE, WEB, TEXT, NOTE, UNKNOWN }

/** One captured smaran and its lifecycle state (the queue item shown on the home screen). */
data class Snip(
    val id: Long,
    val createdAt: Long,
    val status: SnipStatus,
    val type: SmaranType = SmaranType.TEXT,
    val text: String = "",
    val source: String = "",
    /** Audio/voice length in seconds; 0 for text-like types. */
    val durationS: Int = 0,
    /** Empty = created on this device (this phone writes it to Drive); non-empty = pulled from that device. */
    val device: String = "",
    /** Open, type-specific provenance (e.g. url, title, app, author). Empty for plain notes. */
    val metadata: Map<String, String> = emptyMap(),
)
