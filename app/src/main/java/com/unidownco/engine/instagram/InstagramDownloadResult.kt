package com.unidownco.engine.instagram

import com.unidownco.domain.models.FormatOption
import java.io.File

enum class InstagramErrorType {
    AUTHENTICATION_REQUIRED,
    PRIVATE_CONTENT,
    RATE_LIMITED,
    UNSUPPORTED_URL,
    UNAVAILABLE_OR_DELETED,
    NETWORK_ERROR,
    FORMAT_UNAVAILABLE,
    EXTRACTION_FAILED
}

sealed class InstagramDownloadResult {
    data class Success(
        val file: File,
        val format: FormatOption
    ) : InstagramDownloadResult()

    data class Failure(
        val errorType: InstagramErrorType,
        val userMessage: String,
        val rawMessage: String? = null,
        val cause: Throwable? = null
    ) : InstagramDownloadResult()
}
