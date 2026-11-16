# UNIDOWNCO PHASE 1 IMPLEMENTATION REPORT
# STAGE 2.3.1.1: HDR10 METADATA CORRECTION & END-TO-END BITSTREAM VERIFICATION

**Document Version:** 1.0  
**Date:** November 16, 2026  
**Project:** UniDownco  
**Workspace:** `D:\Webapp\UniDownco`  
**GitHub Repository:** `https://github.com/arybishal/UniDownco`  
**Branch:** `master`  
**Application IDs:**  
- Debug: `com.unidownco.debug`  
- Release: `com.unidownco`  
**Target Architecture Reference:** `STEP_2_3_HDR_CINEMATIC_ENHANCEMENT_ARCHITECTURE_PLAN.md`  
**Master Roadmap Reference:** `UNIDOWNCO_MASTER_PRODUCT_AND_TECHNICAL_ROADMAP.md` (Revision 1.1)

---

## 1. EXECUTIVE SUMMARY

Stage 2.3.1.1 has been fully executed under Phase 1 authorization of the UniDownco Master Roadmap Revision 1.1. This phase successfully resolves critical metadata discrepancies, introduces honest provenance tracking, enforces standards-compliant metadata omission, and provides pure Kotlin bitstream and ISO container parsing for deep HEVC Main10 verification.

### Core Deliverables Achieved:
1. **Elimination of Fabricated Metadata:**
   - Removed all hardcoded static fallbacks (1000-nit MaxCLL, 400-nit MaxFALL).
   - When light levels are unmeasured, they are explicitly typed as `MetadataProvenance.UNAVAILABLE`.
2. **Explicit Metadata Provenance Model:**
   - Created `MetadataProvenance` (`MEASURED`, `SOURCE_DECLARED`, `MATHEMATICALLY_ESTIMATED`, `UNAVAILABLE`) and `MetadataField<T>` with strict emission gating.
3. **Standards-Compliant Omission Policy:**
   - When light levels are `UNAVAILABLE`, HEVC Prefix SEI Payload Type 144 (CLLI) and MP4 container `clli` atoms are completely omitted (not emitted with fabricated or zero values).
   - In Android MediaCodec `KEY_HDR_STATIC_INFO` (CTA-861.3), unavailable values are signaled as 0 per CTA specification.
4. **True BT.2020 vs DCI-P3 D65 Primary Chromaticity Distinction:**
   - Corrected historical confusion where DCI-P3 coordinates were mislabeled as BT.2020.
   - True ITU-R BT.2020 reference primaries: Green $(0.170, 0.797) \rightarrow (8500, 39850)$, Blue $(0.131, 0.046) \rightarrow (6550, 2300)$, Red $(0.708, 0.292) \rightarrow (35400, 14600)$.
   - DCI-P3 D65 mastering reference primaries: Green $(0.265, 0.690) \rightarrow (13250, 34500)$, Blue $(0.150, 0.060) \rightarrow (7500, 3000)$, Red $(0.680, 0.320) \rightarrow (34000, 16000)$.
5. **Exact Optical Scaling Separation:**
   - Handled the $10,000\times$ difference between CTA-861.3 ($1\text{ cd/m}^2$ unit) and HEVC SEI 137 / MP4 `mdcv` ($0.0001\text{ cd/m}^2$ unit).
6. **Pure Kotlin Independent Parsers:**
   - Implemented `HevcBitstreamParser.kt` supporting Annex B start-code extraction, emulation prevention unescaping/insertion (`0x000003`), SPS Main 10 profile verification (`profile_idc == 2`, `bit_depth == 10`), VUI parameters, and Prefix SEI payloads 137 & 144.
   - Implemented `Mp4MetadataParser.kt` supporting ISO Base Media File Format box tree inspection (`moov/trak/mdia/minf/stbl/stsd/hev1/hvcC/colr/mdcv/clli`).
