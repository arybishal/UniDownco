package com.unidownco.engine.inspector.classifier

import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.inspector.model.ContentLightLevelReport
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.InspectionError
import com.unidownco.engine.inspector.model.InspectionWarning
import com.unidownco.engine.inspector.model.MasteringDisplayReport
import com.unidownco.engine.inspector.model.MetadataConflictAlert
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.inspector.model.WarningSeverity
import com.unidownco.engine.parser.HevcBitstreamReport
import com.unidownco.engine.parser.Mp4MetadataReport

/**
 * Default implementation of [HdrClassificationEngine].
 *
 * Implements strict, independently verified classification rules:
 * - Genuine HDR10 requires: 10-bit Main10 + BT.2020 primaries + ST 2084 PQ transfer + mastering display (MDCV/SEI 137).
 * - Partial HDR: 10-bit stream with incomplete signaling or missing optional MDCV/CLLI.
 * - SDR: BT.709, BT.601, or sRGB transfer with 8-bit precision.
 * - Fake HDR Rejection: 8-bit stream declaring HDR signaling (BT.2020/PQ) or contradictory bit depth claims.
 * - Malformed / Conflicting: Irreconcilable contradictions between container and bitstream, or physically invalid optical metadata.
 */
class HdrClassificationEngineImpl : HdrClassificationEngine {

