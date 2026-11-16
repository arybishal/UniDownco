package com.unidownco.engine.inspector.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Common abstraction for media assets readable by the inspector engine.
 * Supports app-owned downloaded files, local storage files, MediaStore URIs,
 * and Storage Access Framework (SAF) content URIs.
 */
sealed interface MediaSource : AutoCloseable {
    val displayName: String
    val lengthBytes: Long // -1L if unknown
    val mimeType: String?
    val isSeekable: Boolean

    /**
     * Opens an input stream for sequential reads.
     */
    fun openInputStream(): InputStream

    /**
     * Opens a seekable input stream for random-access box and atom parsing.
     */
    fun openSeekable(): SeekableDataSource

    /**
     * Returns a valid FileDescriptor for MediaExtractor and MediaMetadataRetriever.
     */
    fun getFileDescriptor(): FileDescriptor

    val fileDescriptorOffset: Long get() = 0L

    val fileDescriptorLength: Long get() = if (lengthBytes > 0) lengthBytes else -1L
}

/**
 * Direct file-backed media source for local and downloaded files.
 */
class LocalFileMediaSource(
    val file: File,
    override val mimeType: String? = null
) : MediaSource {
    private var openedRaf: RandomAccessFile? = null

    override val displayName: String get() = file.name
    override val lengthBytes: Long get() = if (file.exists()) file.length() else -1L
    override val isSeekable: Boolean get() = true

    override fun openInputStream(): InputStream = FileInputStream(file)

    override fun openSeekable(): SeekableDataSource {
        val raf = RandomAccessFile(file, "r")
        return RandomAccessFileDataSource(raf)
    }

    override fun getFileDescriptor(): FileDescriptor {
        val raf = openedRaf ?: RandomAccessFile(file, "r").also { openedRaf = it }
        return raf.fd
    }

    override fun close() {
        try { openedRaf?.close() } catch (_: Exception) {}
        openedRaf = null
    }
}

/**
 * Content URI backed media source for Android Storage Access Framework (SAF)
 * and MediaStore queries.
 */
class ContentUriMediaSource(
    val uri: Uri,
    val context: Context,
    override val displayName: String,
    override val lengthBytes: Long,
    override val mimeType: String?,
    private val pfd: ParcelFileDescriptor
) : MediaSource {

    override val isSeekable: Boolean get() = true

    override fun openInputStream(): InputStream {
        return ParcelFileDescriptor.AutoCloseInputStream(pfd.dup())
    }

    override fun openSeekable(): SeekableDataSource {
        val dupPfd = pfd.dup()
        val channel = FileInputStream(dupPfd.fileDescriptor).channel
        return object : SeekableDataSource {
            private val wrapped = FileChannelDataSource(channel)

            override val length: Long get() = if (lengthBytes > 0) lengthBytes else wrapped.length
            override fun seek(position: Long) = wrapped.seek(position)
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = wrapped.read(buffer, offset, length)

            override fun close() {
                try { wrapped.close() } catch (_: Exception) {}
                try { dupPfd.close() } catch (_: Exception) {}
            }
        }
    }

    override fun getFileDescriptor(): FileDescriptor = pfd.fileDescriptor

    override val fileDescriptorLength: Long
        get() = if (lengthBytes > 0) lengthBytes else pfd.statSize

    override fun close() {
        try { pfd.close() } catch (_: Exception) {}
    }
}
