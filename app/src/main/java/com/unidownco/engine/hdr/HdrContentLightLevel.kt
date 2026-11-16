package com.unidownco.engine.hdr

import com.unidownco.engine.parser.HevcBitstreamParser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates Content Light Level information (MaxCLL and MaxFALL).
 * Conforms to CTA-861.3, ITU-T H.265 D.2.35, and ISO/IEC 14496-12 / 23008-12.
 *
 * CRITICAL OMISSION POLICY:
 * When metadata is [MetadataProvenance.UNAVAILABLE], MaxCLL and MaxFALL must NOT be fabricated.
 * In standards-compliant streams, the HEVC SEI Payload Type 144 and MP4 'clli' atom are omitted entirely.
 */
data class HdrContentLightLevel(
    val maxCllNits: Int?, // Maximum Content Light Level in 1 cd/m2
    val maxFallNits: Int?, // Maximum Frame-Average Light Level in 1 cd/m2
    val provenance: MetadataProvenance = MetadataProvenance.UNAVAILABLE,
    val calculationMethod: String? = null
) {
    val isAvailable: Boolean
        get() = provenance != MetadataProvenance.UNAVAILABLE && maxCllNits != null && maxFallNits != null

    /**
     * Serializes to 4-byte payload for HEVC SEI payloadType 144 or MP4 'clli' data block.
     * Big-Endian: 16-bit uint max_content_light_level, 16-bit uint max_pic_average_light_level.
     *
     * Returns null if metadata is UNAVAILABLE or null, enforcing standards-compliant omission.
     */
    fun toRawSei144Payload(): ByteArray? {
        if (!isAvailable) return null
        val cll = maxCllNits!!.coerceIn(0, 65535)
        val fall = maxFallNits!!.coerceIn(0, 65535)

        val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(cll.toShort())
        buffer.putShort(fall.toShort())
        return buffer.array()
    }

    /**
     * Serializes to full HEVC Prefix SEI NAL unit for Payload Type 144.
     * Returns null if metadata is UNAVAILABLE, omitting the SEI NAL unit entirely.
     */
    fun toHevcSeiNalUnit(): ByteArray? {
        val payload = toRawSei144Payload() ?: return null
        val rawRbsp = ByteBuffer.allocate(2 + payload.size).order(ByteOrder.BIG_ENDIAN)
        rawRbsp.put(144.toByte()) // payloadType = 144
        rawRbsp.put(payload.size.toByte()) // payloadSize = 4
        rawRbsp.put(payload)

        val escapedRbsp = HevcBitstreamParser.insertEmulationPrevention(rawRbsp.array())
        val buffer = ByteBuffer.allocate(4 + 2 + escapedRbsp.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x00000001) // 4-byte start code
        buffer.putShort(0x4E01.toShort()) // PREFIX_SEI_NUT, layer=0, tid=1
        buffer.put(escapedRbsp)
        return buffer.array()
    }

    /**
     * Serializes to full MP4 ISOBMFF 'clli' atom.
     * Returns null if metadata is UNAVAILABLE, omitting the box entirely.
     */
    fun toMp4ClliAtom(): ByteArray? {
        val payload = toRawSei144Payload() ?: return null
        val buffer = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(8 + payload.size) // Total box size = 12
        buffer.put(byteArrayOf('c'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte()))
        buffer.put(payload)
        return buffer.array()
    }

    companion object {
        /**
         * Represents explicitly unavailable content light level metadata.
         */
        val UNAVAILABLE_INSTANCE = HdrContentLightLevel(
            maxCllNits = null,
            maxFallNits = null,
            provenance = MetadataProvenance.UNAVAILABLE
        )

        /**
         * Creates a mathematically estimated content light level bounded by the inverse tone mapping curve limits.
         * For BT.2408 expansion with 1000-nit target, theoretical ceiling is 1000 nits.
         */
        fun createMathematicallyEstimated(
            peakTargetNits: Int = 1000,
            estimatedAverageNits: Int = 203
        ): HdrContentLightLevel {
            return HdrContentLightLevel(
                maxCllNits = peakTargetNits,
                maxFallNits = estimatedAverageNits,
                provenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED,
                calculationMethod = "Bounded by ITU-R BT.2408 analytical expansion curve ($peakTargetNits cd/m2 max, $estimatedAverageNits cd/m2 diffuse white)"
            )
        }

        /**
         * Creates a measured content light level computed from decoded frame pixel luminance statistics.
         */
        fun createMeasured(
            measuredMaxCll: Int,
            measuredMaxFall: Int,
            frameCount: Long
        ): HdrContentLightLevel {
            return HdrContentLightLevel(
                maxCllNits = measuredMaxCll,
                maxFallNits = measuredMaxFall,
                provenance = MetadataProvenance.MEASURED,
                calculationMethod = "Calculated across $frameCount decoded linear frames"
            )
        }

        /**
         * Parses a 4-byte Big-Endian buffer (from SEI payload 144 or MP4 'clli' atom data).
         */
        fun fromRawSei144Payload(
            payload: ByteArray,
            provenance: MetadataProvenance = MetadataProvenance.SOURCE_DECLARED
        ): HdrContentLightLevel? {
            if (payload.size < 4) return null
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val cll = buffer.short.toInt() and 0xFFFF
            val fall = buffer.short.toInt() and 0xFFFF

            if (cll == 0 && fall == 0) {
                return HdrContentLightLevel(
                    maxCllNits = null,
                    maxFallNits = null,
                    provenance = MetadataProvenance.UNAVAILABLE
                )
            }

            return HdrContentLightLevel(
                maxCllNits = cll,
                maxFallNits = fall,
                provenance = provenance,
                calculationMethod = "Parsed from SEI/CLLI"
            )
        }
    }
}
