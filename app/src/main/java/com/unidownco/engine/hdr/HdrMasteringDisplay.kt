package com.unidownco.engine.hdr

import com.unidownco.engine.parser.HevcBitstreamParser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates SMPTE ST 2086 mastering display colour volume metadata.
 * Describes the physical optical properties of the reference mastering monitor.
 *
 * Provides distinct, standards-compliant serializations for:
 * 1. Android MediaCodec [android.media.MediaFormat.KEY_HDR_STATIC_INFO] (CTA-861.3 Little-Endian)
 * 2. HEVC Elementary Stream Prefix SEI Payload Type 137 (ITU-T H.265 D.2.27 Big-Endian)
 * 3. MP4 Container 'mdcv' Atom (ISO/IEC 14496-12 / 23008-12 Big-Endian)
 *
 * CRITICAL SCALING DISTINCTION:
 * - In CTA-861.3, Max Mastering Luminance is scaled in units of 1 cd/m2 (16-bit uint).
 * - In HEVC SEI 137 and MP4 'mdcv', Max Mastering Luminance is scaled in units of 0.0001 cd/m2 (32-bit uint).
 */
data class HdrMasteringDisplay(
    val greenX: Int, // Scaled by 50,000 (0.00002 units)
    val greenY: Int,
    val blueX: Int,
    val blueY: Int,
    val redX: Int,
    val redY: Int,
    val whitePointX: Int, // D65 default = 15635 (0.3127 * 50,000)
    val whitePointY: Int, // D65 default = 16450 (0.3290 * 50,000)
    val maxMasteringLuminanceNits: Int, // 1 to 65,535 cd/m2
    val minMasteringLuminanceTenThousandthsNits: Int, // units of 0.0001 cd/m2 (1 = 0.0001 nits)
    val provenance: MetadataProvenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED,
    val profileName: String = "Custom"
) {

    /**
     * Serializes mastering display characteristics into the 20-byte payload for:
     * - HEVC SEI payloadType 137 (Mastering Display Colour Volume, ITU-T H.265 D.2.27)
     * - MP4 container 'mdcv' atom data block (ISO/IEC 14496-12 / 23008-12)
     *
     * Format: Big-Endian, 24 bytes total:
     * - display_primaries_x[0..2], display_primaries_y[0..2] (Green, Blue, Red; 16-bit uint each = 12 bytes)
     * - white_point_x, white_point_y (16-bit uint each = 4 bytes)
     * - max_display_mastering_luminance (32-bit uint in 0.0001 cd/m2 = 4 bytes)
     * - min_display_mastering_luminance (32-bit uint in 0.0001 cd/m2 = 4 bytes)
     */
    fun toRawSei137Payload(): ByteArray {
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
        // Order: c=0 (Green), c=1 (Blue), c=2 (Red)
        buffer.putShort(greenX.toShort())
        buffer.putShort(greenY.toShort())
        buffer.putShort(blueX.toShort())
        buffer.putShort(blueY.toShort())
        buffer.putShort(redX.toShort())
        buffer.putShort(redY.toShort())

        buffer.putShort(whitePointX.toShort())
        buffer.putShort(whitePointY.toShort())

        // Scaled to 0.0001 cd/m2: e.g. 1000 nits -> 10,000,000
        val maxLumTenThousandths = maxMasteringLuminanceNits.toLong() * 10000L
        buffer.putInt((maxLumTenThousandths and 0xFFFFFFFFL).toInt())

        buffer.putInt((minMasteringLuminanceTenThousandthsNits.toLong() and 0xFFFFFFFFL).toInt())
        return buffer.array()
    }

    /**
     * Serializes to full HEVC Prefix SEI NAL unit for Payload Type 137.
     * Header: 4-byte start code (0x00000001) + NAL header 0x4E01 (nal_unit_type 39 = PREFIX_SEI_NUT).
     * Payload header: payloadType = 137 (0x89), payloadSize = 24 (0x18).
     * Total length: 4 + 2 + 2 + 24 = 32 bytes (plus rbsp_trailing_bits if needed).
     */
    fun toHevcSeiNalUnit(): ByteArray {
        val payload = toRawSei137Payload()
        val rawRbsp = ByteBuffer.allocate(2 + payload.size).order(ByteOrder.BIG_ENDIAN)
        rawRbsp.put(137.toByte()) // payloadType = 137
        rawRbsp.put(payload.size.toByte()) // payloadSize = 24
        rawRbsp.put(payload)

        val escapedRbsp = HevcBitstreamParser.insertEmulationPrevention(rawRbsp.array())
        val buffer = ByteBuffer.allocate(4 + 2 + escapedRbsp.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x00000001) // 4-byte start code
        buffer.putShort(0x4E01.toShort()) // PREFIX_SEI_NUT, layer=0, tid=1
        buffer.put(escapedRbsp)
        return buffer.array()
    }

    /**
     * Serializes to full MP4 ISOBMFF 'mdcv' atom.
     * Total size: 32 bytes (8-byte header: 4-byte size = 32, 4-byte type = 'mdcv' + 24-byte payload).
     */
    fun toMp4MdcvAtom(): ByteArray {
        val payload = toRawSei137Payload()
        val buffer = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(8 + payload.size) // Total box size = 32
        buffer.put(byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'c'.code.toByte(), 'v'.code.toByte()))
        buffer.put(payload)
        return buffer.array()
    }

    companion object {
        /**
         * Standard ITU-R BT.2020 true display primaries targeting 1000 nits.
         * Primaries:
         * - Green: x = 0.170, y = 0.797 (8500, 39850)
         * - Blue:  x = 0.131, y = 0.046 (6550, 2300)
         * - Red:   x = 0.708, y = 0.292 (35400, 14600)
         * - White D65: x = 0.3127, y = 0.3290 (15635, 16450)
         */
        val BT2020_TRUE_1000NITS = HdrMasteringDisplay(
            greenX = 8500,
            greenY = 39850,
            blueX = 6550,
            blueY = 2300,
            redX = 35400,
            redY = 14600,
            whitePointX = 15635,
            whitePointY = 16450,
            maxMasteringLuminanceNits = 1000,
            minMasteringLuminanceTenThousandthsNits = 1, // 0.0001 cd/m2
            provenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED,
            profileName = "BT.2020 True Reference (1000 nits)"
        )

        /**
         * Standard DCI-P3 D65 reference mastering display targeting 1000 nits.
         * Widely used in industry mastering (e.g. Sony BVM-X300) for HDR10 within a BT.2020 container.
         * Primaries:
         * - Green: x = 0.265, y = 0.690 (13250, 34500)
         * - Blue:  x = 0.150, y = 0.060 (7500, 3000)
         * - Red:   x = 0.680, y = 0.320 (34000, 16000)
         * - White D65: x = 0.3127, y = 0.3290 (15635, 16450)
         */
        val DCI_P3_D65_1000NITS = HdrMasteringDisplay(
            greenX = 13250,
            greenY = 34500,
            blueX = 7500,
            blueY = 3000,
            redX = 34000,
            redY = 16000,
            whitePointX = 15635,
            whitePointY = 16450,
            maxMasteringLuminanceNits = 1000,
            minMasteringLuminanceTenThousandthsNits = 1, // 0.0001 cd/m2
            provenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED,
            profileName = "DCI-P3 D65 Mastering Reference (1000 nits)"
        )

        /**
         * Parses a 24-byte Big-Endian buffer (from SEI payload 137 or MP4 'mdcv' atom data).
         */
        fun fromRawSei137Payload(
            payload: ByteArray,
            provenance: MetadataProvenance = MetadataProvenance.SOURCE_DECLARED
        ): HdrMasteringDisplay? {
            if (payload.size < 24) return null
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

            val gx = buffer.short.toInt() and 0xFFFF
            val gy = buffer.short.toInt() and 0xFFFF
            val bx = buffer.short.toInt() and 0xFFFF
            val by = buffer.short.toInt() and 0xFFFF
            val rx = buffer.short.toInt() and 0xFFFF
            val ry = buffer.short.toInt() and 0xFFFF

            val wx = buffer.short.toInt() and 0xFFFF
            val wy = buffer.short.toInt() and 0xFFFF

            val maxLumTenThousandths = buffer.int.toLong() and 0xFFFFFFFFL
            val minLumTenThousandths = buffer.int.toLong() and 0xFFFFFFFFL

            val maxNits = (maxLumTenThousandths / 10000L).toInt().coerceIn(1, 65535)
            val minTenThousandths = minLumTenThousandths.toInt().coerceIn(0, 65535)

            return HdrMasteringDisplay(
                greenX = gx,
                greenY = gy,
                blueX = bx,
                blueY = by,
                redX = rx,
                redY = ry,
                whitePointX = wx,
                whitePointY = wy,
                maxMasteringLuminanceNits = maxNits,
                minMasteringLuminanceTenThousandthsNits = minTenThousandths,
                provenance = provenance,
                profileName = "Parsed from SEI/MDCV"
            )
        }
    }
}
