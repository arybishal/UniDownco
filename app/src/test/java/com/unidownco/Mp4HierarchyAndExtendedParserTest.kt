package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.parser.Mp4MetadataParser
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mp4HierarchyAndExtendedParserTest {

    @Test
    fun testRealFixtureA_BoxHierarchyAndFtypParsed() {
        val file = File("src/test/resources/fixtures/fixture_a_hdr10_main10_true2020.mp4").let {
            if (it.exists()) it else File("app/src/test/resources/fixtures/fixture_a_hdr10_main10_true2020.mp4")
        }
        assertThat(file.exists()).isTrue()

        val report = Mp4MetadataParser.parse(file)
        assertThat(report.hasMoov).isTrue()
        assertThat(report.boxHierarchy).isNotEmpty()

        // Verify root boxes contain ftyp and moov
        val types = report.boxHierarchy.map { it.type }
        assertThat(types).contains("ftyp")
        assertThat(types).contains("moov")

        // Verify ftyp details
        assertThat(report.ftypBox).isNotNull()
        val ftyp = report.ftypBox!!
        assertThat(ftyp.majorBrand).isNotEmpty()

        // Verify hierarchical nesting inside moov
        val moovNode = report.boxHierarchy.first { it.type == "moov" }
        assertThat(moovNode.isContainer).isTrue()
        val moovChildTypes = moovNode.children.map { it.type }
        assertThat(moovChildTypes).contains("trak")

        // Verify deep nesting into stsd and hev1
        val allDescendantTypes = mutableListOf<String>()
        fun collectTypes(nodes: List<com.unidownco.engine.inspector.model.BoxNode>) {
            for (node in nodes) {
                allDescendantTypes.add(node.type)
                collectTypes(node.children)
            }
        }
        collectTypes(report.boxHierarchy)

        assertThat(allDescendantTypes).contains("hvcC")
        assertThat(report.colrBox).isNotNull()
        assertThat(report.isGenuineHdr10).isTrue()
    }

    @Test
    fun testParseBoxHierarchyConvenienceMethod() {
        val file = File("src/test/resources/fixtures/fixture_c_sdr_main_8bit.mp4").let {
            if (it.exists()) it else File("app/src/test/resources/fixtures/fixture_c_sdr_main_8bit.mp4")
        }
        assertThat(file.exists()).isTrue()

        val hierarchy = Mp4MetadataParser.parseBoxHierarchy(file)
        assertThat(hierarchy).isNotEmpty()
        assertThat(hierarchy.any { it.type == "ftyp" }).isTrue()
        assertThat(hierarchy.any { it.type == "moov" }).isTrue()
    }

    @Test
    fun testSyntheticExtended64BitBoxParsing() {
        // Build a synthetic box with size32 = 1, followed by 8-byte 64-bit size = 24L, type = "test"
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(1) // size 1 indicates extended 64-bit size
        buffer.put("extb".toByteArray(Charsets.US_ASCII))
        buffer.putLong(24L) // 64-bit size
        buffer.put("12345678".toByteArray(Charsets.US_ASCII)) // 8-byte payload

        val hierarchy = Mp4MetadataParser.parseBoxHierarchy(buffer.array())
        assertThat(hierarchy).hasSize(1)
        val node = hierarchy[0]
        assertThat(node.type).isEqualTo("extb")
        assertThat(node.offset).isEqualTo(0L)
        assertThat(node.size).isEqualTo(24L)
    }

    @Test
    fun testSyntheticSizeZeroBoxExtendsToEof() {
        // Size 0 indicates box extends to EOF
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0) // size 0
        buffer.put("zero".toByteArray(Charsets.US_ASCII))
        buffer.put(ByteArray(24) { 0x42 }) // remainder of file

        val hierarchy = Mp4MetadataParser.parseBoxHierarchy(buffer.array())
        assertThat(hierarchy).hasSize(1)
        val node = hierarchy[0]
        assertThat(node.type).isEqualTo("zero")
        assertThat(node.size).isEqualTo(32L) // extends to full 32 bytes
    }

    @Test
    fun testUnknownBoxesHandledGracefullyWithoutCrashing() {
        // Box 1: "unk1" size 12
        // Box 2: "unk2" size 16
        val buffer = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(12)
        buffer.put("unk1".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0x11223344)

        buffer.putInt(16)
        buffer.put("unk2".toByteArray(Charsets.US_ASCII))
        buffer.putLong(0x1234567890ABCDEFL)

        val hierarchy = Mp4MetadataParser.parseBoxHierarchy(buffer.array())
        assertThat(hierarchy).hasSize(2)
        assertThat(hierarchy[0].type).isEqualTo("unk1")
        assertThat(hierarchy[0].description).contains("unk1")
        assertThat(hierarchy[1].type).isEqualTo("unk2")
        assertThat(hierarchy[1].description).contains("unk2")
    }

    @Test
    fun testParentBoundaryViolation_ClampedSafelyWithWarning() {
        // moov declares size 24 (header 8 + 16 payload)
        // child declares size 32 (exceeding parent by 16 bytes!)
        val buffer = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(24) // moov size 24
        buffer.put("moov".toByteArray(Charsets.US_ASCII))
        // child at offset 8:
        buffer.putInt(32) // declares 32, which would go to offset 40 (beyond moov end 24!)
        buffer.put("trak".toByteArray(Charsets.US_ASCII))
        buffer.put(ByteArray(24) { 0x01 })

        val report = Mp4MetadataParser.parse(buffer.array())
        assertThat(report.hasMoov).isTrue()
        // Must emit warning about child exceeding container boundary
        assertThat(report.warnings.any { it.contains("exceeding container boundary") }).isTrue()
    }

    @Test
    fun testTruncatedHeaders_GracefulTermination() {
        // Truncated file with only 4 bytes (needs at least 8 for box header)
        val truncated = byteArrayOf(0x00, 0x00, 0x00, 0x20)
        val report = Mp4MetadataParser.parse(truncated)
        assertThat(report.hasMoov).isFalse()
        assertThat(report.boxHierarchy).isEmpty()

        // Truncated 64-bit header (size = 1, type = "test", but only 2 bytes of extended size instead of 8)
        val truncatedExt = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
        truncatedExt.putInt(1)
        truncatedExt.put("test".toByteArray(Charsets.US_ASCII))
        truncatedExt.putShort(0x0001)

        val reportExt = Mp4MetadataParser.parse(truncatedExt.array())
        assertThat(reportExt.warnings.any { it.contains("Truncated extended box header") }).isTrue()
    }

    @Test
    fun testAvcCBoxParsing() {
        // Construct a synthetic avcC box:
        // size: 19 bytes
        // type: "avcC"
        // configVersion: 1
        // profile: 100 (High)
        // compat: 0
        // level: 41 (Level 4.1)
        // lengthSizeMinusOne: 3 (0xFF)
        // numSps: 1 (0xE1)
        // spsLen: 4
        // sps bytes: 0x67, 0x64, 0x00, 0x29
        // numPps: 1
        // ppsLen: 2
        // pps bytes: 0x68, 0xEE
        val totalSize = 8 + 19
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(totalSize)
        buffer.put("avcC".toByteArray(Charsets.US_ASCII))
        buffer.put(1.toByte()) // version
        buffer.put(100.toByte()) // profile High
        buffer.put(0.toByte()) // compat
        buffer.put(41.toByte()) // level 4.1
        buffer.put(0xFF.toByte()) // lengthSizeMinusOne = 3
        buffer.put(0xE1.toByte()) // 1 SPS
        buffer.putShort(4.toShort()) // SPS length
        buffer.put(byteArrayOf(0x67, 0x64, 0x00, 0x29))
        buffer.put(1.toByte()) // 1 PPS
        buffer.putShort(2.toShort()) // PPS length
        buffer.put(byteArrayOf(0x68, 0xEE.toByte()))

        val report = Mp4MetadataParser.parse(buffer.array())
        assertThat(report.avcCBox).isNotNull()
        val avcC = report.avcCBox!!
        assertThat(avcC.configurationVersion).isEqualTo(1)
        assertThat(avcC.avcProfileIndication).isEqualTo(100)
        assertThat(avcC.avcLevelIndication).isEqualTo(41)
        assertThat(avcC.lengthSizeMinusOne).isEqualTo(3)
        assertThat(avcC.spsNals).hasSize(1)
        assertThat(avcC.spsNals[0]).isEqualTo(byteArrayOf(0x67, 0x64, 0x00, 0x29))
        assertThat(avcC.ppsNals).hasSize(1)
        assertThat(avcC.ppsNals[0]).isEqualTo(byteArrayOf(0x68, 0xEE.toByte()))
    }
}