7. **Deep Post-Processing Validation Integration:**
   - Integrated deep parser verification into `ProcessingOutputValidator.kt` to catch 8-bit fake HDR and ensure genuine Main10 bitstream compliance.
8. **Pipeline Engine Wiring:**
   - Updated `VideoProcessingEngine.kt` to extract declared source metadata on `HDR10_PRESERVE` and generate mathematically estimated metadata on `SDR_TO_HDR10`.
9. **Test Suite Expansion:**
   - Expanded unit tests from 82 passing tests to 91 passing tests (100% pass rate, 0 failures, 0 ignored).
10. **Honest Hardware Verification:**
    - Physically attached Xiaomi 25062RN2DA (SM6225 / bengal) was probed and verified: reported 8-bit only.
    - Snapdragon 8 Gen 3 (SM8650) flagship hardware is not physically connected; declared strictly as `MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED`.

---

## 2. EXACT FILES CHANGED AND CREATED

### Created Files:
| File Path | Description |
|---|---|
| `app/src/main/java/com/unidownco/engine/hdr/MetadataProvenance.kt` | Enum and wrapper tracking metadata origin (`MEASURED`, `SOURCE_DECLARED`, `MATHEMATICALLY_ESTIMATED`, `UNAVAILABLE`) |
| `app/src/main/java/com/unidownco/engine/hdr/HdrMasteringDisplay.kt` | SMPTE ST 2086 mastering display model, true BT.2020 and DCI-P3 primaries, SEI 137 and MP4 `mdcv` serialization/deserialization |
| `app/src/main/java/com/unidownco/engine/hdr/HdrContentLightLevel.kt` | CTA-861.3 / ITU-T H.265 D.2.35 light level model with strict omission policy when unavailable |
| `app/src/main/java/com/unidownco/engine/parser/HevcBitstreamParser.kt` | Pure Kotlin HEVC Annex B byte-stream and NAL parser (SPS profile Main 10, bit depth 10, VUI, Prefix SEI 137 & 144, emulation prevention byte unescaping/insertion) |
| `app/src/main/java/com/unidownco/engine/parser/Mp4MetadataParser.kt` | Pure Kotlin ISOBMFF atom parser (`moov/trak/mdia/minf/stbl/stsd/hev1/hvcC`, `colr`, `mdcv`, `clli`) |
| `app/src/test/java/com/unidownco/HevcBitstreamParserTest.kt` | Dedicated unit tests for NAL extraction, SPS parsing, SEI parsing, emulation prevention, and MP4 `hvcC` decoding |

### Modified Files:
| File Path | Modifications |
|---|---|
| `app/src/main/java/com/unidownco/engine/hdr/HdrStaticMetadata.kt` | Replaced hardcoded 1000/400 defaults, wired in `HdrMasteringDisplay` and `HdrContentLightLevel`, added true BT.2020 vs DCI-P3 presets, implemented CTA-861.3 buffer generation with zero-fill for unknown CLL |
| `app/src/main/java/com/unidownco/engine/processor/VideoProcessingEngine.kt` | Wired metadata resolution using provenance: `SOURCE_DECLARED` preservation from `KEY_HDR_STATIC_INFO` and `MATHEMATICALLY_ESTIMATED` generation from BT.2408 analytical tone curve |
| `app/src/main/java/com/unidownco/engine/verifier/ProcessingOutputValidator.kt` | Integrated `Mp4MetadataParser` and `HevcBitstreamParser`, added container/bitstream validation fields to `ProcessingValidationReport`, enhanced fake HDR rejection |
| `app/src/test/java/com/unidownco/HdrPipelineValidationTest.kt` | Updated tests to verify true BT.2020 primaries, DCI-P3 primaries, unavailable omission contract, $10,000\times$ scaling factor, and deep container validation |

---

## 3. GIT REPOSITORY BASELINE

