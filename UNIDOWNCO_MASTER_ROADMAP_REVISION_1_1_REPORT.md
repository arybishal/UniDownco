# UNIDOWNCO MASTER ROADMAP REVISION 1.1 REPORT
## Technical Audit, Four Mandatory Corrections & 12-Phase Architecture Governance

**Project:** UniDownco  
**Workspace:** `D:\Webapp\UniDownco`  
**GitHub Repository:** `https://github.com/arybishal/UniDownco`  
**Base Branch:** `master`  
**Document Status:** REVISION REPORT (DOCUMENTATION-ONLY ASSIGNMENT)  
**Target Environments:**
- **Baseline Connected Physical Device:** Xiaomi Redmi Note 25062RN2DA (Qualcomm SM6225 / bengal / Adreno 610, Android 16 / API 36)
- **Flagship Target Platform:** OnePlus Ace 5 / OnePlus 13R class (Qualcomm Snapdragon 8 Gen 3 SM8650-AB / Adreno 750, Android 15 / API 35+)

---

## 1. PREFLIGHT GIT STATUS & HEAD

Prior to editing documentation, a preflight git check was performed:
- **Branch:** `master`
- **Initial HEAD Commit:** [`21844c845ad43202eb86c9db02e3fe2d3c92b23a`](https://github.com/arybishal/UniDownco/commit/21844c845ad43202eb86c9db02e3fe2d3c92b23a)
- **Working Tree:** Completely clean (`nothing to commit, working tree clean`).
- **No Unrelated Modifications:** No uncommitted changes or conflicts were present.

---

## 2. FILES INSPECTED

The following architectural and project documentation files were comprehensively inspected:
1. `UNIDOWNCO_MASTER_PRODUCT_AND_TECHNICAL_ROADMAP.md` (v1.0 baseline)
2. `DESIGN.md` (Design system tokens, typography scale, component standards)
3. `ARCHITECTURE.md` (Clean Architecture + MVVM + provider strategy)
4. `STEP_2_3_HDR_CINEMATIC_ENHANCEMENT_ARCHITECTURE_PLAN.md` (HDR10 and enhancement specifications)
5. `STEP_2_3_1_HDR10_IMPLEMENTATION_REPORT.md` (Stage 2.3.1 implementation and test results)
6. `STEP_2_2_1_FINAL_DEVICE_VERIFICATION_REPORT.md` (Physical device baseline and test suite results)
7. `app/build.gradle.kts` and `gradle/libs.versions.toml` (Application IDs, SDK versions, dependencies)
8. Room database entities (`DownloadEntity.kt`, `ProcessingJobEntity.kt`, `VideoMetadataEntity.kt`, `UniDownDatabase.kt`)
9. Downloader and video processing components (`VideoProcessingEngine.kt`, `HdrRenderCore.kt`, `SdrToHdr10Shader.kt`, `MediaCodecVideoEncoder.kt`, `HdrStaticMetadata.kt`)

---

## 3. ORIGINAL ROADMAP DEFICIENCIES (v1.0 AUDIT)

The audit of Roadmap v1.0 identified several structural and technical deficiencies:
1. **Missing Phase 11:** The roadmap stopped at Phase 10 without articulating a dedicated phase for **Premium UX & Background Experience** (background service lifecycle, queue prioritization/reordering, Picture-in-Picture, accessibility, responsive layouts).
2. **Missing Phase 12:** The roadmap lacked a final **Quality, Security, Compatibility & Release Readiness** phase to govern full-suite regression, storage/database audits, Keystore security, and immutable release packaging.
3. **Ambiguous HDR10 Metadata Representations:** Metadata was treated somewhat interchangeably across Android APIs, elementary bitstreams, and MP4 container atoms, and default static values ($1000\text{ nits MaxCLL} / 400\text{ nits MaxFALL}$) were not formally isolated from measured values.
4. **General Hardware Compatibility Labels:** The hardware matrix used broad categories without separating theoretical silicon capabilities from physically verified capabilities, and lacked a structured 20-dimension evaluation.
5. **Lack of an Explicit Regression Baseline:** While test suites were mentioned, an explicit, mandatory regression baseline protecting previously verified features across all four architectural domains (downloader, processing, storage/database, and UI) was absent.
6. **Release Tag Mutability:** The policy regarding GitHub release tags required strict hardening against force-updating published releases.

---

## 4. EXACT CHANGES MADE IN REVISION 1.1

In [UNIDOWNCO_MASTER_PRODUCT_AND_TECHNICAL_ROADMAP.md](file:///D:/Webapp/UniDownco/UNIDOWNCO_MASTER_PRODUCT_AND_TECHNICAL_ROADMAP.md):
1. **Updated Title & Version Header:** Replaced v1.0 with Revision 1.1 and added a formal **Revision History** table.
2. **Added Phase 11 (Premium UX & Background Experience):** Fully detailed background execution, queue reordering/grouping, PiP lifecycle, accessibility ($48\times 48\text{dp}$ touch targets, TalkBack), and responsive layouts.
3. **Added Phase 12 (Final Quality, Security, Compatibility & Release Readiness):** Detailed comprehensive audit domains, immutable GitHub release tag policy, SHA-256 release checksum governance, and final signoff gates.
4. **Completely Overhauled Section 4 (HDR10 Metadata & Bitstream Governance):**
   - Distinctly separated 5 metadata representations: Mastering Display Colour Volume, Content Light Level, HEVC SEI NALs, MP4 container atoms, and Android `KEY_HDR_STATIC_INFO`.
   - Formally eliminated fabricated metadata defaults; categorized metadata into Measured, Source-Declared, Mathematically Estimated, and Unavailable.
   - Documented exact byte layouts, units, scaling factors, and endianness for all representations (notably highlighting the $10,000\times$ scaling difference between CTA-861.3 and SEI/MP4 luminance units).
   - Specified exact HEVC SPS, VUI, and SEI NAL verification rules with independent parser tool requirements.
   - Defined numerical tolerances for color science validation.
   - Formalized physical device verification status: `MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED`.
5. **Replaced Section 5 with an Expanded 20-Dimension Hardware Capability Matrix:** Evaluated across 5 discovery levels using 10 standardized status classifications.
6. **Added Section 6 (Mandatory Regression Baseline):** Documented explicit regression suites for Downloader, Media Processing, Storage/Database, and UI domains.
7. **Updated Development Sequence to a 12-Phase Model:** Integrated Phases 11 and 12 into the dependency graph and critical path.
8. **Hardened GitHub Release Policy:** Prohibited force-updating or rewriting published tags; mandated new version codes, names, and immutable tags for every release.

---

## 5. CONFIRMATION OF THE FOUR MANDATORY CORRECTIONS

- [x] **Correction A (Phase 11):** Fully documented in Section 7 (Phase 11: Premium UX & Background Experience), covering background service lifecycle, queue priority/reordering, PiP lifecycle, UX states, system integration, tests, and rollback strategy.
- [x] **Correction B (Phase 12):** Fully documented in Section 7 (Phase 12: Final Quality, Security, Compatibility & Release Readiness), establishing full-system audits, immutable release tag policy, APK signing, checksum verification, and release checklists.
- [x] **Correction C (HDR10 Governance):** Fully documented in Section 4, detailing the 5 distinct metadata domains, elimination of fabricated values, independent serialization tables, HEVC SEI NAL verification, color science test vectors, and explicit physical device gating.
- [x] **Correction D (Expanded Hardware Matrix):** Fully documented in Section 5, providing a 20-dimension capability matrix across 5 discovery levels and 10 standardized classifications for both Xiaomi SM6225 and OnePlus SM8650.

---

## 6. CONFIRMATION OF ALL 12 PHASES

The revised roadmap provides comprehensive documentation for all 12 phases:
- **Phase 1:** Stage 2.3.1.1 HDR10 Metadata Correction & End-to-End Bitstream Verification
- **Phase 2:** Built-in Media Inspector & Deep Analysis Dialog (Phase B6)
- **Phase 3:** Built-in Video Trimmer & Fast Stream-Copy Engine (Phase B1)
- **Phase 4:** Audio Extractor & Media Format Converter (Phases B3 & B5)
- **Phase 5:** Advanced Video Compressor with CRF & Target Size Controls (Phase B2)
- **Phase 6:** Advanced Downloader: Batch, Playlists & Format Picker (Phases A1, A2, A5)
- **Phase 7:** Downloader Reliability: Scheduling, Error Classifier, Subtitles & Secure Sessions (Phases A3, A4, A6, A7)
- **Phase 8:** Smart Library: Categories, Filtering, Storage Dashboard, Duplicate Detection & Backup (Phases C1 - C6)
- **Phase 9:** Video Enhancement: Cinematic Color Grading & 3D LUT Integration (Phases D1, D2, D3)
- **Phase 10:** Video Enhancement: Film Grain, Watermarks & Unified Processing Pipeline (Phases D4, D5, D9)
- **Phase 11:** Premium UX & Background Experience
- **Phase 12:** Final Quality, Security, Compatibility & Release Readiness

Each phase explicitly defines: Purpose, Scope, Explicit Exclusions, Prerequisites, Architecture, Implementation Boundaries, Dependencies, Acceptance Criteria, Unit Tests, Integration Tests, Instrumentation Tests, Physical-Device Tests, Performance/Thermal Tests, Regression Requirements, Security Considerations, Documentation Deliverables, Rollback Strategy, and Explicit Approval Gate.

---

## 7. HDR10 METADATA POLICY CHANGES

1. **Elimination of Fabricated Metadata:**
   - Universal hardcoded values ($1000\text{ nits MaxCLL} / 400\text{ nits MaxFALL}$) are strictly prohibited.
   - Values must be Measured (pixel-calculated), Source-Declared (passthrough), Mathematically Estimated (bounded by ITM curve), or marked Unavailable.
   - Standards-compliant omission policy: When content light level is unavailable, SEI payload 144 and MP4 `clli` atoms are omitted or signaled as $0$.
2. **Representation Separation:**
   - Android `KEY_HDR_STATIC_INFO`: 25 bytes, Little-Endian, Max Lum in $1\text{ cd/m}^2$.
   - HEVC SEI Payload 137: Elementary NAL, Big-Endian, Max Lum in $0.0001\text{ cd/m}^2$.
   - HEVC SEI Payload 144: Elementary NAL, Big-Endian, MaxCLL/MaxFALL in $1\text{ cd/m}^2$.
   - MP4 `mdcv` Box: ISOBMFF, Big-Endian, 24 bytes, Max Lum in $0.0001\text{ cd/m}^2$.
   - MP4 `clli` Box: ISOBMFF, Big-Endian, 4 bytes, MaxCLL/MaxFALL in $1\text{ cd/m}^2$.
3. **Independent Verification:**
   - Verification requires raw NAL bitstream inspection via an independent tool (e.g. FFprobe) verifying `profile_idc == 2`, `bit_depth == 10`, `colour_primaries == 9`, `transfer_characteristics == 16`, and prefix SEI payloads 137 and 144.

---

## 8. HARDWARE CLASSIFICATION CHANGES

The hardware evaluation was elevated to an exhaustive 20-dimension matrix:
- **Dimensions Evaluated:** Hardware decoder profiles, hardware encoder profiles, software decoder profiles, software encoder profiles, HEVC Main 8-bit, HEVC Main10 10-bit, resolutions & frame rates, encoder surface-input, EGL configuration availability, EGL 10-bit precision delivery, OpenGL ES version, fragment shader precision, 3D texture support, max texture dimensions, MediaCodec HDR metadata transport, bitstream metadata emission, audio codec compatibility, thermal behavior, sustained transcode throughput, and output validation compliance.
- **Explicit Classifications:** Standardized to 10 unambiguous states (`PHYSICALLY VERIFIED`, `IMPLEMENTED AND TESTED`, `ARCHITECTURALLY READY`, `THEORETICALLY SUPPORTED`, `NOT PHYSICALLY VERIFIED`, `HARDWARE UNSUPPORTED`, `SOFTWARE FALLBACK AVAILABLE`, `PLANNED`, `BLOCKED`, `NOT EXECUTED`).
- **Device Findings:**
  - Xiaomi Redmi Note SM6225: Formally classified as `HARDWARE UNSUPPORTED` for HEVC Main10; gated honestly to 8-bit Main SDR with `"8-Bit HW Only"` UI badge.
  - OnePlus Snapdragon 8 Gen 3 Target: Formally classified as `ARCHITECTURALLY READY` with status `MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED`.

---

## 9. REGRESSION GOVERNANCE CHANGES

A mandatory regression policy was established:
- **Zero-Regression Rule:** The entire existing test suite (82 host unit tests and 18 physical device instrumentation tests) must pass before and after every phase.
- **Covered Domains:** Downloader URL routing and stream assembly, MediaCodec hardware encoding and rotation preservation, Room database foreign keys and migrations, Scoped Storage isolation, and `DESIGN.md` theme/component consistency.
- **Gating Enforcement:** A phase cannot be marked complete if any existing feature regresses.

---

## 10. DOCUMENTATION-ONLY DIFF SUMMARY

The git diff shows that only documentation was modified:
- `UNIDOWNCO_MASTER_PRODUCT_AND_TECHNICAL_ROADMAP.md`: Updated in place (Revision 1.1).
- `UNIDOWNCO_MASTER_ROADMAP_REVISION_1_1_REPORT.md`: Created revision report.
- **Application Source Files:** 0 files changed.
- **Gradle / Build Files:** 0 files changed.
- **Database Schemas:** 0 files changed.

---

## 11. TESTS AND DOCUMENTATION CHECKS PERFORMED

1. **Markdown Structural Verification:** Verified header nesting, table alignments, code blocks, and math formulas throughout both documents.
2. **Cross-Reference Audit:** Verified file links against existing repository paths.
3. **Scope & Prohibition Audit:** Confirmed that no source files, Gradle scripts, or APK builds were initiated.

---

## 12. REMAINING LIMITATIONS

1. **Physical Main10 Hardware Execution:** Physical verification of 10-bit HEVC Main10 hardware encoding remains unexecuted due to the physical absence of compatible hardware (SM8650 target).
2. **Optical Flow Motion Interpolation:** Real-time 60 FPS motion interpolation remains in the research/postponed category.
3. **Dolby Vision Authoring:** Remains excluded due to proprietary licensing and patent boundaries.

---

## 13. EXPLICIT CONFIRMATION: NO IMPLEMENTATION BEGUN

- **No source code was modified.**
- **No database migrations were executed.**
- **No APKs or AABs were generated.**
- **No GitHub releases or tags were created or altered.**
- **Phase 1 has NOT been started.**
- **The project is in a clean, documented, and fully governed state.**
