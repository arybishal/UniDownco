package com.unidownco.engine.parser

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
    val warnings: List<String> = emptyList()
)

/**
 * Independent pure Kotlin parser for ISO Base Media File Format (MP4) boxes.
 *
 * Inspects:
 * - 'colr' (nclx color parameter box)
 * - 'mdcv' (mastering display color volume box)
 * - 'clli' (content light level information box)
 * - 'hvcC' (HEVC decoder configuration record)
 */
object Mp4MetadataParser {

    private val CONTAINER_TYPES = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")

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
            return parseBoxes(raf, 0L, raf.length())
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

    private fun parseBoxes(raf: RandomAccessFile, startOffset: Long, endOffset: Long): Mp4MetadataReport {
        var hasMoov = false
        var videoCodecFourCc: String? = null
        var colrBox: Mp4ColrBox? = null
        var mdcvBox: Mp4MdcvBox? = null
        var clliBox: Mp4ClliBox? = null
        var hvcCBox: Mp4HvcCBox? = null
        val warnings = mutableListOf<String>()

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

            if (size32 == 1L) {
                // 64-bit extended size
                val extSizeBuf = ByteArray(8)
                val extRead = raf.read(extSizeBuf)
                if (extRead < 8) break
                val size64 = ByteBuffer.wrap(extSizeBuf).order(ByteOrder.BIG_ENDIAN).long
                boxHeaderSize = 16L
                boxPayloadSize = size64 - boxHeaderSize
            } else if (size32 == 0L) {
                // Box extends to EOF
                boxPayloadSize = endOffset - pos - boxHeaderSize
            } else {
                boxPayloadSize = size32 - boxHeaderSize
            }

            if (boxPayloadSize < 0) break

            val boxDataStart = pos + boxHeaderSize
            val nextBoxPos = pos + boxHeaderSize + boxPayloadSize

            if (type == "moov") {
                hasMoov = true
                val subReport = parseBoxes(raf, boxDataStart, nextBoxPos)
                if (subReport.videoCodecFourCc != null) videoCodecFourCc = subReport.videoCodecFourCc
                if (subReport.colrBox != null) colrBox = subReport.colrBox
                if (subReport.mdcvBox != null) mdcvBox = subReport.mdcvBox
                if (subReport.clliBox != null) clliBox = subReport.clliBox
                if (subReport.hvcCBox != null) hvcCBox = subReport.hvcCBox
            } else if (type in CONTAINER_TYPES) {
                val subReport = parseBoxes(raf, boxDataStart, nextBoxPos)
                if (subReport.videoCodecFourCc != null) videoCodecFourCc = subReport.videoCodecFourCc
                if (subReport.colrBox != null) colrBox = subReport.colrBox
                if (subReport.mdcvBox != null) mdcvBox = subReport.mdcvBox
                if (subReport.clliBox != null) clliBox = subReport.clliBox
                if (subReport.hvcCBox != null) hvcCBox = subReport.hvcCBox
            } else if (type == "stsd") {
                // Sample Description Box: 4 bytes version/flags + 4 bytes entry count
                if (boxPayloadSize >= 8) {
                    val stsdSubReport = parseStsdBox(raf, boxDataStart + 8, nextBoxPos)
                    if (stsdSubReport.videoCodecFourCc != null) videoCodecFourCc = stsdSubReport.videoCodecFourCc
                    if (stsdSubReport.colrBox != null) colrBox = stsdSubReport.colrBox
                    if (stsdSubReport.mdcvBox != null) mdcvBox = stsdSubReport.mdcvBox
                    if (stsdSubReport.clliBox != null) clliBox = stsdSubReport.clliBox
                    if (stsdSubReport.hvcCBox != null) hvcCBox = stsdSubReport.hvcCBox
                }
            } else if (type == "colr") {
                colrBox = parseColrPayload(raf, boxDataStart, boxPayloadSize)
            } else if (type == "mdcv") {
                mdcvBox = parseMdcvPayload(raf, boxDataStart, boxPayloadSize)
            } else if (type == "clli") {
                clliBox = parseClliPayload(raf, boxDataStart, boxPayloadSize)
            } else if (type == "hvcC") {
                hvcCBox = parseHvcCPayload(raf, boxDataStart, boxPayloadSize)
            }

            pos = nextBoxPos
        }

        val isMain10 = hvcCBox?.profileIdc == 2 || hvcCBox?.bitDepthLuma == 10
        val isBt2020 = colrBox?.colorPrimaries == 9
        val isSmpte2084 = colrBox?.transferCharacteristics == 16
        val isGenuineHdr10 = isMain10 && isBt2020 && isSmpte2084 && mdcvBox != null

        return Mp4MetadataReport(
            hasMoov = hasMoov,
            videoCodecFourCc = videoCodecFourCc,
            colrBox = colrBox,
            mdcvBox = mdcvBox,
            clliBox = clliBox,
            hvcCBox = hvcCBox,
            isMain10 = isMain10,
            isBt2020 = isBt2020,
            isSmpte2084 = isSmpte2084,
            isGenuineHdr10 = isGenuineHdr10,
            warnings = warnings
        )
    }

    private fun parseStsdBox(raf: RandomAccessFile, startOffset: Long, endOffset: Long): Mp4MetadataReport {
        var pos = startOffset
        var videoCodecFourCc: String? = null
        var colrBox: Mp4ColrBox? = null
        var mdcvBox: Mp4MdcvBox? = null
        var clliBox: Mp4ClliBox? = null
        var hvcCBox: Mp4HvcCBox? = null

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
            val entryEnd = pos + size

            if (format == "hev1" || format == "hvc1" || format == "avc1") {
                videoCodecFourCc = format
                // VisualSampleEntry has 78 bytes of fixed fields after the 8-byte box header
                val childStart = pos + 8 + 78
                if (childStart < entryEnd) {
                    val childReport = parseBoxes(raf, childStart, entryEnd)
                    if (childReport.colrBox != null) colrBox = childReport.colrBox
                    if (childReport.mdcvBox != null) mdcvBox = childReport.mdcvBox
                    if (childReport.clliBox != null) clliBox = childReport.clliBox
                    if (childReport.hvcCBox != null) hvcCBox = childReport.hvcCBox
                }
            }

            pos = entryEnd
        }

        return Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = videoCodecFourCc,
            colrBox = colrBox,
            mdcvBox = mdcvBox,
            clliBox = clliBox,
            hvcCBox = hvcCBox,
            isMain10 = hvcCBox?.profileIdc == 2 || hvcCBox?.bitDepthLuma == 10,
            isBt2020 = colrBox?.colorPrimaries == 9,
            isSmpte2084 = colrBox?.transferCharacteristics == 16,
            isGenuineHdr10 = (hvcCBox?.profileIdc == 2) && (colrBox?.colorPrimaries == 9) && (colrBox.transferCharacteristics == 16) && mdcvBox != null
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
}
