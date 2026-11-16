package com.unidownco.engine.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Information extracted from an HEVC Sequence Parameter Set (SPS).
 */
data class HevcSpsInfo(
    val profileIdc: Int,
    val profileName: String,
    val bitDepthLuma: Int,
    val bitDepthChroma: Int,
    val picWidth: Int,
    val picHeight: Int,
    val hasVui: Boolean,
    val colorPrimaries: Int? = null,
    val transferCharacteristics: Int? = null,
    val matrixCoefficients: Int? = null,
    val fullRange: Boolean? = null
)

/**
 * Mastering Display Colour Volume (MDCV) SEI Payload (Payload Type 137).
 * Defined by ITU-T H.265 / SMPTE ST 2086.
 *
 * Luminance values are in units of 0.0001 cd/m² (1 nit = 10,000 units).
 */
data class HevcSeiMdcv(
    val displayPrimariesGx: Int,
    val displayPrimariesGy: Int,
    val displayPrimariesBx: Int,
    val displayPrimariesBy: Int,
    val displayPrimariesRx: Int,
    val displayPrimariesRy: Int,
    val whitePointX: Int,
    val whitePointY: Int,
    val maxMasteringLuminance: Long,
    val minMasteringLuminance: Long
) {
    val maxMasteringLuminanceNits: Double
        get() = maxMasteringLuminance / 10000.0

    val minMasteringLuminanceNits: Double
        get() = minMasteringLuminance / 10000.0
}

/**
 * Content Light Level Information (CLLI) SEI Payload (Payload Type 144).
 * Defined by ITU-T H.265 / CTA-861.3.
 *
 * Light levels are in units of 1 cd/m² (nits).
 */
data class HevcSeiClli(
    val maxCll: Int,
    val maxFall: Int
)

sealed class HevcSeiMessage {
    data class Mdcv(val mdcv: HevcSeiMdcv) : HevcSeiMessage()
    data class Clli(val clli: HevcSeiClli) : HevcSeiMessage()
    data class Other(val payloadType: Int, val payloadSize: Int) : HevcSeiMessage()
}

data class HevcBitstreamReport(
    val spsList: List<HevcSpsInfo>,
    val mdcvSeiList: List<HevcSeiMdcv>,
    val clliSeiList: List<HevcSeiClli>,
    val isMain10: Boolean,
    val is10Bit: Boolean,
    val isBt2020: Boolean,
    val isSmpte2084: Boolean,
    val hasStaticMetadata: Boolean,
    val warnings: List<String> = emptyList()
)

/**
 * Pure Kotlin parser for HEVC / H.265 Annex B streams and NAL units.
 *
 * Provides bitstream verification for:
 * 1. HEVC SPS profile_idc == 2 (Main 10)
 * 2. Bit depth luma and chroma == 10
 * 3. VUI color primaries (9 = BT.2020) and transfer characteristics (16 = ST 2084)
 * 4. Prefix SEI 137 (Mastering Display Colour Volume)
 * 5. Prefix SEI 144 (Content Light Level Information)
 */
object HevcBitstreamParser {

    const val NAL_VPS = 32
    const val NAL_SPS = 33
    const val NAL_PPS = 34
    const val NAL_PREFIX_SEI = 39
    const val NAL_SUFFIX_SEI = 40

    const val SEI_TYPE_MDCV = 137
    const val SEI_TYPE_CLLI = 144

    /**
     * Extracts raw NAL units from an Annex B byte stream.
     * Searches for 0x000001 (3-byte) or 0x00000001 (4-byte) start codes.
     */
    fun extractAnnexBNalUnits(data: ByteArray): List<ByteArray> {
        val nalUnits = mutableListOf<ByteArray>()
        val len = data.size
        var start = -1
        var i = 0

        while (i < len - 2) {
            val isThreeByte = data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()
            val isFourByte = i < len - 3 && data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()

            if (isFourByte || isThreeByte) {
                if (start != -1) {
                    val nalEnd = i
                    if (nalEnd > start) {
                        nalUnits.add(data.copyOfRange(start, nalEnd))
                    }
                }
                start = if (isFourByte) i + 4 else i + 3
                i = start
            } else {
                i++
            }
        }

        if (start != -1 && start < len) {
            nalUnits.add(data.copyOfRange(start, len))
        }

        return nalUnits
    }

