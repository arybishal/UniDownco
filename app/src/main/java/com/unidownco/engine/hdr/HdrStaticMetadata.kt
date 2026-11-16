package com.unidownco.engine.hdr

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unified container for HDR10 static mastering display metadata and content light level information.
 * Conforms to CTA-861.3 / CTA-861-G, SMPTE ST 2086, ITU-T H.265 Annex D, and ISO/IEC 14496-12 / 23008-12.
 *
 * Provides distinct, standards-compliant serializations for:
 * 1. Android MediaCodec [android.media.MediaFormat.KEY_HDR_STATIC_INFO] (CTA-861.3 Little-Endian)
 * 2. HEVC Elementary Stream Prefix SEI Payload Type 137 (MDCV) and Payload Type 144 (CLLI)
 * 3. MP4 Container 'mdcv' and 'clli' Atoms (Big-Endian)
 * 4. MP4 Container 'colr' Atom ('nclx' tuple)
 */
data class HdrStaticMetadata(
    val masteringDisplay: HdrMasteringDisplay = HdrMasteringDisplay.BT2020_TRUE_1000NITS,
    val contentLightLevel: HdrContentLightLevel = HdrContentLightLevel.UNAVAILABLE_INSTANCE
) {
    // Convenience property delegates for backward compatibility
    val greenX: Int get() = masteringDisplay.greenX
    val greenY: Int get() = masteringDisplay.greenY
    val blueX: Int get() = masteringDisplay.blueX
    val blueY: Int get() = masteringDisplay.blueY
    val redX: Int get() = masteringDisplay.redX
    val redY: Int get() = masteringDisplay.redY
    val whitePointX: Int get() = masteringDisplay.whitePointX
    val whitePointY: Int get() = masteringDisplay.whitePointY
    val maxMasteringLuminanceNits: Int get() = masteringDisplay.maxMasteringLuminanceNits
    val minMasteringLuminanceTenThousandthsNits: Int get() = masteringDisplay.minMasteringLuminanceTenThousandthsNits
    val maxCllNits: Int get() = contentLightLevel.maxCllNits ?: 0
    val maxFallNits: Int get() = contentLightLevel.maxFallNits ?: 0

    /**
     * Secondary constructor preserving backward-compatible signature.
     */
    constructor(
        greenX: Int,
        greenY: Int,
        blueX: Int,
        blueY: Int,
        redX: Int,
        redY: Int,
        whitePointX: Int,
        whitePointY: Int,
        maxMasteringLuminanceNits: Int,
        minMasteringLuminanceTenThousandthsNits: Int,
        maxCllNits: Int,
        maxFallNits: Int,
        provenance: MetadataProvenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED
    ) : this(
        masteringDisplay = HdrMasteringDisplay(
            greenX = greenX,
            greenY = greenY,
            blueX = blueX,
            blueY = blueY,
            redX = redX,
            redY = redY,
            whitePointX = whitePointX,
            whitePointY = whitePointY,
            maxMasteringLuminanceNits = maxMasteringLuminanceNits,
            minMasteringLuminanceTenThousandthsNits = minMasteringLuminanceTenThousandthsNits,
            provenance = provenance
        ),
        contentLightLevel = if (maxCllNits > 0 || maxFallNits > 0) {
            HdrContentLightLevel(
                maxCllNits = maxCllNits,
                maxFallNits = maxFallNits,
                provenance = provenance
            )
        } else {
            HdrContentLightLevel.UNAVAILABLE_INSTANCE
        }
    )

    /**
     * Serializes this static metadata into the exact 25-byte CTA-861.3 binary structure
     * expected by Android [android.media.MediaFormat.KEY_HDR_STATIC_INFO].
     *
     * Format: Little-Endian, 25 bytes total:
     * - Byte 0: Static Metadata Descriptor ID (0 = Type 1)
     * - Bytes 1-12: Primaries (Green, Blue, Red; 16-bit uint each, 0.00002 units)
     * - Bytes 13-16: White point (16-bit uint each, 0.00002 units)
     * - Bytes 17-18: Max Mastering Luminance in 1 cd/m2 (16-bit uint)
     * - Bytes 19-20: Min Mastering Luminance in 0.0001 cd/m2 (16-bit uint)
     * - Bytes 21-22: MaxCLL in 1 cd/m2 (16-bit uint, 0 if unavailable)
     * - Bytes 23-24: MaxFALL in 1 cd/m2 (16-bit uint, 0 if unavailable)
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

        // Content Light Level (0 if unavailable per CTA-861.3)
        buffer.putShort(maxCllNits.toShort())
        buffer.putShort(maxFallNits.toShort())

        buffer.rewind()
        return buffer
    }

    /**
     * Generates the MP4 'mdcv' atom (Mastering Display Colour Volume Box).
     */
    fun toMp4MdcvAtom(): ByteArray = masteringDisplay.toMp4MdcvAtom()

    /**
     * Generates the MP4 'clli' atom (Content Light Level Info Box).
     * Returns null if content light level is UNAVAILABLE, enforcing standards-compliant omission.
     */
    fun toMp4ClliAtom(): ByteArray? = contentLightLevel.toMp4ClliAtom()

    /**
     * Generates the MP4 'colr' atom with 'nclx' parameter type (19 bytes total).
     */
    fun toMp4ColrAtom(
        colourPrimaries: Int = 9,       // 9 = BT.2020
        transferCharacteristics: Int = 16, // 16 = SMPTE ST 2084 / PQ
        matrixCoefficients: Int = 9,    // 9 = BT.2020 non-constant luminance
        fullRangeFlag: Int = 0          // 0 = Limited/Studio range [64, 940]
    ): ByteArray {
        val buffer = ByteBuffer.allocate(19).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(19) // Box size = 19
        buffer.put(byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), 'l'.code.toByte(), 'r'.code.toByte()))
        buffer.put(byteArrayOf('n'.code.toByte(), 'c'.code.toByte(), 'l'.code.toByte(), 'x'.code.toByte()))
        buffer.putShort(colourPrimaries.toShort())
        buffer.putShort(transferCharacteristics.toShort())
        buffer.putShort(matrixCoefficients.toShort())
        buffer.put(fullRangeFlag.toByte())
        return buffer.array()
    }

    companion object {
        const val CTA_BUFFER_SIZE = 25

        /**
         * Standard ITU-R BT.2020 true display primaries targeting 1000 nits.
         * Default content light level is [MetadataProvenance.UNAVAILABLE] unless measured.
         */
        val BT2020_TRUE_1000NITS = HdrStaticMetadata(
            masteringDisplay = HdrMasteringDisplay.BT2020_TRUE_1000NITS,
            contentLightLevel = HdrContentLightLevel.UNAVAILABLE_INSTANCE
        )

        /**
         * Standard DCI-P3 D65 reference mastering display targeting 1000 nits.
         * Default content light level is [MetadataProvenance.UNAVAILABLE] unless measured.
         */
        val DCI_P3_D65_1000NITS = HdrStaticMetadata(
            masteringDisplay = HdrMasteringDisplay.DCI_P3_D65_1000NITS,
            contentLightLevel = HdrContentLightLevel.UNAVAILABLE_INSTANCE
        )

        /**
         * Backward-compatible alias to [BT2020_TRUE_1000NITS].
         */
        val BT2020_1000NITS = BT2020_TRUE_1000NITS

        /**
         * Creates an [HdrStaticMetadata] instance with mathematically estimated content light level
         * bounded by the BT.2408 analytical expansion curve (1000 nits peak, 203 nits diffuse white).
         */
        fun createMathematicallyEstimated(
            mastering: HdrMasteringDisplay = HdrMasteringDisplay.BT2020_TRUE_1000NITS,
            peakNits: Int = 1000,
            averageNits: Int = 203
        ): HdrStaticMetadata {
            return HdrStaticMetadata(
                masteringDisplay = mastering.copy(
                    maxMasteringLuminanceNits = peakNits,
                    provenance = MetadataProvenance.MATHEMATICALLY_ESTIMATED
                ),
                contentLightLevel = HdrContentLightLevel.createMathematicallyEstimated(
                    peakTargetNits = peakNits,
                    estimatedAverageNits = averageNits
                )
            )
        }

        /**
         * Parses a CTA-861.3 25-byte [ByteBuffer] back into an [HdrStaticMetadata] instance.
         */
        fun fromByteBuffer(
            buffer: ByteBuffer,
            provenance: MetadataProvenance = MetadataProvenance.SOURCE_DECLARED
        ): HdrStaticMetadata? {
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

            val cll = if (maxCll > 0 || maxFall > 0) {
                HdrContentLightLevel(
                    maxCllNits = maxCll,
                    maxFallNits = maxFall,
                    provenance = provenance
                )
            } else {
                HdrContentLightLevel.UNAVAILABLE_INSTANCE
            }

            return HdrStaticMetadata(
                masteringDisplay = HdrMasteringDisplay(
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
                    provenance = provenance,
                    profileName = "Parsed from CTA-861.3"
                ),
                contentLightLevel = cll
            )
        }
    }
}