    override fun evaluate(
        mp4Report: Mp4MetadataReport?,
        bitstreamReport: HevcBitstreamReport?,
        primaryVideo: VideoStreamDetails?
    ): HdrClassificationResult {
        val warnings = mutableListOf<InspectionWarning>()
        val errors = mutableListOf<InspectionError>()

        val colr = mp4Report?.colrBox
        val mdcvBox = mp4Report?.mdcvBox
        val clliBox = mp4Report?.clliBox

        val sps = bitstreamReport?.spsList?.firstOrNull()
        val bitstreamMdcv = bitstreamReport?.mdcvSeiList?.firstOrNull()
        val bitstreamClli = bitstreamReport?.clliSeiList?.firstOrNull()

        // 1. Bit depth determination
        val bitDepth = primaryVideo?.bitDepth
            ?: (sps?.bitDepthLuma
                ?: (mp4Report?.hvcCBox?.bitDepthLuma ?: 8))

        // 2. Colorimetry & transfer curve identification
        val primariesIdc = sps?.colorPrimaries ?: colr?.colorPrimaries
        val transferIdc = sps?.transferCharacteristics ?: colr?.transferCharacteristics
        val matrixIdc = sps?.matrixCoefficients ?: colr?.matrixCoefficients
        val fullRange = sps?.fullRange ?: colr?.fullRangeFlag

        // 3. Metadata conflict evaluation
        val conflicts = detectMetadataConflicts(mp4Report, bitstreamReport)
        for (conflict in conflicts) {
            warnings.add(
                InspectionWarning(
                    severity = WarningSeverity.WARNING,
                    code = "METADATA_CONFLICT",
                    message = "Metadata mismatch on ${conflict.fieldName}: container declares '${conflict.containerValue}' but bitstream declares '${conflict.bitstreamValue}'.",
                    technicalImpact = conflict.resolutionDescription
                )
            )
        }

        // 4. Optical mastering display evaluation & validation
        var hasMalformedOpticalMetadata = false
        val masteringDisplay = when {
            mdcvBox != null -> {
                val gx = mdcvBox.greenX / 50000.0
                val gy = mdcvBox.greenY / 50000.0
                val bx = mdcvBox.blueX / 50000.0
                val by = mdcvBox.blueY / 50000.0
                val rx = mdcvBox.redX / 50000.0
                val ry = mdcvBox.redY / 50000.0
                val wx = mdcvBox.whiteX / 50000.0
                val wy = mdcvBox.whiteY / 50000.0
                val maxLum = mdcvBox.maxMasteringLuminanceNits
                val minLum = mdcvBox.minMasteringLuminanceNits

                if (minLum > maxLum || minLum < 0.0 || maxLum < 0.0) {
                    hasMalformedOpticalMetadata = true
                    errors.add(
                        InspectionError(
                            code = "MALFORMED_MASTERING_LUMINANCE",
                            message = "Invalid mastering luminance in container 'mdcv' atom: min ($minLum nits) > max ($maxLum nits)",
                            isMalformed = true
                        )
                    )
                }

                MasteringDisplayReport(
                    greenX = gx,
                    greenY = gy,
                    blueX = bx,
                    blueY = by,
                    redX = rx,
                    redY = ry,
                    whitePointX = wx,
                    whitePointY = wy,
                    maxLuminanceNits = maxLum,
                    minLuminanceNits = minLum,
                    provenance = MetadataProvenance.SOURCE_DECLARED,
                    sourceLocation = "ISOBMFF 'mdcv' atom"
                )
            }
            bitstreamMdcv != null -> {
                val gx = bitstreamMdcv.displayPrimariesGx / 50000.0
                val gy = bitstreamMdcv.displayPrimariesGy / 50000.0
                val bx = bitstreamMdcv.displayPrimariesBx / 50000.0
                val by = bitstreamMdcv.displayPrimariesBy / 50000.0
                val rx = bitstreamMdcv.displayPrimariesRx / 50000.0
                val ry = bitstreamMdcv.displayPrimariesRy / 50000.0
                val wx = bitstreamMdcv.whitePointX / 50000.0
                val wy = bitstreamMdcv.whitePointY / 50000.0
                val maxLum = bitstreamMdcv.maxMasteringLuminanceNits
                val minLum = bitstreamMdcv.minMasteringLuminanceNits

                if (minLum > maxLum || minLum < 0.0 || maxLum < 0.0) {
                    hasMalformedOpticalMetadata = true
                    errors.add(
                        InspectionError(
                            code = "MALFORMED_MASTERING_LUMINANCE",
                            message = "Invalid mastering luminance in HEVC SEI 137: min ($minLum nits) > max ($maxLum nits)",
                            isMalformed = true
                        )
                    )
                }

                MasteringDisplayReport(
                    greenX = gx,
                    greenY = gy,
                    blueX = bx,
                    blueY = by,
                    redX = rx,
                    redY = ry,
                    whitePointX = wx,
                    whitePointY = wy,
                    maxLuminanceNits = maxLum,
                    minLuminanceNits = minLum,
                    provenance = MetadataProvenance.SOURCE_DECLARED,
                    sourceLocation = "HEVC SEI 137 (Mastering Display Colour Volume)"
                )
            }
            else -> null
        }

        // 5. Content light level evaluation
        val clliReport = when {
            clliBox != null -> ContentLightLevelReport(
                maxCllNits = clliBox.maxCll,
                maxFallNits = clliBox.maxFall,
                provenance = MetadataProvenance.SOURCE_DECLARED,
                sourceLocation = "ISOBMFF 'clli' atom"
            )
            bitstreamClli != null -> ContentLightLevelReport(
                maxCllNits = bitstreamClli.maxCll,
                maxFallNits = bitstreamClli.maxFall,
                provenance = MetadataProvenance.SOURCE_DECLARED,
                sourceLocation = "HEVC SEI 144 (Content Light Level)"
            )
            else -> null
        }

        if (clliReport == null && masteringDisplay != null) {
            warnings.add(
                InspectionWarning(
                    severity = WarningSeverity.INFO,
                    code = "OMITTED_CLLI_SIGNALS",
                    message = "Content Light Level Info (CTA-861.3 / CLLI) is omitted in both container and bitstream.",
                    technicalImpact = "Tone mapping targets must rely exclusively on ST 2086 mastering display luminance."
                )
            )
        }

        // 6. Classification Decision Logic
        val is8Bit = bitDepth == 8
        val hasHdrSignaling = transferIdc == 16 || transferIdc == 18 || primariesIdc == 9
        val isContradictoryBitDepth = mp4Report?.hvcCBox?.profileIdc == 2 && sps != null && sps.bitDepthLuma == 8

        val classification: HdrClassification
        when {
            hasMalformedOpticalMetadata -> {
                classification = HdrClassification.MALFORMED_OR_CONFLICTING
            }

            // Fake HDR Safeguard 1: Contradictory Main 10 container claim vs 8-bit bitstream SPS
            isContradictoryBitDepth -> {
                classification = HdrClassification.FAKE_HDR_REJECTED
                warnings.add(
                    InspectionWarning(
                        severity = WarningSeverity.CRITICAL,
                        code = "CONTRADICTORY_BIT_DEPTH_HDR",
                        message = "Fake HDR rejected: Container atom claims HEVC Main 10, but bitstream SPS confirms 8-bit Main profile.",
                        technicalImpact = "Stream cannot deliver true 10-bit color gradation."
                    )
                )
            }

            // Fake HDR Safeguard 2: 8-bit stream with HDR color signaling
            is8Bit && hasHdrSignaling -> {
                classification = HdrClassification.FAKE_HDR_REJECTED
                warnings.add(
                    InspectionWarning(
                        severity = WarningSeverity.CRITICAL,
                        code = "FAKE_HDR_DETECTED",
                        message = "Fake HDR rejected: 8-bit stream declares HDR signaling (${mapColorPrimaries(primariesIdc)}, ${mapColorTransfer(transferIdc)}).",
                        technicalImpact = "Genuine HDR requires minimum 10-bit precision. 8-bit precision causes severe color banding, posterization, and inaccurate tone mapping."
                    )
                )
            }

            // Genuine HDR10: 10-bit Main10 + BT.2020 + SMPTE ST 2084 (PQ) + valid mastering display
            bitDepth >= 10 && primariesIdc == 9 && transferIdc == 16 && masteringDisplay != null -> {
                classification = HdrClassification.GENUINE_HDR10
            }

            // Partial HDR: Main10 + BT.2020 + PQ without mastering display
            bitDepth >= 10 && primariesIdc == 9 && transferIdc == 16 && masteringDisplay == null -> {
                classification = HdrClassification.PARTIAL_HDR
                warnings.add(
                    InspectionWarning(
                        severity = WarningSeverity.WARNING,
                        code = "HDR10_METADATA_MISSING",
                        message = "Main10 BT.2020 PQ stream lacks SMPTE ST 2086 mastering display metadata (MDCV / SEI 137).",
                        technicalImpact = "Target displays cannot adapt peak brightness to original mastering monitor optics."
                    )
                )
            }

            // HLG (Hybrid Log-Gamma)
            transferIdc == 18 -> {
                classification = HdrClassification.HLG
            }

            // Standard Dynamic Range (SDR)
            primariesIdc in listOf(1, 5, 6) && (transferIdc in listOf(1, 6, 13) || transferIdc == null) -> {
                classification = HdrClassification.SDR_BT709
            }

            hasHdrSignaling -> {
                classification = HdrClassification.PARTIAL_HDR
            }

            else -> {
                classification = HdrClassification.UNKNOWN
            }
        }

        val hdrDetails = HdrColorDetails(
            classification = classification,
            colorPrimariesName = mapColorPrimaries(primariesIdc),
            colorTransferName = mapColorTransfer(transferIdc),
            matrixCoefficientsName = mapMatrixCoefficients(matrixIdc),
            colorRangeName = mapColorRange(fullRange),
            bitDepth = bitDepth,
            masteringDisplay = masteringDisplay,
            contentLightLevel = clliReport,
            dolbyVisionInfo = null,
            containerAtomVerified = colr != null,
            bitstreamSeiVerified = bitstreamMdcv != null,
            conflicts = conflicts
        )

        return HdrClassificationResult(
            classification = classification,
            hdrColorDetails = hdrDetails,
            warnings = warnings,
            errors = errors,
            conflicts = conflicts
        )
    }