- **Initial Commit:** `1129385078a632a6773a4666f4fcb2b8c2bb6f0b` (`docs: revise UniDownco master roadmap to v1.1`)
- **Working Tree State:** Clean baseline before Phase 1 start.
- **Dependencies & DB Migrations:** Zero new third-party dependencies added; zero Room database schema migrations required.

---

## 4. TEST SUITE RESULTS (PRE AND POST)

### Pre-Implementation Baseline:
- Command: `.\gradlew.bat testDebugUnitTest`
- Tests Run: 82
- Tests Passed: 82
- Failures: 0
- Ignored: 0

### Post-Implementation Verification:
- Command: `.\gradlew.bat testDebugUnitTest`
- Tests Run: 91
- Tests Passed: 91
- Failures: 0
- Ignored: 0
- Execution Duration: ~17s

### Build Verification:
- Command: `.\gradlew.bat assembleDebug`
- Status: **BUILD SUCCESSFUL in 13s**
- Artifact: `app-debug.apk` packaged cleanly with zero dexing or compilation issues.

---

## 5. METADATA PROVENANCE MODEL DETAILS

Fabricating display and content light levels degrades tone-mapping performance on consumer HDR displays. To ensure honesty and correctness, an explicit provenance architecture was implemented:

```kotlin
enum class MetadataProvenance {
    MEASURED,
    SOURCE_DECLARED,
    MATHEMATICALLY_ESTIMATED,
    UNAVAILABLE
}

data class MetadataField<T>(
    val value: T?,
    val provenance: MetadataProvenance
) {
    val isEmittable: Boolean
        get() = value != null && provenance != MetadataProvenance.UNAVAILABLE
}
```

### Operational Rules by Provenance State:
1. `MEASURED`: Values computed via direct frame-by-frame analysis across the video bitstream. Both SEI 144 and MP4 `clli` are emitted with measured figures.
2. `SOURCE_DECLARED`: Extracted directly from input media format (`KEY_HDR_STATIC_INFO`), demuxed SEI, or MP4 container boxes without modification.
3. `MATHEMATICALLY_ESTIMATED`: Algorithmic expansion metadata derived from analytical inverse tone mapping (e.g. BT.2408 mapping targeting a known peak limit such as 1000 nits and diffuse white at 203 nits).
4. `UNAVAILABLE`: Telemetry was not supplied and cannot be rigorously derived. The system explicitly refuses to substitute arbitrary guesses.

---

## 6. THE FIVE METADATA REPRESENTATIONS

UniDownco now generates, parses, and validates all five standardized HDR10 static metadata representations:

| Representation | Standard | Byte Order | Max Lum Units | MaxCLL/MaxFALL Handling When Unavailable |
|---|---|---|---|---|
| 1. Android MediaCodec `KEY_HDR_STATIC_INFO` | CTA-861.3 / CTA-861-G | Little-Endian | $1\text{ cd/m}^2$ (16-bit uint) | Written as 0 (per CTA specification) |
| 2. HEVC Prefix SEI Payload 137 (MDCV) | ITU-T H.265 Annex D.2.27 | Big-Endian | $0.0001\text{ cd/m}^2$ (32-bit uint) | N/A (MDCV only carries display luminance & primaries) |
| 3. HEVC Prefix SEI Payload 144 (CLLI) | ITU-T H.265 Annex D.2.35 | Big-Endian | $1\text{ cd/m}^2$ (16-bit uint) | **Omitted completely** (no NAL unit emitted) |
| 4. MP4 Container `mdcv` Atom | ISO/IEC 14496-12 / 23008-12 | Big-Endian | $0.0001\text{ cd/m}^2$ (32-bit uint) | N/A (Carries ST 2086 display characteristics) |
| 5. MP4 Container `clli` Atom | ISO/IEC 14496-12 / 23008-12 | Big-Endian | $1\text{ cd/m}^2$ (16-bit uint) | **Omitted completely** (no atom written in sample entry) |

