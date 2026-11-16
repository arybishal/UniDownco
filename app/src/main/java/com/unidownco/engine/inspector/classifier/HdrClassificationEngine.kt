package com.unidownco.engine.inspector.classifier

import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.InspectionError
import com.unidownco.engine.inspector.model.InspectionWarning
import com.unidownco.engine.inspector.model.MetadataConflictAlert
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.parser.HevcBitstreamReport
import com.unidownco.engine.parser.Mp4MetadataReport

/**
 * Result of an authoritative HDR and colorimetric analysis.
 */
data class HdrClassificationResult(
    val classification: HdrClassification,
    val hdrColorDetails: HdrColorDetails,
    val warnings: List<InspectionWarning> = emptyList(),
    val errors: List<InspectionError> = emptyList(),
    val conflicts: List<MetadataConflictAlert> = emptyList()
)

/**
 * Dedicated engine responsible for analyzing optical, colorimetric, and bitstream evidence
 * and assigning an authoritative [HdrClassification].
 */
interface HdrClassificationEngine {

    /**
     * Evaluates container metadata, elementary bitstream headers, and video stream properties.
     */
    fun evaluate(
        mp4Report: Mp4MetadataReport?,
        bitstreamReport: HevcBitstreamReport?,
        primaryVideo: VideoStreamDetails?
    ): HdrClassificationResult
}
