package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.core.database.DownloadEntity
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.DownloadStatus
import com.unidownco.domain.models.EncodingStatus
import org.junit.Test

class DownloadItemStateTest {

    @Test
    fun testStatusFlags() {
        assertThat(DownloadStatus.PENDING.isActive).isTrue()
        assertThat(DownloadStatus.DOWNLOADING.isActive).isTrue()
        assertThat(DownloadStatus.FETCHING_METADATA.isActive).isTrue()

        assertThat(DownloadStatus.PAUSED.isActive).isFalse()
        assertThat(DownloadStatus.COMPLETED.isFinished).isTrue()
        assertThat(DownloadStatus.FAILED.isFinished).isTrue()
        assertThat(DownloadStatus.CANCELLED.isFinished).isTrue()
    }

    @Test
    fun testEntityDomainMappingRoundTrip() {
        val original = DownloadItem(
            id = 42L,
            url = "https://www.youtube.com/watch?v=test12345",
            platform = Platform.YOUTUBE,
            title = "Sample Tech Video",
            author = "Tech Creator",
            thumbnailUrl = "https://example.com/thumb.jpg",
            originalFilename = "Sample_Tech_Video.mp4",
            downloadPath = "/data/user/0/com.unidownco/movies/test.mp4",
            outputPath = null,
            resolution = "1080p",
            fps = 60,
            codec = "h264",
            durationSeconds = 180L,
            fileSizeBytes = 50_000_000L,
            downloadedBytes = 25_000_000L,
            progressPercent = 50,
            downloadSpeedBytesPerSec = 2_500_000L,
            etaSeconds = 10L,
            downloadStatus = DownloadStatus.DOWNLOADING,
            encodingStatus = EncodingStatus.NOT_STARTED,
            errorMessage = null,
            createdAt = 1700000000000L,
            completedAt = null
        )

        val entity = DownloadEntity.fromDomain(original)
        val mappedBack = entity.toDomain()

        assertThat(mappedBack.id).isEqualTo(original.id)
        assertThat(mappedBack.url).isEqualTo(original.url)
        assertThat(mappedBack.platform).isEqualTo(original.platform)
        assertThat(mappedBack.title).isEqualTo(original.title)
        assertThat(mappedBack.author).isEqualTo(original.author)
        assertThat(mappedBack.resolution).isEqualTo(original.resolution)
        assertThat(mappedBack.fps).isEqualTo(original.fps)
        assertThat(mappedBack.downloadStatus).isEqualTo(original.downloadStatus)
        assertThat(mappedBack.progressPercent).isEqualTo(original.progressPercent)
    }
}