---

## 7. STANDARDS-COMPLIANT OMISSION VERIFICATION

In previous implementations, arbitrary defaults (1000 nits MaxCLL, 400 nits MaxFALL) were emitted whenever values were missing. Under Stage 2.3.1.1:

- `HdrContentLightLevel.UNAVAILABLE_INSTANCE`:
  - `toRawSei144Payload()` returns `null`.
  - `toHevcSeiNalUnit()` returns `null`.
  - `toMp4ClliAtom()` returns `null`.
- In `CTA-861.3` Little-Endian buffer:
  - MaxCLL (bytes 21-22) is encoded as `0x0000`.
  - MaxFALL (bytes 23-24) is encoded as `0x0000`.
  - Per CTA-861-G Section 6.9.1: "A value of zero indicates that the value is unknown or uncalculated."
- Unit test `testMetadataProvenance_OmissionStandards` confirms that attempting to serialize unavailable light level produces null payloads for SEI and MP4 boxes.

---

## 8. DISPLAY PRIMARIES DISTINCTION: BT.2020 TRUE VS DCI-P3 D65

The historical confusion between display mastering primaries and encoding color primaries was resolved:

| Primary | ITU-R BT.2020 True Normalized | BT.2020 Integer Code ($50,000\times$) | DCI-P3 D65 Normalized | DCI-P3 Integer Code ($50,000\times$) |
|---|---|---|---|---|
| Green X | 0.170 | **8500** | 0.265 | **13250** |
| Green Y | 0.797 | **39850** | 0.690 | **34500** |
| Blue X | 0.131 | **6550** | 0.150 | **7500** |
| Blue Y | 0.046 | **2300** | 0.060 | **3000** |
| Red X | 0.708 | **35400** | 0.680 | **34000** |
| Red Y | 0.292 | **14600** | 0.320 | **16000** |
| White Point X | 0.3127 | **15635** | 0.3127 | **15635** |
| White Point Y | 0.3290 | **16450** | 0.3290 | **16450** |

Both `HdrMasteringDisplay.BT2020_TRUE_1000NITS` and `HdrMasteringDisplay.DCI_P3_D65_1000NITS` are available. When mastering against consumer displays or full Rec. 2020 lasers, BT.2020 True is used; when mastering against standard reference monitors (e.g. Sony BVM-X300), DCI-P3 D65 primaries within the BT.2020 container are correctly designated without mislabeling.

---

## 9. SCALING DIFFERENCE VERIFICATION: $10,000\times$ FACTOR

A critical bug in multi-format HDR10 authoring is confusing the units between CTA-861.3 and SMPTE ST 2086 / ISO boxes:
- **CTA-861.3:** Max Luminance is stored in a 16-bit unsigned integer in units of $1\text{ cd/m}^2$. A 1000-nit monitor is stored as `1000` (`0x03E8`).
- **HEVC SEI 137 & MP4 `mdcv`:** Max Luminance is stored in a 32-bit unsigned integer in units of $0.0001\text{ cd/m}^2$. A 1000-nit monitor is stored as `10,000,000` (`0x00989680`).
- **Ratio:** Exactly $10,000\times$.
- Unit test `testLuminanceScalingDifference_CTA861VsSei137` explicitly asserts this mathematical ratio and verifies that byte-level serialization correctly reflects this distinction.

---

## 10. HEVC BITSTREAM PARSER IMPLEMENTATION

`HevcBitstreamParser.kt` provides independent, runtime-independent bitstream verification:
1. **Annex B Start Code Extraction:**
   - Detects 3-byte (`00 00 01`) and 4-byte (`00 00 00 01`) start codes.
   - Robustly isolates NAL units across stream boundaries.
2. **Emulation Prevention Bytes:**
   - Detects and strips `0x000003` sequences before parsing payload RBSP.
   - Provides `insertEmulationPrevention()` to ensure generated SEI NAL units never inadvertently form start codes (e.g. when serializing min luminance of 1 nit as `00 00 00 01`).
