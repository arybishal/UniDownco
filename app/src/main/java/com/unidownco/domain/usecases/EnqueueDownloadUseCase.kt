package com.unidownco.domain.usecases

import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo

class EnqueueDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(info: VideoExtractionInfo, selectedFormat: FormatOption): Long {
        return repository.enqueueDownload(info, selectedFormat)
    }
}
