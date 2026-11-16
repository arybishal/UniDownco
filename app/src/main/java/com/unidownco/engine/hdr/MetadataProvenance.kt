package com.unidownco.engine.hdr

/**
 * Explicit origin and authority classification for video metadata values.
 * Strictly prevents fabricated or unjustified metadata from being emitted in output streams.
 */
enum class MetadataProvenance {
    /**
     * Calculated directly from decoded frame pixel luminance values across all frames.
     */
    MEASURED,

    /**
     * Extracted directly from valid, authenticated source stream metadata (SEI or container atom)
     * and preserved without modification.
     */
    SOURCE_DECLARED,

    /**
     * Derived from analytical mathematical boundaries of the applied color transfer or inverse tone
     * mapping curve (e.g. ITU-R BT.2408 expansion ceiling), with documented methodology and limitations.
     */
    MATHEMATICALLY_ESTIMATED,

    /**
     * Metadata is not available and cannot be reliably measured or mathematically bounded.
     * Standards-compliant representation requires omission or signaling as unknown (0).
     */
    UNAVAILABLE
}

/**
 * Container for a metadata value paired with its explicit provenance and emission safety rule.
 */
data class MetadataField<T>(
    val value: T?,
    val provenance: MetadataProvenance,
    val description: String,
    val measurementMethod: String? = null
) {
    /**
     * True if this field contains a non-null value that is legally and technically safe to emit.
     */
    val isEmittable: Boolean
        get() = value != null && provenance != MetadataProvenance.UNAVAILABLE
}
