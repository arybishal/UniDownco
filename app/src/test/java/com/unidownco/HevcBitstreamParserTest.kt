package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.HdrContentLightLevel
import com.unidownco.engine.hdr.HdrMasteringDisplay
import com.unidownco.engine.parser.HevcBitstreamParser
import com.unidownco.engine.parser.HevcSeiMessage
import com.unidownco.engine.parser.Mp4MetadataParser
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HevcBitstreamParserTest {

    @Test
    fun testAnnexBStartCodeExtraction_MultipleNals() {
        val nal1 = byteArrayOf(0x40, 0x01, 0x0C, 0x01) // VPS
        val nal2 = byteArrayOf(0x42, 0x01, 0x01, 0x02, 0x03) // SPS
        val nal3 = byteArrayOf(0x4E, 0x01, 0x89.toByte(), 0x00) // Prefix SEI

        // Build Annex B stream with mix of 3-byte and 4-byte start codes
        val stream = ByteBuffer.allocate(64)
        // 4-byte start code
        stream.put(byteArrayOf(0, 0, 0, 1))
        stream.put(nal1)
        // 3-byte start code
        stream.put(byteArrayOf(0, 0, 1))
        stream.put(nal2)
        // 4-byte start code
        stream.put(byteArrayOf(0, 0, 0, 1))
        stream.put(nal3)

        val extracted = HevcBitstreamParser.extractAnnexBNalUnits(stream.array().copyOf(stream.position()))
        assertThat(extracted).hasSize(3)
        assertThat(extracted[0]).isEqualTo(nal1)
        assertThat(extracted[1]).isEqualTo(nal2)
        assertThat(extracted[2]).isEqualTo(nal3)

        assertThat(HevcBitstreamParser.getNalUnitType(extracted[0])).isEqualTo(HevcBitstreamParser.NAL_VPS)
        assertThat(HevcBitstreamParser.getNalUnitType(extracted[1])).isEqualTo(HevcBitstreamParser.NAL_SPS)
        assertThat(HevcBitstreamParser.getNalUnitType(extracted[2])).isEqualTo(HevcBitstreamParser.NAL_PREFIX_SEI)
    }

    @Test
    fun testUnescapeEmulationPrevention() {
        // In HEVC, 0x000003 followed by 0x00, 0x01, 0x02, or 0x03 is an emulation prevention sequence
        val rawWithEpb = byteArrayOf(0x00, 0x00, 0x03, 0x02, 0x10, 0x20, 0x00, 0x00, 0x03, 0x03)
        val unescaped = HevcBitstreamParser.unescapeEmulationPrevention(rawWithEpb)
        assertThat(unescaped).isEqualTo(byteArrayOf(0x00, 0x00, 0x02, 0x10, 0x20, 0x00, 0x00, 0x03))
    }

    @Test
    fun testPrefixSei_MdcvAndClliParsing() {
        val display = HdrMasteringDisplay.BT2020_TRUE_1000NITS
        val sei137Nal = display.toHevcSeiNalUnit()

        val cll = HdrContentLightLevel.createMathematicallyEstimated(1000, 400)
        val sei144Nal = cll.toHevcSeiNalUnit()!!

        // Concatenate into single Annex B stream (toHevcSeiNalUnit already includes 4-byte start code)
        val annexB = ByteBuffer.allocate(sei137Nal.size + sei144Nal.size)
        annexB.put(sei137Nal)
        annexB.put(sei144Nal)

        val report = HevcBitstreamParser.parseAnnexB(annexB.array().copyOf(annexB.position()))
        assertThat(report.hasStaticMetadata).isTrue()
        assertThat(report.mdcvSeiList).hasSize(1)
        assertThat(report.clliSeiList).hasSize(1)

        val mdcv = report.mdcvSeiList[0]
        assertThat(mdcv.displayPrimariesGx).isEqualTo(8500)
        assertThat(mdcv.displayPrimariesGy).isEqualTo(39850)
        assertThat(mdcv.displayPrimariesBx).isEqualTo(6550)
        assertThat(mdcv.displayPrimariesBy).isEqualTo(2300)
        assertThat(mdcv.displayPrimariesRx).isEqualTo(35400)
        assertThat(mdcv.displayPrimariesRy).isEqualTo(14600)
        assertThat(mdcv.whitePointX).isEqualTo(15635)
        assertThat(mdcv.whitePointY).isEqualTo(16450)
        assertThat(mdcv.maxMasteringLuminance).isEqualTo(10_000_000L)
        assertThat(mdcv.minMasteringLuminance).isEqualTo(1L)

        val clli = report.clliSeiList[0]
        assertThat(clli.maxCll).isEqualTo(1000)
        assertThat(clli.maxFall).isEqualTo(400)
    }

    @Test
    fun testMp4HvcCBoxParsing() {
        // Construct synthetic hvcC box (23 bytes header + 1 array)
        val nalUnit = byteArrayOf(0x42, 0x01, 0xAA.toByte(), 0xBB.toByte()) // Dummy SPS
        val hvcCSize = 8 + 23 + 1 + 3 + 2 + nalUnit.size
        val buf = ByteBuffer.allocate(hvcCSize).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(hvcCSize)
        buf.put("hvcC".toByteArray(Charsets.US_ASCII))

        buf.put(1.toByte()) // configurationVersion = 1
        buf.put(0x22.toByte()) // profile_space = 0, tier = 1, profile_idc = 2 (Main 10)
        buf.putInt(0x60000000) // compatibility flags
        buf.put(byteArrayOf(0, 0, 0, 0, 0, 0)) // constraint indicator flags (6 bytes)
        buf.put(120.toByte()) // level_idc = 120 (Level 4.0)
        buf.putShort(0) // min_spatial_segmentation_idc
        buf.put(0.toByte()) // parallelismType
        buf.put(0x01.toByte()) // chromaFormat = 1 (4:2:0)
        buf.put(0x02.toByte()) // bitDepthLumaMinus8 = 2 (10-bit)
        buf.put(0x02.toByte()) // bitDepthChromaMinus8 = 2 (10-bit)
        buf.putShort(0) // avgFrameRate
        buf.put(0x0F.toByte()) // constantFrameRate, etc.

        buf.put(1.toByte()) // numOfArrays = 1
        // Array 0
        buf.put(0xA1.toByte()) // array_completeness=1, NAL_unit_type = 33 (SPS)
        buf.putShort(1) // numNalus = 1
        buf.putShort(nalUnit.size.toShort())
        buf.put(nalUnit)

        // Wrap inside moov -> trak -> mdia -> minf -> stbl
        val stblSize = 8 + hvcCSize
        val stblBuf = ByteBuffer.allocate(stblSize).order(ByteOrder.BIG_ENDIAN)
        stblBuf.putInt(stblSize).put("stbl".toByteArray(Charsets.US_ASCII)).put(buf.array())

        val minfSize = 8 + stblSize
        val minfBuf = ByteBuffer.allocate(minfSize).order(ByteOrder.BIG_ENDIAN)
        minfBuf.putInt(minfSize).put("minf".toByteArray(Charsets.US_ASCII)).put(stblBuf.array())

        val mdiaSize = 8 + minfSize
        val mdiaBuf = ByteBuffer.allocate(mdiaSize).order(ByteOrder.BIG_ENDIAN)
        mdiaBuf.putInt(mdiaSize).put("mdia".toByteArray(Charsets.US_ASCII)).put(minfBuf.array())

        val trakSize = 8 + mdiaSize
        val trakBuf = ByteBuffer.allocate(trakSize).order(ByteOrder.BIG_ENDIAN)
        trakBuf.putInt(trakSize).put("trak".toByteArray(Charsets.US_ASCII)).put(mdiaBuf.array())

        val moovSize = 8 + trakSize
        val moovBuf = ByteBuffer.allocate(moovSize).order(ByteOrder.BIG_ENDIAN)
        moovBuf.putInt(moovSize).put("moov".toByteArray(Charsets.US_ASCII)).put(trakBuf.array())

        val report = Mp4MetadataParser.parse(moovBuf.array())
        assertThat(report.hasMoov).isTrue()
        assertThat(report.hvcCBox).isNotNull()
        val hvcC = report.hvcCBox!!
        assertThat(hvcC.profileIdc).isEqualTo(2) // Main 10
        assertThat(hvcC.bitDepthLuma).isEqualTo(10)
        assertThat(hvcC.bitDepthChroma).isEqualTo(10)
        assertThat(hvcC.nalUnits).hasSize(1)
        assertThat(hvcC.nalUnits[0]).isEqualTo(nalUnit)
        assertThat(report.isMain10).isTrue()
    }
}
