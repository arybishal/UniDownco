# UniDownco Design System

## Visual Identity
**WHITE CANVAS. BLACK STRUCTURE. YELLOW ACCENT. INTER TYPOGRAPHY.**

UniDownco features a high-contrast, editorial, engineering-grade design language. It combines the clean precision of a white canvas with sharp black structural framing, subtle warm gray dividers, and a signature high-visibility yellow (`#FFD429`) used strictly for primary call-to-actions, active indicators, and progress states.

---

## 1. Color Palette

### Light Mode (Primary Canvas)
| Token | Hex | Role |
| :--- | :--- | :--- |
| `Background` | `#FFFFFF` | Core screen canvas |
| `Surface` | `#FFFFFF` | Card surfaces, modal layers |
| `SurfaceSubtle` | `#F8F9FA` | Secondary inputs, chip backgrounds, code blocks |
| `PrimaryText` | `#000000` | Headlines, titles, active labels |
| `SecondaryText`| `#333333` | Subtitles, body copy, descriptions |
| `TertiaryText` | `#777777` | Captions, timestamps, disabled states |
| `PrimaryBorder`| `#000000` | 1dp solid structural borders on primary cards & inputs |
| `SubtleBorder` | `#E0E0E0` | Subtle hairline dividers |
| `AccentYellow` | `#FFD429` | Signature action accent: download buttons, active toggles, progress bars |
| `AccentYellowDark`| `#E5BD1B`| Pressed / hover state for yellow accent |
| `Success` | `#107C41` | Verification checks, completed badges |
| `Error` | `#D83B01` | Failure notifications, network errors |

### Dark Mode (OLED / High-Contrast Adaptation)
Preserves the exact structural identity and accent discipline in dark environments:
| Token | Hex | Role |
| :--- | :--- | :--- |
| `Background` | `#0E0E10` | Deep black/charcoal canvas |
| `Surface` | `#18181B` | Elevated card surfaces |
| `SurfaceSubtle` | `#222226` | Secondary containers |
| `PrimaryText` | `#FFFFFF` | Headlines, titles, active labels |
| `SecondaryText`| `#D1D1D6` | Subtitles, body copy |
| `TertiaryText` | `#8E8E93` | Captions, timestamps, disabled |
| `PrimaryBorder`| `#333338` | Structural framing borders |
| `SubtleBorder` | `#27272A` | Subtle dividers |
| `AccentYellow` | `#FFD429` | Signature action accent |
| `AccentYellowDark`| `#FFE066`| Pressed / high-contrast focus |

*Note: Signature yellow (`#FFD429`) is never used for broad surface fills or large backdrops. It is strictly reserved for primary download triggers, progress bars, active toggle thumbs, and selected filters.*

---

## 2. Typography

Three purposeful type families define the hierarchy:

1. **Inter** (`FontFamily.SansSerif` / bundled Inter):
   - Used for all standard application UI: navigation bars, titles, body copy, buttons, dialogs, form fields.
   - Weights: Regular (400), Medium (500), SemiBold (600), Bold (700).

2. **Noto Sans**:
   - Multilingual support and Devanagari/Nepali fallback typography.

3. **JetBrains Mono** (`FontFamily.Monospace` / bundled JetBrains Mono):
   - Dedicated strictly to technical metadata:
     - Codecs: `HEVC Main10`, `AVC`, `AV1`, `VP9`
     - Formats: `HDR10`, `HDR10+`, `Dolby Vision Profile 8.4`, `SDR`
     - Video metrics: `3840x2160`, `59.940 FPS`, `18.4 Mbps`, `48 kHz / 2 ch`
     - Hashes, URLs, and diagnostic logs.

### Type Scale
- **Display Large**: 28sp / Bold / Inter
- **Headline Medium**: 20sp / SemiBold / Inter
- **Title Medium**: 16sp / Medium / Inter
- **Body Large**: 15sp / Regular / Inter
- **Body Medium**: 13sp / Regular / Inter
- **Label Large**: 14sp / SemiBold / Inter (Buttons & CTAs)
- **Label Small**: 11sp / Medium / Inter (Tags & badges)
- **Mono Technical**: 12sp / Medium / JetBrains Mono (Specs & metadata)

---

## 3. Spacing & Grid System

Based on a strict 4dp foundation:
- `Spacing.xxs` = 4dp
- `Spacing.xs`  = 8dp
- `Spacing.sm`  = 12dp
- `Spacing.md`  = 16dp
- `Spacing.lg`  = 24dp
- `Spacing.xl`  = 32dp
- `Spacing.xxl` = 40dp

Screen padding standard: **16dp** horizontal edge margin, **24dp** vertical section spacing.

---

## 4. Shapes & Geometry

- **Buttons & Inputs**: Rounded corners `10dp`.
- **Cards & Containers**: Rounded corners `12dp`.
- **Dialogs & Bottom Sheets**: Rounded corners `16dp` (top corners for sheets).
- **Badges & Tags**: Rounded corners `6dp` or fully pill-shaped `100dp`.
- **Borders**: 1dp solid black (`#000000` in Light mode, `#333338` in Dark mode).

---

## 5. Component Patterns

### Primary Button
- Background: `#000000` (Black in Light mode, White in Dark mode)
- Text / Icon: `#FFFFFF` (Bold)
- Border: 1dp solid `#000000`
- Corner radius: `10dp`
- Padding: `16dp` horizontal, `12dp` vertical
- Role: Primary screen actions, analysis triggers, modal confirmations.

### Accent Button (Signature Action)
- Background: `#FFD429` (Signature Yellow)
- Text / Icon: `#000000` (Bold)
- Border: 1dp solid `#000000`
- Corner radius: `10dp`
- Role: Dedicated strictly to high-intent download triggers (`DOWNLOAD 1080P`, `START DOWNLOAD`).

### Secondary / Outlined Button
- Background: `#FFFFFF` (Canvas surface)
- Text / Icon: `#000000` (Medium)
- Border: 1dp solid `#000000`
- Corner radius: `10dp`
- Role: Secondary controls (Pause, Cancel, Filter, Update).

### Destructive Button
- Background: `#FCE8E6` (Red subtle tint)
- Text / Icon: `#D83B01` (Bold)
- Border: 1dp solid `#D83B01`
- Corner radius: `10dp`
- Role: Delete video, clear logs, purge cache.

### Input Field (URL Bar)
- Background: `#FFFFFF`
- Text: `#000000`, placeholder `#777777`
- Border: 1.5dp solid `#000000`
- Corner radius: `10dp`
- Inline Quick-Action: "PASTE" button integrated inside or adjacent to field.

### Status Badges
- **Downloading**: `#FFD429` background with `#000000` text.
- **Completed**: `#E6F4EA` background with `#107C41` text + border.
- **Failed**: `#FCE8E6` background with `#D83B01` text + border.
- **Technical Tag (Codec/Resolution)**: `#F1F3F4` background with `#000000` JetBrains Mono text.

---

## 6. Implementation Rule
All Jetpack Compose code must reference `UniDownTheme`, `UniColors`, `UniSpacing`, and `UniTypography`. No hardcoded hex values or ad-hoc shapes are permitted across UI screens.