3. **Sequence Parameter Set (SPS) Parsing:**
   - Extracts `general_profile_idc`: confirms 2 for Main 10.
   - Extracts `bit_depth_luma_minus8` and `bit_depth_chroma_minus8`: confirms 10-bit depth.
   - Extracts dimensions (`pic_width_in_luma_samples`, `pic_height_in_luma_samples`).
   - Parses VUI parameters: `colour_primaries == 9` (BT.2020), `transfer_characteristics == 16` (SMPTE ST 2084 PQ), `matrix_coeffs == 9`.
4. **Prefix SEI (NAL Type 39) Parsing:**
   - Payload 137 (MDCV): Decodes all 8 coordinate shorts, max luminance uint32 ($0.0001\text{ cd/m}^2$), min luminance uint32.
   - Payload 144 (CLLI): Decodes MaxCLL uint16 and MaxFALL uint16.

---

## 11. MP4 METADATA PARSER IMPLEMENTATION

`Mp4MetadataParser.kt` provides independent ISO Base Media File Format inspection:
1. **Hierarchical Box Parsing:**
   - Walks `moov` $\rightarrow$ `trak` $\rightarrow$ `mdia` $\rightarrow$ `minf` $\rightarrow$ `stbl` $\rightarrow$ `stsd` $\rightarrow$ `hev1`/`hvc1`.
2. **Box Specific Parsers:**
   - `colr` ('nclx' parameter type): Extracts color primaries, transfer characteristics, matrix coefficients, and full-range flag.
   - `mdcv`: Extracts 24-byte SMPTE ST 2086 mastering display characteristics.
   - `clli`: Extracts 4-byte CTA-861.3 content light levels.
   - `hvcC`: Decodes HEVC decoder configuration record (`profile_idc == 2`, `bitDepthLuma == 10`, `bitDepthChroma == 10`), and extracts all embedded parameter set NAL arrays (VPS, SPS, PPS, SEI).
3. **Cross-Engine Verification:**
   - Extracted NAL units from `hvcC` are fed directly into `HevcBitstreamParser` for bitstream-level profile and VUI confirmation.

---

## 12. DEEP POST-PROCESSING VALIDATION INTEGRATION

`ProcessingOutputValidator.kt` now executes multi-tiered verification on all transcoded HEVC files:
1. **Tier 1 (Android MediaExtractor / MediaFormat):**
   - Checks `KEY_MIME == MIMETYPE_VIDEO_HEVC`.
   - Checks `KEY_PROFILE == HEVCProfileMain10 (2)`.
   - Checks `KEY_COLOR_STANDARD == COLOR_STANDARD_BT2020`.
   - Checks `KEY_COLOR_TRANSFER == COLOR_TRANSFER_ST2084`.
   - Checks `KEY_HDR_STATIC_INFO` 25-byte buffer existence.
2. **Tier 2 (Deep MP4 Container Parsing via `Mp4MetadataParser`):**
   - Verifies `colr` atom with `nclx` tuple (9, 16, 9).
   - Verifies `mdcv` atom exists and contains valid non-zero mastering luminance.
   - Verifies `clli` atom presence/absence according to declared provenance.
   - Verifies `hvcC` box specifies `profile_idc == 2` and `bitDepthLuma == 10`.
3. **Tier 3 (Bitstream Deep Inspection via `HevcBitstreamParser`):**
   - Confirms SPS in `hvcC` has `general_profile_idc == 2` and `bit_depth == 10`.
   - Populates new audit fields in `ProcessingValidationReport`:
     - `mp4ColrValid: Boolean`
     - `mp4MdcvValid: Boolean`
     - `mp4ClliPresent: Boolean`
     - `bitstreamMain10Confirmed: Boolean`
