package com.unidownco.domain.usecases

import com.unidownco.core.common.Resource
import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.VideoExtractionInfo

class ExtractVideoInfoUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(url: String): Resource<VideoExtractionInfo> {
        return repository.extractMetadata(url)
    }
}
