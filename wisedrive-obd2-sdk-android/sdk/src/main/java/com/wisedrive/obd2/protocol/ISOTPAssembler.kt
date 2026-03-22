package com.wisedrive.obd2.protocol

import com.wisedrive.obd2.util.Logger

/**
 * ISO-TP (ISO 15765-2) Multi-frame message assembler
 * Handles reassembly of multi-frame CAN messages
 */
object ISOTPAssembler {

    private const val TAG = "ISOTPAssembler"

    // Frame type constants
    private const val FRAME_TYPE_SINGLE = 0x00
    private const val FRAME_TYPE_FIRST = 0x10
    private const val FRAME_TYPE_CONSECUTIVE = 0x20
    private const val FRAME_TYPE_FLOW_CONTROL = 0x30

    /**
     * Reassemble multi-frame ISO-TP message
     */
    fun reassemble(rawResponse: String): ByteArray {
        val frames = parseFrames(rawResponse)
        
        if (frames.isEmpty()) {
            return ByteArray(0)
        }
        
        // Check if single frame
        val firstFrame = frames.first()
        val firstByte = firstFrame.firstOrNull() ?: return ByteArray(0)
        val frameType = firstByte and 0xF0
        
        when (frameType) {
            FRAME_TYPE_SINGLE -> {
                // Single frame: length in lower nibble
                val length = firstByte and 0x0F
                return firstFrame.drop(1).take(length).map { it.toByte() }.toByteArray()
            }
            FRAME_TYPE_FIRST -> {
                // First frame of multi-frame message
                return reassembleMultiFrame(frames)
            }
            else -> {
                // Unknown or not ISO-TP formatted
                return frames.flatten().map { it.toByte() }.toByteArray()
            }
        }
    }

    /**
     * Reassemble multi-frame message
     */
    private fun reassembleMultiFrame(frames: List<List<Int>>): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        
        val result = mutableListOf<Int>()
        var expectedSequence = 1
        
        for ((index, frame) in frames.withIndex()) {
            if (frame.isEmpty()) continue
            
            val firstByte = frame[0]
            val frameType = firstByte and 0xF0
            
            when (frameType) {
                FRAME_TYPE_FIRST -> {
                    // First frame: 12-bit length in first 2 bytes
                    if (frame.size < 2) continue
                    
                    val length = ((firstByte and 0x0F) shl 8) or frame[1]
                    Logger.d(TAG, "First frame, total length: $length bytes")
                    
                    // Data starts at byte 2
                    result.addAll(frame.drop(2))
                }
                FRAME_TYPE_CONSECUTIVE -> {
                    // Consecutive frame: sequence number in lower nibble
                    val sequence = firstByte and 0x0F
                    
                    if (sequence == expectedSequence % 16) {
                        // Add data (skip header byte)
                        result.addAll(frame.drop(1))
                        expectedSequence++
                    } else {
                        Logger.w(TAG, "Out of sequence: expected $expectedSequence, got $sequence")
                    }
                }
                else -> {
                    // Add raw data
                    result.addAll(frame)
                }
            }
        }
        
        return result.map { it.toByte() }.toByteArray()
    }

    /**
     * Parse response into frames
     */
    private fun parseFrames(rawResponse: String): List<List<Int>> {
        val frames = mutableListOf<List<Int>>()
        
        val lines = rawResponse
            .replace("SEARCHING...", "")
            .replace("BUS INIT...", "")
            .replace(">", "")
            .split("\r", "\n")
            .filter { it.isNotBlank() }
        
        for (line in lines) {
            val tokens = line.trim().split("\\s+".toRegex())
            val bytes = mutableListOf<Int>()
            
            for (token in tokens) {
                // Skip ECU headers (3+ chars like "7E8")
                if (token.length == 2) {
                    token.toIntOrNull(16)?.let { bytes.add(it) }
                }
            }
            
            if (bytes.isNotEmpty()) {
                frames.add(bytes)
            }
        }
        
        return frames
    }

    /**
     * Check if response is multi-frame
     */
    fun isMultiFrame(rawResponse: String): Boolean {
        val cleaned = rawResponse
            .replace("SEARCHING...", "")
            .replace(">", "")
            .trim()
        
        // Look for first frame indicator (10 XX)
        val tokens = cleaned.split("\\s+".toRegex())
        for (token in tokens) {
            val byte = token.toIntOrNull(16) ?: continue
            if ((byte and 0xF0) == FRAME_TYPE_FIRST) {
                return true
            }
        }
        
        // Also check for consecutive frame markers (21, 22, etc.)
        return cleaned.contains(Regex("\\b2[0-9A-Fa-f]\\b"))
    }

    /**
     * Get total message length from first frame
     */
    fun getMessageLength(rawResponse: String): Int {
        val frames = parseFrames(rawResponse)
        if (frames.isEmpty()) return 0
        
        val firstFrame = frames.first()
        if (firstFrame.size < 2) return 0
        
        val firstByte = firstFrame[0]
        val frameType = firstByte and 0xF0
        
        return when (frameType) {
            FRAME_TYPE_SINGLE -> firstByte and 0x0F
            FRAME_TYPE_FIRST -> ((firstByte and 0x0F) shl 8) or firstFrame[1]
            else -> 0
        }
    }
}
