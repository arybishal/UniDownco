package com.unidownco.domain.usecases

import com.unidownco.core.common.UrlValidator
import com.unidownco.core.common.ValidationResult

class ValidateUrlUseCase {
    operator fun invoke(url: String?): ValidationResult {
        return UrlValidator.validateAndExtract(url)
    }
}