    private fun detectMetadataConflicts(
        mp4Report: Mp4MetadataReport?,
        bitstreamReport: HevcBitstreamReport?
    ): List<MetadataConflictAlert> {
        val conflicts = mutableListOf<MetadataConflictAlert>()
        val colr = mp4Report?.colrBox ?: return conflicts
        val sps = bitstreamReport?.spsList?.firstOrNull() ?: return conflicts

        if (sps.hasVui) {
            if (sps.colorPrimaries != null && colr.colorPrimaries != sps.colorPrimaries) {
                conflicts.add(
                    MetadataConflictAlert(
                        fieldName = "color_primaries",
                        containerValue = "${mapColorPrimaries(colr.colorPrimaries)} (${colr.colorPrimaries})",
                        bitstreamValue = "${mapColorPrimaries(sps.colorPrimaries)} (${sps.colorPrimaries})",
                        resolutionDescription = "Bitstream VUI takes precedence during decoder rendering; container box governs demuxer routing."
                    )
                )
            }
            if (sps.transferCharacteristics != null && colr.transferCharacteristics != sps.transferCharacteristics) {
                conflicts.add(
                    MetadataConflictAlert(
                        fieldName = "transfer_characteristics",
                        containerValue = "${mapColorTransfer(colr.transferCharacteristics)} (${colr.transferCharacteristics})",
                        bitstreamValue = "${mapColorTransfer(sps.transferCharacteristics)} (${sps.transferCharacteristics})",
                        resolutionDescription = "Bitstream transfer curve takes precedence during decoder tone mapping."
                    )
                )
            }
            if (sps.matrixCoefficients != null && colr.matrixCoefficients != sps.matrixCoefficients) {
                conflicts.add(
                    MetadataConflictAlert(
                        fieldName = "matrix_coefficients",
                        containerValue = "${mapMatrixCoefficients(colr.matrixCoefficients)} (${colr.matrixCoefficients})",
                        bitstreamValue = "${mapMatrixCoefficients(sps.matrixCoefficients)} (${sps.matrixCoefficients})",
                        resolutionDescription = "Bitstream matrix coefficients govern YUV-to-RGB color transform."
                    )
                )
            }
            if (sps.fullRange != null && colr.fullRangeFlag != sps.fullRange) {
                conflicts.add(
                    MetadataConflictAlert(
                        fieldName = "full_range_flag",
                        containerValue = "${colr.fullRangeFlag}",
                        bitstreamValue = "${sps.fullRange}",
                        resolutionDescription = "Bitstream range flag governs quantization level scaling."
                    )
                )
            }
        }

        return conflicts
    }

