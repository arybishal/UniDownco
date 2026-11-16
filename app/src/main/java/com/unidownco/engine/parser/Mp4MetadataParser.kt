package com.unidownco.engine.parser

import com.unidownco.engine.inspector.model.BoxNode
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed MP4 'colr' (Color Information) box.
 */
data class Mp4ColrBox(
    val colorType: String, // Usually "nclx"
    val colorPrimaries: Int, // 9 = BT.2020
    val transferCharacteristics: Int, // 16 = SMPTE ST 2084 PQ, 18 = HLG, 1 = BT.709
    val matrixCoefficients: Int, // 9 = BT.2020 non-constant
    val fullRangeFlag: Boolean
)

/**
 * Parsed MP4 'mdcv' (Mastering Display Color Volume) box.
 * Defined in SMPTE ST 2086 / ISO/IEC 14496-12.
 *
 * Luminance is in units of 0.0001 cd/m² (1 nit = 10,000 units).
 */
data class Mp4MdcvBox(
    val greenX: Int,
    val greenY: Int,
    val blueX: Int,
    val blueY: Int,
    val redX: Int,
    val redY: Int,
    val whiteX: Int,
    val whiteY: Int,
    val maxMasteringLuminance: Long,
    val minMasteringLuminance: Long
) {
    val maxMasteringLuminanceNits: Double
        get() = maxMasteringLuminance / 10000.0

    val minMasteringLuminanceNits: Double
        get() = minMasteringLuminance / 10000.0
}

/**
 * Parsed MP4 'clli' (Content Light Level Information) box.
 * Defined in CTA-861.3 / ISO/IEC 14496-12.
 */
data class Mp4ClliBox(
    val maxCll: Int,
    val maxFall: Int
)

/**
 * Parsed MP4 'hvcC' (HEVC Configuration) box.
 */
data class Mp4HvcCBox(
    val configurationVersion: Int,
    val profileSpace: Int,
    val tierFlag: Boolean,
    val profileIdc: Int, // 1 = Main, 2 = Main 10
    val profileCompatibilityFlags: Long,
    val levelIdc: Int,
    val bitDepthLuma: Int, // 8 or 10
    val bitDepthChroma: Int, // 8 or 10
    val nalUnits: List<ByteArray>
)

/**
 * Parsed MP4 'avcC' (AVC Configuration) box.
 * Defined in ISO/IEC 14496-15.
 */
data class Mp4AvcCBox(
    val configurationVersion: Int,
    val avcProfileIndication: Int,
    val profileCompatibility: Int,
    val avcLevelIndication: Int,
    val lengthSizeMinusOne: Int,
    val spsNals: List<ByteArray>,
    val ppsNals: List<ByteArray>
)

/**
 * Parsed MP4 'ftyp' (File Type) box.
 */
data class Mp4FtypBox(
    val majorBrand: String,
    val minorVersion: Long,
    val compatibleBrands: List<String>
)

/**
 * Track metadata extracted from 'tkhd' (Track Header).
 */
data class Mp4TrackHeader(
    val trackId: Int,
    val width: Double,
    val height: Double,
    val duration: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val handlerType: String? = null
)

/**
 * Complete metadata and structural inspection report from an MP4 / ISOBMFF container.
 */
data class Mp4MetadataReport(
    val hasMoov: Boolean,
    val videoCodecFourCc: String?,
    val colrBox: Mp4ColrBox?,
    val mdcvBox: Mp4MdcvBox?,
    val clliBox: Mp4ClliBox?,
    val hvcCBox: Mp4HvcCBox?,
    val isMain10: Boolean,
    val isBt2020: Boolean,
    val isSmpte2084: Boolean,
    val isGenuineHdr10: Boolean,
    val warnings: List<String> = emptyList(),
    val boxHierarchy: List<BoxNode> = emptyList(),
    val ftypBox: Mp4FtypBox? = null,
    val avcCBox: Mp4AvcCBox? = null,
    val trackHeaders: List<Mp4TrackHeader> = emptyList()
)

