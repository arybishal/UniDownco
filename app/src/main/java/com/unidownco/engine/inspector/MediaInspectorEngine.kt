package com.unidownco.engine.inspector

import android.content.Context
import android.net.Uri
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.source.MediaSource
import java.io.File

/**
 * High-performance, memory-bounded media inspection engine.
 *
 * Inspects local files, SAF content URIs, and arbitrary media sources across all streams,
 * container atoms, elementary bitstream headers, and optical metadata.
 */
interface MediaInspectorEngine {

    /**
     * Inspects a resolved [MediaSource].
     */
    suspend fun inspect(source: MediaSource): Result<MediaInspectionReport>

    /**
     * Resolves and inspects a local or downloaded [File].
     */
    suspend fun inspect(file: File): Result<MediaInspectionReport>

    /**
     * Resolves and inspects an Android content or file [Uri].
     */
    suspend fun inspect(uri: Uri, context: Context): Result<MediaInspectionReport>
}
