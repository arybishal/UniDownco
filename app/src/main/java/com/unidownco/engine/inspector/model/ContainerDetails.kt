package com.unidownco.engine.inspector.model

/**
 * Represents a single node in the hierarchical box / atom tree of an ISOBMFF / MP4 container.
 *
 * @property type 4-character ASCII box type (e.g. 'ftyp', 'moov', 'trak', 'stsd', 'colr')
 * @property offset Absolute byte offset from the start of the container
 * @property size Total box size in bytes including header
 * @property description Human-readable technical description of the box's function
 * @property children Nested sub-boxes for container atoms
 */
data class BoxNode(
    val type: String,
    val offset: Long,
    val size: Long,
    val description: String,
    val children: List<BoxNode> = emptyList()
) {
    val isContainer: Boolean
        get() = children.isNotEmpty()

    val totalDescendants: Int
        get() = children.size + children.sumOf { it.totalDescendants }
}

/**
 * Encapsulates container-level formatting, track allocation, and structural atom hierarchy.
 */
data class ContainerDetails(
    val containerFormat: String, // e.g. "MP4", "MOV", "MKV", "WebM", "AAC"
    val majorBrand: String? = null,
    val minorVersion: Long? = null,
    val compatibleBrands: List<String> = emptyList(),
    val durationMs: Long? = null,
    val totalTracks: Int = 0,
    val videoTrackCount: Int = 0,
    val audioTrackCount: Int = 0,
    val subtitleTrackCount: Int = 0,
    val boxHierarchy: List<BoxNode> = emptyList(),
    val rawMimeType: String? = null
)