/**
 * Independent pure Kotlin parser for ISO Base Media File Format (MP4 / ISOBMFF) boxes.
 *
 * Provides safe, bounded, memory-efficient inspection of:
 * - Structural container hierarchy ('moov', 'trak', 'mdia', 'minf', 'stbl', 'stsd', etc.)
 * - File types and brands ('ftyp')
 * - Video codec configurations ('hvcC', 'avcC')
 * - Colorimetry parameter records ('colr' nclx)
 * - Reference monitor mastering displays ('mdcv')
 * - Content light level declarations ('clli')
 */
object Mp4MetadataParser {

    private val CONTAINER_TYPES = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta")
    private const val MAX_RECURSION_DEPTH = 16

    private class BoxParseState {
        var hasMoov: Boolean = false
        var videoCodecFourCc: String? = null
        var colrBox: Mp4ColrBox? = null
        var mdcvBox: Mp4MdcvBox? = null
        var clliBox: Mp4ClliBox? = null
        var hvcCBox: Mp4HvcCBox? = null
        var ftypBox: Mp4FtypBox? = null
        var avcCBox: Mp4AvcCBox? = null
        val trackHeaders = mutableListOf<Mp4TrackHeader>()
        val warnings = mutableListOf<String>()
    }

    /**
     * Parses an MP4 file from disk.
     */
    fun parse(file: File): Mp4MetadataReport {
        if (!file.exists() || file.length() < 8L) {
            return Mp4MetadataReport(
                hasMoov = false,
                videoCodecFourCc = null,
                colrBox = null,
                mdcvBox = null,
                clliBox = null,
                hvcCBox = null,
                isMain10 = false,
                isBt2020 = false,
                isSmpte2084 = false,
                isGenuineHdr10 = false,
                warnings = listOf("File does not exist or too small: ${file.name}")
            )
        }

        RandomAccessFile(file, "r").use { raf ->
            val state = BoxParseState()
            val nodes = parseBoxTree(raf, 0L, raf.length(), state, depth = 0)
            return buildReport(state, nodes)
        }
    }

