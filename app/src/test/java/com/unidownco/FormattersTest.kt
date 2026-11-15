package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Formatters
import org.junit.Test

class FormattersTest {

    @Test
    fun testFormatBytes() {
        assertThat(Formatters.formatBytes(0L)).isEqualTo("0 B")
        assertThat(Formatters.formatBytes(1024L)).isEqualTo("1.0 KB")
        assertThat(Formatters.formatBytes(15 * 1024 * 1024L)).isEqualTo("15.0 MB")
        assertThat(Formatters.formatBytes(2L * 1024 * 1024 * 1024)).isEqualTo("2.0 GB")
    }

    @Test
    fun testFormatDuration() {
        assertThat(Formatters.formatDuration(0L)).isEqualTo("00:00")
        assertThat(Formatters.formatDuration(45L)).isEqualTo("00:45")
        assertThat(Formatters.formatDuration(125L)).isEqualTo("02:05")
        assertThat(Formatters.formatDuration(3665L)).isEqualTo("01:01:05")
    }

    @Test
    fun testFormatEta() {
        assertThat(Formatters.formatEta(0L)).isEqualTo("Done")
        assertThat(Formatters.formatEta(30L)).isEqualTo("30s left")
        assertThat(Formatters.formatEta(90L)).isEqualTo("1m 30s left")
    }

    @Test
    fun testFormatSpeed() {
        assertThat(Formatters.formatSpeed(1024 * 1024 * 3L)).isEqualTo("3.0 MB/s")
    }
}