    /**
     * Inserts HEVC emulation prevention bytes (0x03) when 0x0000 is followed by <= 0x03.
     */
    fun insertEmulationPrevention(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size + 16)
        var zeroCount = 0
        for (b in bytes) {
            if (zeroCount >= 2 && (b.toInt() and 0xFF) <= 3) {
                out.add(3.toByte())
                zeroCount = 0
            }
            out.add(b)
            if (b == 0.toByte()) {
                zeroCount++
            } else {
                zeroCount = 0
            }
        }
        val result = ByteArray(out.size)
        for (idx in out.indices) {
            result[idx] = out[idx]
        }
        return result
    }

    /**
     * Unescapes HEVC emulation prevention bytes (0x000003 -> 0x0000).
     */
    fun unescapeEmulationPrevention(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        var i = 0
        val len = bytes.size
        while (i < len) {
            if (i + 2 < len && bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() && bytes[i + 2] == 3.toByte()) {
                out.add(bytes[i])
                out.add(bytes[i + 1])
                i += 3 // Skip the 0x03 byte
            } else {
                out.add(bytes[i])
                i++
            }
        }
        val result = ByteArray(out.size)
        for (idx in out.indices) {
            result[idx] = out[idx]
        }
        return result
    }

    /**
     * Extracts the NAL unit type from a raw NAL unit (with or without start codes).
     */
    fun getNalUnitType(nalData: ByteArray): Int {
        if (nalData.isEmpty()) return -1
        // Skip leading start code if present
        var offset = 0
        if (nalData.size >= 4 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 0.toByte() && nalData[3] == 1.toByte()) {
            offset = 4
        } else if (nalData.size >= 3 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 1.toByte()) {
            offset = 3
        }
        if (offset >= nalData.size) return -1
        return (nalData[offset].toInt() and 0x7E) ushr 1
    }

    /**
     * Parses an HEVC Sequence Parameter Set (SPS) NAL unit.
     */
    fun parseSpsNal(nalData: ByteArray): HevcSpsInfo? {
        var offset = 0
        if (nalData.size >= 4 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 0.toByte() && nalData[3] == 1.toByte()) {
            offset = 4
        } else if (nalData.size >= 3 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 1.toByte()) {
            offset = 3
        }

        if (nalData.size <= offset + 2) return null

        val nalType = (nalData[offset].toInt() and 0x7E) ushr 1
        if (nalType != NAL_SPS) return null

        // Strip 2-byte NAL header and unescape RBSP
        val rawRbsp = nalData.copyOfRange(offset + 2, nalData.size)
        val rbsp = unescapeEmulationPrevention(rawRbsp)
        val reader = BitReader(rbsp)

        try {
            // sps_video_parameter_set_id (u(4))
            reader.readBits(4)
            // sps_max_sub_layers_minus1 (u(3))
            val maxSubLayersMinus1 = reader.readBits(3)
            // sps_temporal_id_nesting_flag (u(1))
            reader.readFlag()

            // profile_tier_level
            reader.readBits(2) // general_profile_space
            reader.readFlag()  // general_tier_flag
            val profileIdc = reader.readBits(5) // general_profile_idc: 1 = Main, 2 = Main 10
            reader.readBitsLong(32) // general_profile_compatibility_flags
            reader.readFlag()  // general_progressive_source_flag
            reader.readFlag()  // general_interlaced_source_flag
            reader.readFlag()  // general_non_packed_constraint_flag
            reader.readFlag()  // general_frame_only_constraint_flag
            reader.skipBits(44) // reserved constraint flags
            reader.readBits(8)  // general_level_idc

            val subLayerProfilePresent = BooleanArray(maxSubLayersMinus1)
            val subLayerLevelPresent = BooleanArray(maxSubLayersMinus1)
            for (i in 0 until maxSubLayersMinus1) {
                subLayerProfilePresent[i] = reader.readFlag()
                subLayerLevelPresent[i] = reader.readFlag()
            }
            if (maxSubLayersMinus1 > 0) {
                reader.skipBits((8 - maxSubLayersMinus1) * 2)
            }
            for (i in 0 until maxSubLayersMinus1) {
                if (subLayerProfilePresent[i]) {
                    reader.skipBits(88) // sub_layer profile flags
                }
                if (subLayerLevelPresent[i]) {
                    reader.skipBits(8)  // sub_layer level
                }
            }

            // sps_seq_parameter_set_id (ue(v))
            reader.readUe()
            // chroma_format_idc (ue(v)): 0=mono, 1=4:2:0, 2=4:2:2, 3=4:4:4
            val chromaFormatIdc = reader.readUe()
            if (chromaFormatIdc == 3) {
                reader.readFlag() // separate_colour_plane_flag
            }

            // Dimensions
            val picWidth = reader.readUe()
            val picHeight = reader.readUe()

            // conformance_window_flag
            val confWinFlag = reader.readFlag()
            if (confWinFlag) {
                reader.readUe() // conf_win_left_offset
                reader.readUe() // conf_win_right_offset
                reader.readUe() // conf_win_top_offset
                reader.readUe() // conf_win_bottom_offset
            }

            // Bit depths
            val bitDepthLumaMinus8 = reader.readUe()
            val bitDepthChromaMinus8 = reader.readUe()
            val bitDepthLuma = bitDepthLumaMinus8 + 8
            val bitDepthChroma = bitDepthChromaMinus8 + 8

            // log2_max_pic_order_cnt_lsb_minus4
            val log2MaxPocLsbMinus4 = reader.readUe()

            // Sub-layer ordering
            val subLayerOrderingPresent = reader.readFlag()
            val startOrdering = if (subLayerOrderingPresent) 0 else maxSubLayersMinus1
            for (i in startOrdering..maxSubLayersMinus1) {
                reader.readUe() // max_dec_pic_buffering_minus1
                reader.readUe() // max_num_reorder_pics
                reader.readUe() // max_latency_increase_plus1
            }

            // Coding block sizes
            reader.readUe() // log2_min_luma_coding_block_size_minus3
            reader.readUe() // log2_diff_max_min_luma_coding_block_size
            reader.readUe() // log2_min_luma_transform_block_size_minus2
            reader.readUe() // log2_diff_max_min_luma_transform_block_size
            reader.readUe() // max_transform_hierarchy_depth_inter
            reader.readUe() // max_transform_hierarchy_depth_intra

            // Scaling list
            val scalingListEnabled = reader.readFlag()
            if (scalingListEnabled) {
                val scalingListDataPresent = reader.readFlag()
                if (scalingListDataPresent) {
                    skipScalingListData(reader)
                }
            }

            reader.readFlag() // amp_enabled_flag
            reader.readFlag() // sample_adaptive_offset_enabled_flag

            // PCM
            val pcmEnabled = reader.readFlag()
            if (pcmEnabled) {
                reader.readBits(4) // pcm_sample_bit_depth_luma_minus1
                reader.readBits(4) // pcm_sample_bit_depth_chroma_minus1
                reader.readUe()    // log2_min_pcm_luma_coding_block_size_minus3
                reader.readUe()    // log2_diff_max_min_pcm_luma_coding_block_size
                reader.readFlag()  // pcm_loop_filter_disabled_flag
            }

            // Short-term reference picture sets
            val numShortTermRefPicSets = reader.readUe()
            var numPicTotalCurr = 0
            for (i in 0 until numShortTermRefPicSets) {
                skipShortTermRefPicSet(i, numShortTermRefPicSets, reader)
            }

            // Long-term reference picture sets
            val longTermRefPicsPresent = reader.readFlag()
            if (longTermRefPicsPresent) {
                val numLongTermRefPics = reader.readUe()
                for (i in 0 until numLongTermRefPics) {
                    reader.readBits(log2MaxPocLsbMinus4 + 4) // lt_ref_pic_poc_lsb_sps
                    reader.readFlag() // used_by_curr_pic_lt_sps_flag
                }
            }

            reader.readFlag() // sps_temporal_mvp_enabled_flag
            reader.readFlag() // strong_intra_smoothing_enabled_flag

            // VUI Parameters
            val vuiPresent = reader.readFlag()
            var colorPrimaries: Int? = null
            var transferCharacteristics: Int? = null
            var matrixCoeffs: Int? = null
            var fullRange: Boolean? = null

            if (vuiPresent) {
                val aspectRatioPresent = reader.readFlag()
                if (aspectRatioPresent) {
                    val aspectIdc = reader.readBits(8)
                    if (aspectIdc == 255) {
                        reader.readBits(16) // sar_width
                        reader.readBits(16) // sar_height
                    }
                }
                val overscanPresent = reader.readFlag()
                if (overscanPresent) {
                    reader.readFlag() // overscan_appropriate_flag
                }
                val videoSignalPresent = reader.readFlag()
                if (videoSignalPresent) {
                    reader.readBits(3) // video_format
                    fullRange = reader.readFlag() // video_full_range_flag
                    val colourDescPresent = reader.readFlag()
                    if (colourDescPresent) {
                        colorPrimaries = reader.readBits(8)
                        transferCharacteristics = reader.readBits(8)
                        matrixCoeffs = reader.readBits(8)
                    }
                }
            }

            val profileName = when (profileIdc) {
                1 -> "Main"
                2 -> "Main 10"
                3 -> "Main Still Picture"
                else -> "Profile $profileIdc"
            }

            return HevcSpsInfo(
                profileIdc = profileIdc,
                profileName = profileName,
                bitDepthLuma = bitDepthLuma,
                bitDepthChroma = bitDepthChroma,
                picWidth = picWidth,
                picHeight = picHeight,
                hasVui = vuiPresent,
                colorPrimaries = colorPrimaries,
                transferCharacteristics = transferCharacteristics,
                matrixCoefficients = matrixCoeffs,
                fullRange = fullRange
            )
        } catch (_: Exception) {
            // If advanced fields parsing failed, return partial info if profile was parsed
            return null
        }
    }

    private fun skipScalingListData(reader: BitReader) {
        for (sizeId in 0..3) {
            val matrixCount = if (sizeId == 3) 2 else 6
            for (matrixId in 0 until matrixCount) {
                val predMode = reader.readFlag()
                if (!predMode) {
                    reader.readUe()
                } else {
                    val coefNum = minOf(64, 1 shl (4 + (sizeId shl 1)))
                    if (sizeId > 1) {
                        reader.readSe()
                    }
                    for (i in 0 until coefNum) {
                        reader.readSe()
                    }
                }
            }
        }
    }

    private fun skipShortTermRefPicSet(stRpsIdx: Int, numShortTermRefPicSets: Int, reader: BitReader) {
        var interRefPicSetPredictionFlag = false
        if (stRpsIdx != 0) {
            interRefPicSetPredictionFlag = reader.readFlag()
        }
        if (interRefPicSetPredictionFlag) {
            reader.readFlag() // delta_rps_sign
            reader.readUe()   // abs_delta_rps_minus1
            // Ref pic set prediction loop
            // In typical streams this is short
            val numEntries = 16 // safe bounded skip
            for (i in 0 until numEntries) {
                if (!reader.hasMoreBits()) break
                val usedByCurrPic = reader.readFlag()
                if (!usedByCurrPic) {
                    reader.readFlag()
                }
            }
        } else {
            val numNegativePics = reader.readUe()
            val numPositivePics = reader.readUe()
            for (i in 0 until numNegativePics) {
                reader.readUe()   // delta_poc_s0_minus1
                reader.readFlag() // used_by_curr_pic_s0_flag
            }
            for (i in 0 until numPositivePics) {
                reader.readUe()   // delta_poc_s1_minus1
                reader.readFlag() // used_by_curr_pic_s1_flag
            }
        }
    }

    /**
     * Parses a Prefix SEI NAL unit (NAL unit type 39).
     * Extracts SEI payloads:
     * - Payload 137: Mastering Display Colour Volume (MDCV)
     * - Payload 144: Content Light Level Information (CLLI)
     */
    fun parsePrefixSeiNal(nalData: ByteArray): List<HevcSeiMessage> {
        var offset = 0
        if (nalData.size >= 4 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 0.toByte() && nalData[3] == 1.toByte()) {
            offset = 4
        } else if (nalData.size >= 3 && nalData[0] == 0.toByte() && nalData[1] == 0.toByte() && nalData[2] == 1.toByte()) {
            offset = 3
        }

        if (nalData.size <= offset + 2) return emptyList()

        val nalType = (nalData[offset].toInt() and 0x7E) ushr 1
        if (nalType != NAL_PREFIX_SEI && nalType != NAL_SUFFIX_SEI) return emptyList()

        val rawRbsp = nalData.copyOfRange(offset + 2, nalData.size)
        val rbsp = unescapeEmulationPrevention(rawRbsp)

        val messages = mutableListOf<HevcSeiMessage>()
        var ptr = 0
        val len = rbsp.size

        while (ptr < len) {
            // Read payloadType
            var payloadType = 0
            while (ptr < len && (rbsp[ptr].toInt() and 0xFF) == 0xFF) {
                payloadType += 255
                ptr++
            }
            if (ptr >= len) break
            payloadType += (rbsp[ptr].toInt() and 0xFF)
            ptr++

            // Read payloadSize
            var payloadSize = 0
            while (ptr < len && (rbsp[ptr].toInt() and 0xFF) == 0xFF) {
                payloadSize += 255
                ptr++
            }
            if (ptr >= len) break
            payloadSize += (rbsp[ptr].toInt() and 0xFF)
            ptr++

            if (ptr + payloadSize > len) {
                // Truncated or trailing bits reached
                break
            }

            val payload = rbsp.copyOfRange(ptr, ptr + payloadSize)
            ptr += payloadSize

            when (payloadType) {
                SEI_TYPE_MDCV -> {
                    if (payload.size >= 24) {
                        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                        val gx = buf.short.toInt() and 0xFFFF
                        val gy = buf.short.toInt() and 0xFFFF
                        val bx = buf.short.toInt() and 0xFFFF
                        val by = buf.short.toInt() and 0xFFFF
                        val rx = buf.short.toInt() and 0xFFFF
                        val ry = buf.short.toInt() and 0xFFFF
                        val wx = buf.short.toInt() and 0xFFFF
                        val wy = buf.short.toInt() and 0xFFFF
                        val maxLum = buf.int.toLong() and 0xFFFFFFFFL
                        val minLum = buf.int.toLong() and 0xFFFFFFFFL

                        messages.add(
                            HevcSeiMessage.Mdcv(
                                HevcSeiMdcv(
                                    displayPrimariesGx = gx,
                                    displayPrimariesGy = gy,
                                    displayPrimariesBx = bx,
                                    displayPrimariesBy = by,
                                    displayPrimariesRx = rx,
                                    displayPrimariesRy = ry,
                                    whitePointX = wx,
                                    whitePointY = wy,
                                    maxMasteringLuminance = maxLum,
                                    minMasteringLuminance = minLum
                                )
                            )
                        )
                    }
                }
                SEI_TYPE_CLLI -> {
                    if (payload.size >= 4) {
                        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                        val maxCll = buf.short.toInt() and 0xFFFF
                        val maxFall = buf.short.toInt() and 0xFFFF
                        messages.add(
                            HevcSeiMessage.Clli(
                                HevcSeiClli(
                                    maxCll = maxCll,
                                    maxFall = maxFall
                                )
                            )
                        )
                    }
                }
                else -> {
                    messages.add(HevcSeiMessage.Other(payloadType, payloadSize))
                }
            }

            // Check if remaining byte is rbsp_trailing_bits (0x80)
            if (ptr < len && (rbsp[ptr].toInt() and 0xFF) == 0x80 && ptr == len - 1) {
                break
            }
        }

        return messages
    }

    /**
     * Parses a collection of NAL units and produces a consolidated report.
     */
    fun parseNalUnits(nalUnits: List<ByteArray>): HevcBitstreamReport {
        val spsList = mutableListOf<HevcSpsInfo>()
        val mdcvList = mutableListOf<HevcSeiMdcv>()
        val clliList = mutableListOf<HevcSeiClli>()
        val warnings = mutableListOf<String>()

        for (nal in nalUnits) {
            when (getNalUnitType(nal)) {
                NAL_SPS -> {
                    val sps = parseSpsNal(nal)
                    if (sps != null) {
                        spsList.add(sps)
                    } else {
                        warnings.add("Failed parsing SPS NAL unit (${nal.size} bytes)")
                    }
                }
                NAL_PREFIX_SEI -> {
                    val messages = parsePrefixSeiNal(nal)
                    for (msg in messages) {
                        when (msg) {
                            is HevcSeiMessage.Mdcv -> mdcvList.add(msg.mdcv)
                            is HevcSeiMessage.Clli -> clliList.add(msg.clli)
                            is HevcSeiMessage.Other -> {}
                        }
                    }
                }
            }
        }

        val firstSps = spsList.firstOrNull()
        val isMain10 = firstSps?.profileIdc == 2
        val is10Bit = firstSps?.bitDepthLuma == 10
        val isBt2020 = firstSps?.colorPrimaries == 9
        val isSmpte2084 = firstSps?.transferCharacteristics == 16
        val hasStaticMetadata = mdcvList.isNotEmpty()

        return HevcBitstreamReport(
            spsList = spsList,
            mdcvSeiList = mdcvList,
            clliSeiList = clliList,
            isMain10 = isMain10,
            is10Bit = is10Bit,
            isBt2020 = isBt2020,
            isSmpte2084 = isSmpte2084,
            hasStaticMetadata = hasStaticMetadata,
            warnings = warnings
        )
    }

    /**
     * Parses an Annex B byte stream directly.
     */
    fun parseAnnexB(data: ByteArray): HevcBitstreamReport {
        val nals = extractAnnexBNalUnits(data)
        return parseNalUnits(nals)
    }

    /**
     * Helper bit reader for Exp-Golomb and variable bit reading.
     */
    class BitReader(private val bytes: ByteArray) {
        private var bitOffset = 0

        fun hasMoreBits(): Boolean = bitOffset < bytes.size * 8

        fun readBits(n: Int): Int {
            if (n == 0) return 0
            var result = 0
            for (i in 0 until n) {
                val byteIndex = bitOffset / 8
                if (byteIndex >= bytes.size) return result shl (n - i)
                val bitInByte = 7 - (bitOffset % 8)
                val bit = (bytes[byteIndex].toInt() ushr bitInByte) and 1
                result = (result shl 1) or bit
                bitOffset++
            }
            return result
        }

        fun readBitsLong(n: Int): Long {
            if (n == 0) return 0L
            var result = 0L
            for (i in 0 until n) {
                val byteIndex = bitOffset / 8
                if (byteIndex >= bytes.size) return result shl (n - i)
                val bitInByte = 7 - (bitOffset % 8)
                val bit = (bytes[byteIndex].toLong() ushr bitInByte) and 1L
                result = (result shl 1) or bit
                bitOffset++
            }
            return result
        }

        fun readFlag(): Boolean = readBits(1) == 1

        fun readUe(): Int {
            var leadingZeros = 0
            while (bitOffset < bytes.size * 8 && readBits(1) == 0) {
                leadingZeros++
            }
            if (leadingZeros == 0) return 0
            if (leadingZeros >= 31) return 0
            val rest = readBits(leadingZeros)
            return (1 shl leadingZeros) - 1 + rest
        }

        fun readSe(): Int {
            val ue = readUe()
            return if (ue % 2 == 1) {
                (ue + 1) / 2
            } else {
                -(ue / 2)
            }
        }

        fun skipBits(n: Int) {
            bitOffset = (bitOffset + n).coerceAtMost(bytes.size * 8)
        }
    }
}
