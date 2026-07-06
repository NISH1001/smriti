package com.nishparadox.smriti.capture

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes 16-bit mono PCM to a canonical .wav file. */
object WavWriter {
    fun write(pcm: ShortArray, sampleRate: Int, file: File) {
        val dataBytes = pcm.size * 2
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeBytes("RIFF"); writeIntLE(out, 36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); writeIntLE(out, 16)
            writeShortLE(out, 1); writeShortLE(out, 1)                 // PCM, mono
            writeIntLE(out, sampleRate); writeIntLE(out, sampleRate * 2)
            writeShortLE(out, 2); writeShortLE(out, 16)                // block align, bits
            out.writeBytes("data"); writeIntLE(out, dataBytes)
            val bb = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) bb.putShort(s)
            out.write(bb.array())
        }
    }
    private fun writeIntLE(o: DataOutputStream, v: Int) {
        o.writeByte(v); o.writeByte(v shr 8); o.writeByte(v shr 16); o.writeByte(v shr 24)
    }
    private fun writeShortLE(o: DataOutputStream, v: Int) { o.writeByte(v); o.writeByte(v shr 8) }
}
