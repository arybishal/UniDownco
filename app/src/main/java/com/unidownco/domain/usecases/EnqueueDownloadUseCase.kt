package com.unidownco.domain.usecases

import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.domain.models.VideoExtractionInfo

class EnqueueDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(
        info: VideoExtractionInfo,
        selectedFormat: FormatOption,
        preset: ProcessingPreset = ProcessingPreset.HDR10_ENHANCED
    ): Long {
        return repository.enqueueDownload(info, selectedFormat, preset)
    }
}
