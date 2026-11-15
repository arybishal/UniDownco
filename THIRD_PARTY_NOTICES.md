# Third-Party Software Notices and Licenses

UniDownco incorporates and links against the following third-party open-source components.
This document provides complete attribution, exact versions, licenses, and notices in compliance with respective distribution obligations.

---

## 1. youtubedl-android
- **Version:** `0.18.1` (`io.github.junkfood02.youtubedl-android:library:0.18.1`)
- **Maintainer / Contributors:** JunkFood02, yausername
- **Repository:** https://github.com/JunkFood02/youtubedl-android
- **License:** GNU General Public License v3.0 (GPL-3.0)
- **Notice & Obligations:** This library provides an Android JNI runtime wrapper for yt-dlp, packaging CPython and FFmpeg binaries for Android. Under GPL-3.0, any derivative work or application distributing this component must provide access to the source code under GPL-3.0.

## 2. youtubedl-android FFmpeg Binary Package
- **Version:** `0.18.1` (`io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`)
- **Maintainer / Contributors:** JunkFood02, yausername
- **Repository:** https://github.com/JunkFood02/youtubedl-android
- **License:** GNU General Public License v3.0 (GPL-3.0)
- **Notice & Obligations:** Contains compiled FFmpeg and FFprobe binaries for Android architectures (`arm64-v8a`, `armeabi-v7a`, `x86_64`).

## 3. yt-dlp
- **Version:** Bundled release executable managed via youtubedl-android (supports dynamic updates)
- **Maintainers:** yt-dlp contributors
- **Repository:** https://github.com/yt-dlp/yt-dlp
- **License:** The Unlicense / Creative Commons Zero v1.0 Universal (Public Domain)
- **Notice:** yt-dlp is dedicated to the public domain. Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial.

## 4. CPython
- **Version:** `3.8.x` (embedded runtime in youtubedl-android)
- **Organization:** Python Software Foundation (PSF)
- **Website:** https://www.python.org/
- **License:** Python Software Foundation License Version 2
- **Notice:** Copyright © 2001-2026 Python Software Foundation. All Rights Reserved.

## 5. FFmpeg Multimedia Framework
- **Version:** Compiled binary embedded via youtubedl-android-ffmpeg
- **Originator / Maintainers:** Fabrice Bellard, FFmpeg project developers
- **Website:** https://ffmpeg.org/
- **License:** GNU Lesser General Public License v2.1+ (LGPL-2.1+) / GNU General Public License v3.0 (GPL-3.0) depending on build flags
- **Notice:** FFmpeg is a trademark of Fabrice Bellard.

## 6. QuickJS Javascript Engine
- **Version:** Embedded native shared library (`libqjs.so`)
- **Authors:** Fabrice Bellard and Charlie Gordon
- **Website:** https://bellard.org/quickjs/
- **License:** MIT License
- **Notice:** Copyright © 2019-2021 Fabrice Bellard and Charlie Gordon. Permission is hereby granted, free of charge, to any person obtaining a copy of this software.

## 7. Jackson JSON Processor
- **Version:** `2.11.1` (`com.fasterxml.jackson.core:jackson-databind:2.11.1`, `jackson-core`, `jackson-annotations`)
- **Developer:** FasterXML, LLC
- **Website:** https://github.com/FasterXML/jackson
- **License:** Apache License, Version 2.0

## 8. Apache Commons
- **Versions:** `commons-compress:1.12`, `commons-io:2.5`
- **Developer:** Apache Software Foundation
- **Website:** https://commons.apache.org/
- **License:** Apache License, Version 2.0

## 9. Square OkHttp
- **Version:** `4.12.0` (`com.squareup.okhttp3:okhttp:4.12.0`, `logging-interceptor:4.12.0`)
- **Developer:** Square, Inc.
- **Website:** https://square.github.io/okhttp/
- **License:** Apache License, Version 2.0

## 10. AndroidX & Jetpack Libraries
- **Versions:**
  - Compose BOM: `2024.10.01` (Compose UI `1.7.x`, Material 3 `1.3.x`)
  - Navigation Compose: `2.8.3`
  - Core KTX: `1.15.0`
  - Lifecycle: `2.8.7`
  - Room Database: `2.6.1`
  - DataStore Preferences: `1.1.1`
  - WorkManager: `2.10.0`
- **Developer:** Google LLC / Android Open Source Project
- **License:** Apache License, Version 2.0

## 11. Kotlin & Coroutines
- **Versions:** Kotlin `2.0.21`, Kotlinx Coroutines `1.9.0`
- **Developer:** JetBrains s.r.o. and Google LLC
- **Website:** https://kotlinlang.org/
- **License:** Apache License, Version 2.0

## 12. Testing Libraries
- **Versions:** JUnit `4.13.2` (EPL-2.0 / Apache 2.0), Google Truth `1.4.4` (Apache 2.0), AndroidX Test `1.2.1` / Espresso `3.6.1` (Apache 2.0), `org.json:json:20240303` (JSON License)

---

### GPL-3.0 Source Distribution Obligations
Because UniDownco links with `youtubedl-android` (GPL-3.0) and FFmpeg binaries built with GPL enablement, the complete application source code is provided under GPL-compatible terms. Any distributor of compiled binaries (APKs/AABs) must make the complete corresponding source code readily available under the GPL-3.0 license.
