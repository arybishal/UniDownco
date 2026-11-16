package com.unidownco.engine.inspector.evaluator

import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.inspector.model.DeviceCompatibilityDetails
import com.unidownco.engine.inspector.model.MediaInspectionReport

/**
 * Cross-references inspected media properties against the host hardware capabilities.
 */
interface DeviceCompatibilityEvaluator {

    /**
     * Evaluates whether the device can decode the source media and whether it can
     * hardware-encode HEVC Main or Main10 streams.
     */
    fun evaluate(
        report: MediaInspectionReport,
        capabilities: HardwareCapabilities
    ): DeviceCompatibilityDetails
}
