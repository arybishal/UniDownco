package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.inspector.source.ByteArrayDataSource
import com.unidownco.engine.inspector.source.LocalFileMediaSource
import com.unidownco.engine.inspector.source.MediaSourceResolver
import com.unidownco.engine.inspector.source.RandomAccessFileDataSource
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile

class MediaSourceResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testResolveLocalFile_Success() {
        val testFile = tempFolder.newFile("test_video.mp4")
        testFile.writeBytes(ByteArray(100) { it.toByte() })

        val result = MediaSourceResolver.resolve(testFile)
        assertThat(result.isSuccess).isTrue()

        val source = result.getOrThrow()
        assertThat(source).isInstanceOf(LocalFileMediaSource::class.java)
        assertThat(source.displayName).isEqualTo("test_video.mp4")
        assertThat(source.lengthBytes).isEqualTo(100L)
        assertThat(source.isSeekable).isTrue()
        assertThat(source.mimeType).isEqualTo("video/mp4")

        source.close()
    }

    @Test
    fun testResolveLocalFile_NonExistentFile() {
        val nonExistent = File(tempFolder.root, "does_not_exist.mp4")
        val result = MediaSourceResolver.resolve(nonExistent)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun testResolveLocalFile_DirectoryRejection() {
        val subDir = tempFolder.newFolder("videos")
        val result = MediaSourceResolver.resolve(subDir)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("Target is a directory")
    }

    @Test
    fun testByteArrayDataSource_SeekingAndReading() {
        val data = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val source = ByteArrayDataSource(data)

        assertThat(source.length).isEqualTo(10L)

        // Read first 4 bytes
        val buf1 = ByteArray(4)
        val r1 = source.read(buf1)
        assertThat(r1).isEqualTo(4)
        assertThat(buf1).isEqualTo(byteArrayOf(10, 20, 30, 40))

        // Seek to index 7
        source.seek(7L)
        val buf2 = ByteArray(3)
        val r2 = source.read(buf2)
        assertThat(r2).isEqualTo(3)
        assertThat(buf2).isEqualTo(byteArrayOf(80, 90, 100))

        // Read at EOF returns -1
        val bufEof = ByteArray(4)
        val rEof = source.read(bufEof)
        assertThat(rEof).isEqualTo(-1)

        source.close()
    }

    @Test
    fun testRandomAccessFileDataSource_SeekingAndReading() {
        val testFile = tempFolder.newFile("raf_test.dat")
        testFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        RandomAccessFile(testFile, "r").use { raf ->
            val source = RandomAccessFileDataSource(raf)
            assertThat(source.length).isEqualTo(8L)

            source.seek(2L)
            val buf = ByteArray(3)
            val read = source.read(buf)
            assertThat(read).isEqualTo(3)
            assertThat(buf).isEqualTo(byteArrayOf(3, 4, 5))
        }
    }

    @Test
    fun testResolveAny_PolymorphicDispatch() {
        val testFile = tempFolder.newFile("sample.mkv")
        testFile.writeBytes(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))

        // File instance
        val rFile = MediaSourceResolver.resolveAny(testFile, null)
        assertThat(rFile.isSuccess).isTrue()
        assertThat(rFile.getOrThrow().displayName).isEqualTo("sample.mkv")
        rFile.getOrThrow().close()

        // String path
        val rString = MediaSourceResolver.resolveAny(testFile.absolutePath, null)
        assertThat(rString.isSuccess).isTrue()
        assertThat(rString.getOrThrow().displayName).isEqualTo("sample.mkv")
        rString.getOrThrow().close()

        // Content URI without context
        val rUriNoCtx = MediaSourceResolver.resolveAny("content://media/external/video/media/1", null)
        assertThat(rUriNoCtx.isFailure).isTrue()
        assertThat(rUriNoCtx.exceptionOrNull()?.message).contains("Context required")

        // Unsupported object
        val rInvalid = MediaSourceResolver.resolveAny(12345, null)
        assertThat(rInvalid.isFailure).isTrue()
        assertThat(rInvalid.exceptionOrNull()?.message).contains("Unsupported media source type")
    }
}
