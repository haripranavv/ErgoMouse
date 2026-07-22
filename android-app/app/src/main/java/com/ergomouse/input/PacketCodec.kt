package com.ergomouse.input

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketCodec {
    private var sequenceNumber = 0

    fun encode(intent: InputIntent): ByteArray? {
        val seq = sequenceNumber++ % 65536
        
        return when (intent) {
            is InputIntent.Move -> {
                // Header (6 bytes) + Payload (7 bytes) = 13 bytes
                val buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
                
                // --- HEADER ---
                buffer.put(1.toByte())        // version = 1
                buffer.put(0x10.toByte())     // type = MOVE (0x10)
                buffer.putShort(seq.toShort())// sequence number
                buffer.putShort(0.toShort())  // padding to reach 6-byte header
                
                // --- PAYLOAD ---
                // Convert float pixels to Q8.8 fixed-point for the wire
                buffer.putShort((intent.dx * 256).toInt().toShort()) 
                buffer.putShort((intent.dy * 256).toInt().toShort())
                buffer.putShort(System.currentTimeMillis().rem(65536).toShort()) // timestamp
                
                var flags = 0
                if (intent.precisionMode) flags = flags or 0x01
                if (intent.dragActive) flags = flags or 0x02
                buffer.put(flags.toByte())
                
                buffer.array()
            }
            // For MVP Phase 1, we are just implementing Move.
            // Click and Scroll will follow this exact same pattern!
            else -> null 
        }
    }
}