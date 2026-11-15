package com.unidownco.core.common

enum class Platform(val displayName: String, val identifier: String) {
    YOUTUBE("YouTube", "youtube"),
    TIKTOK("TikTok", "tiktok"),
    INSTAGRAM("Instagram", "instagram"),
    FACEBOOK("Facebook", "facebook"),
    REDDIT("Reddit", "reddit"),
    X_TWITTER("X / Twitter", "twitter"),
    GENERIC("Direct / Web Video", "generic"),
    UNSUPPORTED("Unsupported", "unsupported");

    companion object {
        fun fromIdentifier(id: String): Platform {
            return values().firstOrNull { it.identifier.equals(id, ignoreCase = true) } ?: GENERIC
        }
    }
}