    /**
     * Parses an MP4 from an in-memory byte array.
     */
    fun parse(data: ByteArray): Mp4MetadataReport {
        val temp = File.createTempFile("mp4_test_", ".mp4")
        try {
            temp.writeBytes(data)
            return parse(temp)
        } finally {
            try { temp.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts only the structural BoxNode hierarchy tree from a file.
     */
    fun parseBoxHierarchy(file: File): List<BoxNode> {
        return parse(file).boxHierarchy
    }

    /**
     * Extracts only the structural BoxNode hierarchy tree from an in-memory byte array.
     */
    fun parseBoxHierarchy(data: ByteArray): List<BoxNode> {
        return parse(data).boxHierarchy
    }

    private fun parseBoxTree(
        raf: RandomAccessFile,
        startOffset: Long,
        endOffset: Long,
        state: BoxParseState,
        depth: Int
    ): List<BoxNode> {
        val nodes = mutableListOf<BoxNode>()
        var pos = startOffset
        val header = ByteArray(8)

        while (pos + 8 <= endOffset) {
            raf.seek(pos)
            val read = raf.read(header)
            if (read < 8) break

            val size32 = ((header[0].toLong() and 0xFF) shl 24) or
                ((header[1].toLong() and 0xFF) shl 16) or
                ((header[2].toLong() and 0xFF) shl 8) or
                (header[3].toLong() and 0xFF)

            val type = String(header, 4, 4, Charsets.US_ASCII)

            var boxHeaderSize = 8L
            var boxPayloadSize: Long
            var boxTotalSize: Long

            if (size32 == 1L) {
                // 64-bit extended size
                if (pos + 16 > endOffset) {
                    state.warnings.add("Truncated extended box header at offset $pos for box '$type'")
                    break
                }
                val extSizeBuf = ByteArray(8)
                val extRead = raf.read(extSizeBuf)
                if (extRead < 8) break
                val size64 = ByteBuffer.wrap(extSizeBuf).order(ByteOrder.BIG_ENDIAN).long
                if (size64 < 16L) {
                    state.warnings.add("Invalid 64-bit box size ($size64) for box '$type' at offset $pos")
                    break
                }
                boxHeaderSize = 16L
                boxTotalSize = size64
                boxPayloadSize = size64 - boxHeaderSize
            } else if (size32 == 0L) {
                // Box extends to container end / EOF
                boxTotalSize = (endOffset - pos).coerceAtLeast(boxHeaderSize)
                boxPayloadSize = (boxTotalSize - boxHeaderSize).coerceAtLeast(0L)
            } else {
                if (size32 < 8L) {
                    state.warnings.add("Invalid box size ($size32) for box '$type' at offset $pos")
                    break
                }
                boxTotalSize = size32
                boxPayloadSize = size32 - boxHeaderSize
            }

            // Overflow hazard check
            if (pos > Long.MAX_VALUE - boxTotalSize) {
                state.warnings.add("Integer overflow hazard at offset $pos for box '$type'")
                break
            }

            var nextBoxPos = pos + boxTotalSize

            // Parent/child bounds validation
            if (nextBoxPos > endOffset) {
                state.warnings.add("Box '$type' at offset $pos declares size $boxTotalSize exceeding container boundary $endOffset")
                boxTotalSize = endOffset - pos
                boxPayloadSize = (boxTotalSize - boxHeaderSize).coerceAtLeast(0L)
                nextBoxPos = endOffset
            }

            val boxDataStart = pos + boxHeaderSize
            var children = emptyList<BoxNode>()

            if (type == "moov") {
                state.hasMoov = true
                if (depth < MAX_RECURSION_DEPTH) {
                    children = parseBoxTree(raf, boxDataStart, nextBoxPos, state, depth + 1)
                }
            } else if (type in CONTAINER_TYPES) {
                if (depth < MAX_RECURSION_DEPTH) {
                    children = parseBoxTree(raf, boxDataStart, nextBoxPos, state, depth + 1)
                }
            } else if (type == "stsd") {
                // Sample Description Box: 4 bytes version/flags + 4 bytes entry count
                if (boxPayloadSize >= 8 && depth < MAX_RECURSION_DEPTH) {
                    children = parseStsdEntries(raf, boxDataStart + 8, nextBoxPos, state, depth + 1)
                }
            } else if (type == "hvc1" || type == "hev1" || type == "avc1") {
                state.videoCodecFourCc = type
                // VisualSampleEntry has 78 bytes of fixed fields after 8-byte box header = 86 bytes
                val childStart = pos + 86L
                if (childStart < nextBoxPos && depth < MAX_RECURSION_DEPTH) {
                    children = parseBoxTree(raf, childStart, nextBoxPos, state, depth + 1)
                }
            } else if (type == "mp4a") {
                // AudioSampleEntry has 28 bytes of fixed fields after 8-byte box header = 36 bytes
                val childStart = pos + 36L
                if (childStart < nextBoxPos && depth < MAX_RECURSION_DEPTH) {
                    children = parseBoxTree(raf, childStart, nextBoxPos, state, depth + 1)
                }
            } else {
                // Leaf boxes: parse specific payloads if needed
                when (type) {
                    "ftyp" -> if (state.ftypBox == null) state.ftypBox = parseFtypPayload(raf, boxDataStart, boxPayloadSize)
                    "colr" -> if (state.colrBox == null) state.colrBox = parseColrPayload(raf, boxDataStart, boxPayloadSize)
                    "mdcv" -> if (state.mdcvBox == null) state.mdcvBox = parseMdcvPayload(raf, boxDataStart, boxPayloadSize)
                    "clli" -> if (state.clliBox == null) state.clliBox = parseClliPayload(raf, boxDataStart, boxPayloadSize)
                    "hvcC" -> if (state.hvcCBox == null) state.hvcCBox = parseHvcCPayload(raf, boxDataStart, boxPayloadSize)
                    "avcC" -> if (state.avcCBox == null) state.avcCBox = parseAvcCPayload(raf, boxDataStart, boxPayloadSize)
                    "tkhd" -> parseTkhdPayload(raf, boxDataStart, boxPayloadSize)?.let { state.trackHeaders.add(it) }
                }
            }

            nodes.add(BoxNode(type, pos, boxTotalSize, getBoxDescription(type), children))
            pos = nextBoxPos
        }

        return nodes
    }

    private fun parseStsdEntries(
        raf: RandomAccessFile,
        startOffset: Long,
        endOffset: Long,
        state: BoxParseState,
        depth: Int
    ): List<BoxNode> {
        val entryNodes = mutableListOf<BoxNode>()
        var pos = startOffset
        val header = ByteArray(8)

        while (pos + 8 <= endOffset) {
            raf.seek(pos)
            val read = raf.read(header)
            if (read < 8) break

            val size = ((header[0].toLong() and 0xFF) shl 24) or
                ((header[1].toLong() and 0xFF) shl 16) or
                ((header[2].toLong() and 0xFF) shl 8) or
                (header[3].toLong() and 0xFF)
            val format = String(header, 4, 4, Charsets.US_ASCII)

            if (size < 8) break
            var entryEnd = pos + size
            if (entryEnd > endOffset) {
                entryEnd = endOffset
            }

            var entryChildren = emptyList<BoxNode>()

            if (format == "hev1" || format == "hvc1" || format == "avc1") {
                state.videoCodecFourCc = format
                // VisualSampleEntry has 78 bytes of fixed fields after 8-byte box header = 86 bytes
                val childStart = pos + 86L
                if (childStart < entryEnd && depth < MAX_RECURSION_DEPTH) {
                    entryChildren = parseBoxTree(raf, childStart, entryEnd, state, depth + 1)
                }
            } else if (format == "mp4a") {
                val childStart = pos + 36L
                if (childStart < entryEnd && depth < MAX_RECURSION_DEPTH) {
                    entryChildren = parseBoxTree(raf, childStart, entryEnd, state, depth + 1)
                }
            }

            entryNodes.add(BoxNode(format, pos, size, getBoxDescription(format), entryChildren))
            pos = entryEnd
        }

        return entryNodes
    }

    private fun buildReport(state: BoxParseState, boxNodes: List<BoxNode>): Mp4MetadataReport {
        var colrBox = state.colrBox
        var mdcvBox = state.mdcvBox
        var clliBox = state.clliBox
        val hvcCBox = state.hvcCBox

        // If elementary stream parameter sets were packed inside hvcC NALs, cross-extract them
        if (hvcCBox != null && hvcCBox.nalUnits.isNotEmpty()) {
            val bitstreamReport = HevcBitstreamParser.parseNalUnits(hvcCBox.nalUnits)
            if (colrBox == null && bitstreamReport.spsList.isNotEmpty()) {
                val sps = bitstreamReport.spsList[0]
                if (sps.hasVui && sps.colorPrimaries != null) {
                    colrBox = Mp4ColrBox(
                        colorType = "nclx",
                        colorPrimaries = sps.colorPrimaries,
                        transferCharacteristics = sps.transferCharacteristics ?: 0,
                        matrixCoefficients = sps.matrixCoefficients ?: 0,
                        fullRangeFlag = sps.fullRange ?: false
                    )
                }
            }
            if (mdcvBox == null && bitstreamReport.mdcvSeiList.isNotEmpty()) {
                val mdcv = bitstreamReport.mdcvSeiList[0]
                mdcvBox = Mp4MdcvBox(
                    greenX = mdcv.displayPrimariesGx,
                    greenY = mdcv.displayPrimariesGy,
                    blueX = mdcv.displayPrimariesBx,
                    blueY = mdcv.displayPrimariesBy,
                    redX = mdcv.displayPrimariesRx,
                    redY = mdcv.displayPrimariesRy,
                    whiteX = mdcv.whitePointX,
                    whiteY = mdcv.whitePointY,
                    maxMasteringLuminance = mdcv.maxMasteringLuminance,
                    minMasteringLuminance = mdcv.minMasteringLuminance
                )
            }
            if (clliBox == null && bitstreamReport.clliSeiList.isNotEmpty()) {
                val clli = bitstreamReport.clliSeiList[0]
                clliBox = Mp4ClliBox(
                    maxCll = clli.maxCll,
                    maxFall = clli.maxFall
                )
            }
        }

        val isMain10 = hvcCBox?.profileIdc == 2 || hvcCBox?.bitDepthLuma == 10
        val isBt2020 = colrBox?.colorPrimaries == 9
        val isSmpte2084 = colrBox?.transferCharacteristics == 16
        val isGenuineHdr10 = isMain10 && isBt2020 && isSmpte2084 && mdcvBox != null

        return Mp4MetadataReport(
            hasMoov = state.hasMoov,
            videoCodecFourCc = state.videoCodecFourCc,
            colrBox = colrBox,
            mdcvBox = mdcvBox,
            clliBox = clliBox,
            hvcCBox = hvcCBox,
            isMain10 = isMain10,
            isBt2020 = isBt2020,
            isSmpte2084 = isSmpte2084,
            isGenuineHdr10 = isGenuineHdr10,
            warnings = state.warnings,
            boxHierarchy = boxNodes,
            ftypBox = state.ftypBox,
            avcCBox = state.avcCBox,
            trackHeaders = state.trackHeaders
        )
    }

    private fun parseFtypPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4FtypBox? {
        if (size < 8) return null
        val readLen = Math.min(size, 1024L).toInt()
        raf.seek(offset)
        val data = ByteArray(readLen)
        val bytesRead = raf.read(data)
        if (bytesRead < 8) return null

        val majorBrand = String(data, 0, 4, Charsets.US_ASCII)
        val minorVersion = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL

        val compatibleBrands = mutableListOf<String>()
        var p = 8
        while (p + 4 <= bytesRead) {
            compatibleBrands.add(String(data, p, 4, Charsets.US_ASCII))
            p += 4
        }

        return Mp4FtypBox(
            majorBrand = majorBrand,
            minorVersion = minorVersion,
            compatibleBrands = compatibleBrands
        )
    }

    private fun parseColrPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4ColrBox? {
        if (size < 4) return null
        raf.seek(offset)
        val typeBuf = ByteArray(4)
        raf.read(typeBuf)
        val colorType = String(typeBuf, Charsets.US_ASCII)

        if (colorType == "nclx" && size >= 11) {
            val data = ByteArray(7)
            raf.read(data)
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val primaries = buf.short.toInt() and 0xFFFF
            val transfer = buf.short.toInt() and 0xFFFF
            val matrix = buf.short.toInt() and 0xFFFF
            val fullRange = (buf.get().toInt() and 0x80) != 0

            return Mp4ColrBox(
                colorType = colorType,
                colorPrimaries = primaries,
                transferCharacteristics = transfer,
                matrixCoefficients = matrix,
                fullRangeFlag = fullRange
            )
        }

        return Mp4ColrBox(
            colorType = colorType,
            colorPrimaries = 0,
            transferCharacteristics = 0,
            matrixCoefficients = 0,
            fullRangeFlag = false
        )
    }

    private fun parseMdcvPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4MdcvBox? {
        if (size < 24) return null
        raf.seek(offset)
        val data = ByteArray(24)
        raf.read(data)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        return Mp4MdcvBox(
            greenX = buf.short.toInt() and 0xFFFF,
            greenY = buf.short.toInt() and 0xFFFF,
            blueX = buf.short.toInt() and 0xFFFF,
            blueY = buf.short.toInt() and 0xFFFF,
            redX = buf.short.toInt() and 0xFFFF,
            redY = buf.short.toInt() and 0xFFFF,
            whiteX = buf.short.toInt() and 0xFFFF,
            whiteY = buf.short.toInt() and 0xFFFF,
            maxMasteringLuminance = buf.int.toLong() and 0xFFFFFFFFL,
            minMasteringLuminance = buf.int.toLong() and 0xFFFFFFFFL
        )
    }

    private fun parseClliPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4ClliBox? {
        if (size < 4) return null
        raf.seek(offset)
        val data = ByteArray(4)
        raf.read(data)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        return Mp4ClliBox(
            maxCll = buf.short.toInt() and 0xFFFF,
            maxFall = buf.short.toInt() and 0xFFFF
        )
    }

    private fun parseHvcCPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4HvcCBox? {
        if (size < 23) return null
        raf.seek(offset)
        val header = ByteArray(23)
        raf.read(header)

        val version = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val profileSpace = (b1 ushr 6) and 0x03
        val tierFlag = (b1 and 0x20) != 0
        val profileIdc = b1 and 0x1F

        val compFlags = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val levelIdc = header[12].toInt() and 0xFF

        val bitDepthLuma = (header[17].toInt() and 0x07) + 8
        val bitDepthChroma = (header[18].toInt() and 0x07) + 8
        val numArrays = header[22].toInt() and 0xFF

        val nalUnits = mutableListOf<ByteArray>()
        for (a in 0 until numArrays) {
            val arrHeader = ByteArray(3)
            val read = raf.read(arrHeader)
            if (read < 3) break
            val numNalus = ((arrHeader[1].toInt() and 0xFF) shl 8) or (arrHeader[2].toInt() and 0xFF)

            for (n in 0 until numNalus) {
                val lenBytes = ByteArray(2)
                val lenRead = raf.read(lenBytes)
                if (lenRead < 2) break
                val nalLen = ((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)
                if (nalLen > 0) {
                    val nal = ByteArray(nalLen)
                    raf.read(nal)
                    nalUnits.add(nal)
                }
            }
        }

        return Mp4HvcCBox(
            configurationVersion = version,
            profileSpace = profileSpace,
            tierFlag = tierFlag,
            profileIdc = profileIdc,
            profileCompatibilityFlags = compFlags,
            levelIdc = levelIdc,
            bitDepthLuma = bitDepthLuma,
            bitDepthChroma = bitDepthChroma,
            nalUnits = nalUnits
        )
    }

    private fun parseAvcCPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4AvcCBox? {
        if (size < 7) return null
        val readLen = Math.min(size, 65536L).toInt()
        raf.seek(offset)
        val data = ByteArray(readLen)
        val bytesRead = raf.read(data)
        if (bytesRead < 7) return null

        val version = data[0].toInt() and 0xFF
        val profile = data[1].toInt() and 0xFF
        val compat = data[2].toInt() and 0xFF
        val level = data[3].toInt() and 0xFF
        val lengthSizeMinusOne = data[4].toInt() and 0x03
        val numSps = data[5].toInt() and 0x1F

        val spsList = mutableListOf<ByteArray>()
        var p = 6
        for (i in 0 until numSps) {
            if (p + 2 > bytesRead) break
            val spsLen = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
            p += 2
            if (p + spsLen > bytesRead) break
            spsList.add(data.copyOfRange(p, p + spsLen))
            p += spsLen
        }

        val ppsList = mutableListOf<ByteArray>()
        if (p < bytesRead) {
            val numPps = data[p].toInt() and 0xFF
            p += 1
            for (i in 0 until numPps) {
                if (p + 2 > bytesRead) break
                val ppsLen = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
                p += 2
                if (p + ppsLen > bytesRead) break
                ppsList.add(data.copyOfRange(p, p + ppsLen))
                p += ppsLen
            }
        }

        return Mp4AvcCBox(
            configurationVersion = version,
            avcProfileIndication = profile,
            profileCompatibility = compat,
            avcLevelIndication = level,
            lengthSizeMinusOne = lengthSizeMinusOne,
            spsNals = spsList,
            ppsNals = ppsList
        )
    }

    private fun parseTkhdPayload(raf: RandomAccessFile, offset: Long, size: Long): Mp4TrackHeader? {
        if (size < 84) return null
        val readLen = Math.min(size, 120L).toInt()
        raf.seek(offset)
        val data = ByteArray(readLen)
        val bytesRead = raf.read(data)
        if (bytesRead < 84) return null

        val version = data[0].toInt() and 0xFF
        val trackId: Int
        val duration: Long
        val widthFixed: Int
        val heightFixed: Int

        if (version == 1) {
            if (bytesRead < 96) return null
            trackId = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).int
            duration = ByteBuffer.wrap(data, 24, 8).order(ByteOrder.BIG_ENDIAN).long
            widthFixed = ByteBuffer.wrap(data, 88, 4).order(ByteOrder.BIG_ENDIAN).int
            heightFixed = ByteBuffer.wrap(data, 92, 4).order(ByteOrder.BIG_ENDIAN).int
        } else {
            trackId = ByteBuffer.wrap(data, 12, 4).order(ByteOrder.BIG_ENDIAN).int
            duration = ByteBuffer.wrap(data, 20, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            widthFixed = ByteBuffer.wrap(data, 76, 4).order(ByteOrder.BIG_ENDIAN).int
            heightFixed = ByteBuffer.wrap(data, 80, 4).order(ByteOrder.BIG_ENDIAN).int
        }

        val width = (widthFixed ushr 16).toDouble() + ((widthFixed and 0xFFFF) / 65536.0)
        val height = (heightFixed ushr 16).toDouble() + ((heightFixed and 0xFFFF) / 65536.0)

        return Mp4TrackHeader(
            trackId = trackId,
            width = width,
            height = height,
            duration = duration,
            isVideo = width > 0 && height > 0,
            isAudio = width == 0.0 && height == 0.0,
            handlerType = null
        )
    }

    fun getBoxDescription(type: String): String = when (type) {
        "ftyp" -> "File Type Box (specifies container brands and compatibility)"
        "moov" -> "Movie Box (container for all presentation metadata)"
        "mvhd" -> "Movie Header Box (presentation duration, timescale, and default volume)"
        "trak" -> "Track Box (container for individual track metadata)"
        "tkhd" -> "Track Header Box (track characteristics, dimensions, and playback matrix)"
        "edts" -> "Edit Box (container for edit lists)"
        "elst" -> "Edit List Box (maps presentation timeline to media timeline)"
        "mdia" -> "Media Box (container for media declarations within track)"
        "mdhd" -> "Media Header Box (media timescale, duration, and language code)"
        "hdlr" -> "Handler Reference Box (declares media handler type: vide, soun, subt)"
        "minf" -> "Media Information Box (format-specific track handler tables)"
        "vmhd" -> "Video Media Header Box (video composition modes)"
        "smhd" -> "Sound Media Header Box (sound track balance and characteristics)"
        "dinf" -> "Data Information Box (declares location of media sample data)"
        "dref" -> "Data Reference Box (data table entries / data references)"
        "stbl" -> "Sample Table Box (indexes timing, chunks, sizes, and sample entries)"
        "stsd" -> "Sample Description Box (codec configuration records and visual/audio sample entries)"
        "stts" -> "Decoding Time to Sample Box (table of sample durations)"
        "stss" -> "Sync Sample Box (indexes random access IDR keyframes)"
        "ctts" -> "Composition Time to Sample Box (presentation timestamp offsets)"
        "stsc" -> "Sample To Chunk Box (maps samples to chunk groups)"
        "stsz" -> "Sample Size Box (individual sample byte sizes)"
        "stco" -> "Chunk Offset Box (32-bit offsets to chunk data in mdat)"
        "co64" -> "Chunk Large Offset Box (64-bit offsets to chunk data in mdat)"
        "hvc1" -> "HEVC Visual Sample Entry (in-band parameter sets)"
        "hev1" -> "HEVC Visual Sample Entry (out-of-band parameter sets)"
        "hvcC" -> "HEVC Configuration Box (HEVCDecoderConfigurationRecord with VPS/SPS/PPS)"
        "avc1" -> "AVC Visual Sample Entry (H.264 video stream definition)"
        "avcC" -> "AVC Configuration Box (AVCDecoderConfigurationRecord with SPS/PPS)"
        "colr" -> "Color Parameter Box (nclx primaries, transfer curve, matrix, and full-range flag)"
        "mdcv" -> "Mastering Display Color Volume Box (SMPTE ST 2086 reference monitor optics)"
        "clli" -> "Content Light Level Information Box (CTA-861.3 MaxCLL / MaxFALL light levels)"
        "mp4a" -> "MPEG-4 Audio Sample Entry (AAC audio stream definition)"
        "esds" -> "Elementary Stream Descriptor Box (DecoderConfigDescriptor for audio)"
        "Opus" -> "Opus Audio Sample Entry"
        "dOps" -> "Opus Specific Box (channel count, pre-skip, sample rate)"
        "tx3g" -> "Timed Text Subtitle Sample Entry"
        "wvtt" -> "WebVTT Subtitle Sample Entry"
        "udta" -> "User Data Box (container for user/creation tags)"
        "meta" -> "Metadata Box (container for metadata items and keys)"
        "ilst" -> "iTunes Metadata Item List"
        "mdat" -> "Media Data Box (raw encoded audio/video/subtitle elementary payloads)"
        "free" -> "Free Space Box (padding space for header modification)"
        "skip" -> "Skip Box (free space padding)"
        else -> "Box '$type'"
    }
}