    private fun mapColorPrimaries(idc: Int?): String? = when (idc) {
        1 -> "BT.709"
        4 -> "FCC 47 CFR 73.682"
        5 -> "BT.601 PAL"
        6 -> "BT.601 NTSC"
        7 -> "SMPTE 240M"
        9 -> "BT.2020"
        12 -> "DCI-P3 (SMPTE EG 432-1)"
        null -> null
        else -> "Primaries ($idc)"
    }

    private fun mapColorTransfer(idc: Int?): String? = when (idc) {
        1 -> "BT.709"
        4 -> "Gamma 2.2"
        5 -> "Gamma 2.8"
        6 -> "SMPTE 170M"
        8 -> "Linear"
        13 -> "sRGB"
        16 -> "SMPTE ST 2084 (PQ)"
        18 -> "ARIB STD-B67 (HLG)"
        null -> null
        else -> "Transfer ($idc)"
    }

    private fun mapMatrixCoefficients(idc: Int?): String? = when (idc) {
        0 -> "Identity (RGB)"
        1 -> "BT.709"
        6 -> "BT.601"
        9 -> "BT.2020 non-constant"
        10 -> "BT.2020 constant"
        null -> null
        else -> "Matrix ($idc)"
    }

    private fun mapColorRange(fullRange: Boolean?): String? = when (fullRange) {
        true -> "Full Range (0-255 / 0-1023)"
        false -> "Limited / Studio Range (16-235 / 64-940)"
        null -> null
    }
}