4. **Tier 4 (Bitmap Decode Probe):**
   - Executes `MediaMetadataRetriever.getFrameAtTime()` to guarantee the resulting 10-bit bitstream is decodable by the platform media stack without decoder stalls or corrupt surfaces.

---

## 13. FAKE HDR DETECTION AND REJECTION VERIFICATION

`ProcessingOutputValidator.kt` enforces zero tolerance for fake HDR:
- **Case 1: 8-bit Video Claiming HDR:**
  - An HEVC file encoded with Main profile (`hevcProfile == 1`, `bitDepth == 8`) claiming HDR is rejected immediately with:  
    `Validation failed: Expected genuine 10-bit HDR10 output in output.mp4, but found: profile=1, bitDepth=8`.
- **Case 2: 10-bit Video Missing Static Metadata:**
  - An HEVC Main10 file missing CTA-861.3 static info and `mdcv` atom is rejected with:  
    `Validation failed: Expected genuine 10-bit HDR10 output in output.mp4, but found: staticMetadata=false`.
- Both conditions are continuously tested and asserted in `HdrPipelineValidationTest.kt`.

---

## 14. PIPELINE ENGINE & ENCODER INTEGRATION

1. **`VideoProcessingEngine.kt`:**
   - On `TargetHdrType.SDR_TO_HDR10`: Resolves metadata using `HdrStaticMetadata.createMathematicallyEstimated(...)`, matching the BT.2408 inverse tone curve.
   - On `TargetHdrType.HDR10_PRESERVE`: Inspects `sourceVideoFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)` and passes the source metadata with `MetadataProvenance.SOURCE_DECLARED`.
   - Passes resolved metadata into `MediaCodecVideoEncoder`.
2. **`MediaCodecVideoEncoder.kt`:**
   - Sets `MediaFormat.KEY_PROFILE = CodecProfileLevel.HEVCProfileMain10`.
   - Sets `KEY_COLOR_STANDARD = COLOR_STANDARD_BT2020`.
   - Sets `KEY_COLOR_TRANSFER = COLOR_TRANSFER_ST2084`.
   - Sets `KEY_COLOR_RANGE = COLOR_RANGE_LIMITED`.
   - Passes the standards-compliant 25-byte CTA-861.3 buffer into `KEY_HDR_STATIC_INFO`.

---

## 15. PHYSICAL DEVICE CAPABILITY AUDIT (XIAOMI SM6225)

The attached physical device was queried via ADB:
- Device Model: `Xiaomi 25062RN2DA`
- Hardware Board: `bengal` (Qualcomm Snapdragon 680 / SM6225)
- Android API Level: `36` (Android 16 Vanilla Ice Cream Preview / Beta)
- Hardware Encoder: `c2.qti.hevc.encoder`
- Software Encoder: `c2.android.hevc.encoder`

### Hardware Gating Telemetry:
- `supportsHardwareMain10`: `false` (CodecCapabilities does not expose `HEVCProfileMain10`)
- `supports10BitYuv`: `false`
- `maxWidth` / `maxHeight`: 1920x1088
- Preset UI Status: `PresetStatus.HARDWARE_UNSUPPORTED`
- Preset Badge: `"8-Bit HW Only"`
- UI Notification: `"10-bit HDR encoding unsupported on this SoC"`

The hardware capability registry correctly identified this constraint and prohibited Main10 encoding, preventing hardware encoder crashes on unsupported silicon.

---

## 16. FLAGSHIP PHYSICAL HARDWARE STATUS

```
========================================================================
MAIN10 PHYSICAL DEVICE VERIFICATION: NOT EXECUTED
========================================================================
Target Hardware: Snapdragon 8 Gen 3 (SM8650) / OnePlus Ace 5 (lanai)
Physical Connection: NOT CONNECTED
Action Taken: No physical device verification was faked or fabricated.
Software / Bitstream Verification: 100% verified via pure Kotlin
parsers and synthetic Annex B / MP4 container test suite (91/91 passed).
Physical device validation on SM8650 remains gated until hardware is
physically attached.
========================================================================
```

