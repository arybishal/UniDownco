package com.unidownco.engine.inspector.source

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Common seekable random-access input abstraction for media containers.
 * Allows inspecting files, content URIs, and in-memory byte arrays with identical traversal logic.
 */
interface SeekableDataSource : AutoCloseable {
    val length: Long
    fun seek(position: Long)
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int
}

/**
 * Seekable input wrapping a standard [RandomAccessFile].
 */
class RandomAccessFileDataSource(private val raf: RandomAccessFile) : SeekableDataSource {
    override val length: Long get() = raf.length()

    override fun seek(position: Long) {
        raf.seek(position)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return raf.read(buffer, offset, length)
    }

    override fun close() {
        raf.close()
    }
}

/**
 * Seekable input wrapping an Android [FileChannel] (e.g. from ParcelFileDescriptor).
 */
class FileChannelDataSource(private val channel: FileChannel) : SeekableDataSource {
    override val length: Long get() = channel.size()

    override fun seek(position: Long) {
        channel.position(position)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val byteBuffer = ByteBuffer.wrap(buffer, offset, length)
        return channel.read(byteBuffer)
    }

    override fun close() {
        channel.close()
    }
}

/**
 * Seekable input backed by an in-memory byte array for unit tests and micro-benchmarks.
 */
class ByteArrayDataSource(private val data: ByteArray) : SeekableDataSource {
    private var position: Long = 0L

    override val length: Long get() = data.size.toLong()

    override fun seek(position: Long) {
        this.position = position.coerceIn(0L, length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= data.size) return -1
        val available = (data.size - position).toInt()
        val toRead = Math.min(length, available)
        if (toRead <= 0) return -1
        System.arraycopy(data, position.toInt(), buffer, offset, toRead)
        position += toRead
        return toRead
    }

    override fun close() {
        // No-op for in-memory buffer
    }
}
