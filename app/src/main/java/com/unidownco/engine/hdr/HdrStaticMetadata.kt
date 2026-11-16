package com.unidownco.engine.hdr

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates HDR10 static mastering display metadata and content light level information
 * conforming to CTA-861.3 / CTA-861-G and SMPTE ST 2086.
 *
 * Produces the standardized 25-byte Little-Endian binary buffer required by Android
 * [android.media.MediaFormat.KEY_HDR_STATIC_INFO].
 */
data class HdrStaticMetadata(
    val greenX: Int = 13250, // 0.2650 * 50000
    val greenY: Int = 34500, // 0.6900 * 50000
    val blueX: Int = 7500,   // 0.1500 * 50000
    val blueY: Int = 3000,   // 0.0600 * 50000
    val redX: Int = 34000,   // 0.6800 * 50000
    val redY: Int = 16000,   // 0.3200 * 50000
    val whitePointX: Int = 15635, // 0.3127 * 50000 (D65)
    val whitePointY: Int = 16450, // 0.3290 * 50000 (D65)
    val maxMasteringLuminanceNits: Int = 1000, // 1 to 65535 cd/m2
    val minMasteringLuminanceTenThousandthsNits: Int = 1, // 0.0001 cd/m2
    val maxCllNits: Int = 1000, // Maximum Content Light Level
    val maxFallNits: Int = 400  // Maximum Frame-Average Light Level
) {

    /**
     * Serializes this static metadata into the exact 25-byte CTA-861.3 binary structure
     * expected by Android [android.media.MediaFormat.KEY_HDR_STATIC_INFO].
     */
    fun toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(CTA_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0.toByte()) // Static Metadata Descriptor ID: 0 (Type 1)

        // Primaries in CTA-861 order: Green, Blue, Red
        buffer.putShort(greenX.toShort())
        buffer.putShort(greenY.toShort())
        buffer.putShort(blueX.toShort())
        buffer.putShort(blueY.toShort())
        buffer.putShort(redX.toShort())
        buffer.putShort(redY.toShort())

        // White Point
        buffer.putShort(whitePointX.toShort())
        buffer.putShort(whitePointY.toShort())

        // Mastering Luminance
        buffer.putShort(maxMasteringLuminanceNits.toShort())
        buffer.putShort(minMasteringLuminanceTenThousandthsNits.toShort())

        // Content Light Level
        buffer.putShort(maxCllNits.toShort())
        buffer.putShort(maxFallNits.toShort())

        buffer.rewind()
        return buffer
    }

    companion object {
        const val CTA_BUFFER_SIZE = 25

        /**
         * Standard ITU-R BT.2020 / D65 mastering profile targeting 1000 nits.
         */
        val BT2020_1000NITS = HdrStaticMetadata(
            greenX = 13250,
            greenY = 34500,
            blueX = 7500,
            blueY = 3000,
            redX = 34000,
            redY = 16000,
            whitePointX = 15635,
            whitePointY = 16450,
            maxMasteringLuminanceNits = 1000,
            minMasteringLuminanceTenThousandthsNits = 1,
            maxCllNits = 1000,
            maxFallNits = 400
        )

        /**
         * Parses a CTA-861.3 25-byte [ByteBuffer] back into an [HdrStaticMetadata] instance.
         */
        fun fromByteBuffer(buffer: ByteBuffer): HdrStaticMetadata? {
            if (buffer.remaining() < CTA_BUFFER_SIZE) return null

            val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val id = duplicate.get().toInt()
            if (id != 0) return null // Only Type 1 static descriptor supported

            val gx = duplicate.short.toInt() and 0xFFFF
            val gy = duplicate.short.toInt() and 0xFFFF
            val bx = duplicate.short.toInt() and 0xFFFF
            val by = duplicate.short.toInt() and 0xFFFF
            val rx = duplicate.short.toInt() and 0xFFFF
            val ry = duplicate.short.toInt() and 0xFFFF

            val wx = duplicate.short.toInt() and 0xFFFF
            val wy = duplicate.short.toInt() and 0xFFFF

            val maxL = duplicate.short.toInt() and 0xFFFF
            val minL = duplicate.short.toInt() and 0xFFFF

            val maxCll = duplicate.short.toInt() and 0xFFFF
            val maxFall = duplicate.short.toInt() and 0xFFFF

            return HdrStaticMetadata(
                greenX = gx,
                greenY = gy,
                blueX = bx,
                blueY = by,
                redX = rx,
                redY = ry,
                whitePointX = wx,
                whitePointY = wy,
                maxMasteringLuminanceNits = maxL,
                minMasteringLuminanceTenThousandthsNits = minL,
                maxCllNits = maxCll,
                maxFallNits = maxFall
            )
        }
    }
}