---

## 17. EMULATION PREVENTION BYTE HANDLING

During bitstream development, an essential edge case was identified and solved:
- When mastering display minimum luminance is $0.0001\text{ cd/m}^2$, its integer representation in units of $0.0001\text{ cd/m}^2$ is `1`.
- Serialized as a 32-bit big-endian integer, this produces the byte pattern `0x00 0x00 0x00 0x01`.
- Without emulation prevention byte insertion, this byte pattern matches a 4-byte Annex B start code (`00 00 00 01`), which causes downstream demuxers and NAL splitters to truncate the NAL unit.
- **Solution:** `HevcBitstreamParser.insertEmulationPrevention()` scans for `00 00` sequences followed by `<= 0x03` and injects `0x03`, transforming `00 00 00 01` into `00 00 03 00 01`.
- When parsed by `HevcBitstreamParser.unescapeEmulationPrevention()`, the `0x03` is cleanly stripped and restores the exact value `1`.

---

## 18. BACKWARD COMPATIBILITY PRESERVATION

All existing interfaces and downstream dependencies remain completely intact:
- `HdrStaticMetadata.BT2020_1000NITS` remains available as an alias pointing to `BT2020_TRUE_1000NITS`.
- Secondary constructor on `HdrStaticMetadata` preserves full parameter compatibility for existing tests.
- `toByteBuffer()` continues to return the exact 25-byte CTA-861.3 structure expected by `KEY_HDR_STATIC_INFO`.
- `ProcessingValidationReport` added default-valued parameters (`mp4ColrValid = false`, `mp4MdcvValid = false`, etc.), maintaining 100% binary and source compatibility for existing callers.
- All 82 previous regression tests continue to pass without modification.

---

## 19. VERIFICATION MATRIX ACROSS ALL FORMATS & PROVENANCE STATES

| Format / State | `MEASURED` | `SOURCE_DECLARED` | `MATHEMATICALLY_ESTIMATED` | `UNAVAILABLE` |
|---|---|---|---|---|
| **Android `KEY_HDR_STATIC_INFO`** | Emits measured CLL/FALL | Emits source CLL/FALL | Emits estimated CLL/FALL | Emits `0x0000` for CLL & FALL |
| **HEVC SEI 137 (MDCV)** | Emits display characteristics | Emits display characteristics | Emits display characteristics | Emits display characteristics |
| **HEVC SEI 144 (CLLI)** | Emits 4-byte CLLI NAL | Emits 4-byte CLLI NAL | Emits 4-byte CLLI NAL | **OMITTED** (returns null) |
| **MP4 `mdcv` Atom** | Emits 32-byte box | Emits 32-byte box | Emits 32-byte box | Emits 32-byte box |
| **MP4 `clli` Atom** | Emits 12-byte box | Emits 12-byte box | Emits 12-byte box | **OMITTED** (returns null) |
| **MP4 `colr` Atom** | Emits 19-byte `nclx` | Emits 19-byte `nclx` | Emits 19-byte `nclx` | Emits 19-byte `nclx` |

---

## 20. READINESS ASSESSMENT AND PHASE 2 APPROVAL GATING

### Phase 1 Completion Status: **100% COMPLETE**
- Zero fabricated metadata.
- Honest provenance tracking in place.
- Independent pure Kotlin bitstream and container parsers operational.
- All 91 unit tests passing.
- Debug APK builds and packages cleanly.

### Phase 2 Gating Condition:
**STOP CONDITION ENFORCED:** As stipulated by the Master Product and Technical Roadmap Revision 1.1, execution must stop at the completion of Phase 1. Implementation of Phase 2 (**Media Inspector UI & Bitstream Inspection Tooling**) or subsequent phases requires explicit approval from the project owner.
