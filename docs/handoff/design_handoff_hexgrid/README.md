# Handoff: HexGrid Launcher — Edit Mode & Settings Redesign

## Overview

This is a UI redesign for **HexGrid**, an Android home-screen launcher that arranges app icons in a hexagonal grid. The redesign covers two screens:

- **Edit Mode** — the bottom sheet panel that slides up over the launcher when the user edits hex layout parameters
- **Settings** — the full-screen settings page

The main interface (the hex grid itself) is unchanged. Only these two UI surfaces are redesigned.

---

## About the Design Files

The files in this bundle are **high-fidelity design references built in HTML/React**. They are prototypes showing the intended look and interactive behavior — not production code to copy directly.

The task for the developer is to **recreate these designs in the HexGrid Android codebase** using its existing UI framework (Jetpack Compose or XML views). Match colors, typography, spacing, and interactions as closely as possible while following the codebase's established patterns and component conventions.

`HexGrid Redesign v2.html` — open in any modern browser to inspect and interact with the prototype. The design canvas lets you double-click each artboard (Edit Mode, Settings) to see it fullscreen at 390×844px.

---

## Fidelity

**High-fidelity.** This is a pixel-level reference. Colors, typography, spacing, border radii, shadow/glow values, and interaction states are all final. Recreate them faithfully.

---

## Design Tokens

### Colors

| Token | Value | Usage |
|---|---|---|
| `--bg` | `#040410` | Screen background |
| `--surface` | `#0d0d1e` | Card / sheet solid bg |
| `--sheet-glass` | `rgba(7,7,22,0.86)` + `backdrop-filter: blur(48px)` | Glass sheet variant |
| `--accent` | `#7c5cfc` | Primary accent (violet) |
| `--accent-b` | `#00d4ff` | Secondary accent (cyan) |
| `--text-1` | `#f0f0ff` | Headings, values |
| `--text-2` | `#9898c0` | Slider labels, body |
| `--text-3` | `#40405e` | Subtitles, descriptions |
| `--text-4` | `#36366a` | Section labels |
| `--divider` | `rgba(255,255,255,0.055)` | Hairline dividers |
| `--border-subtle` | `rgba(255,255,255,0.07)` | Card/sheet borders |
| `--border-accent` | `rgba(124,92,252,0.38)` | Customize card border |

**Accent is themeable.** The design supports three accent palettes:

| Name | Primary | Secondary |
|---|---|---|
| Violet (default) | `#7c5cfc` | `#00d4ff` |
| Coral | `#ff5555` | `#ffcc33` |
| Mint | `#00c9a7` | `#64dfdf` |

### Typography

| Role | Font | Size | Weight | Other |
|---|---|---|---|---|
| Screen title | Space Grotesk | 38px | 700 | letter-spacing: -1.5px |
| Sheet heading | Space Grotesk | 20px | 700 | letter-spacing: -0.4px |
| Card label | Space Grotesk | 11.5px | 700 | letter-spacing: 0.8px, UPPERCASE |
| Sub-section label | DM Sans | 10.5px | 700 | letter-spacing: 1.4px, UPPERCASE |
| Section label | DM Sans | 11px | 700 | letter-spacing: 1.8px, UPPERCASE |
| Body / slider label | DM Sans | 13px | 500 | — |
| Slider value badge | Space Grotesk | 12px | 700 | letter-spacing: 0.3px |
| Status bar time | DM Sans | 13px | 600 | — |

### Spacing & Radii

| Element | Value |
|---|---|
| Screen horizontal padding | 22–24px |
| Bottom sheet border-radius (top) | 28px |
| Hero card border-radius | 22px |
| Setting card border-radius | 20px (outer), 19px (inner) |
| Icon circle border-radius | 14px |
| Tab bar border-radius | 20px (container), 16px (item) |
| Slider thumb size | 22×22px |
| Slider track height | 8px |

### Shadows & Glows

| Element | Value |
|---|---|
| Slider thumb | `box-shadow: 0 0 0 4px rgba(124,92,252,0.16), 0 0 18px rgba(124,92,252,0.38), 0 2px 8px rgba(0,0,0,0.6)` |
| Card gradient border | `background: linear-gradient(140deg, rgba(accent,0.18) 0%, rgba(255,255,255,0.07) 60%, transparent 100%)` — wrapping 1px padding |
| Active tab glow | `box-shadow: 0 0 16px rgba(accent,0.12)` |
| Icon circle | `box-shadow: 0 6px 20px rgba(0,0,0,0.3)` |
| Atmospheric bg glow | Two blurred `border-radius: 50%` divs, `filter: blur(60px)`, 16% opacity |

---

## Screen 1 — Edit Mode

### Overview

A bottom sheet slides up over the hex launcher grid when the user enters edit mode. The launcher grid in the background is dimmed. The sheet has a segmented tab bar at the bottom switching between four panels: Shape, Style, Order, More.

### Background

- Full-screen hex grid (the existing launcher) shown behind the sheet
- `linear-gradient` vignette overlay: `rgba(4,4,16,0.3)` top → `rgba(4,4,16,0.6)` at 55%

### Bottom Sheet

- **Height:** 578px, anchored to bottom edge
- **Border-radius:** 28px top-left, 28px top-right, 0 bottom
- **Background (glass):** `rgba(7,7,22,0.86)`, `backdrop-filter: blur(48px) saturate(1.5)`
- **Background (solid):** `#0b0b1e`
- **Grain texture:** SVG `feTurbulence` noise overlay, `mix-blend-mode: overlay`, opacity `0.032`
- **Top accent line:** absolute, `height: 1.5px`, `background: linear-gradient(to right, transparent 5%, accent 30%, cyan 70%, transparent 95%)`
- **Drag handle:** centered pill, `width: 32px, height: 4px, border-radius: 2px`, `background: rgba(255,255,255,0.14)`, padding-top 14px

### Sheet Header (below handle)

- **Padding:** 12px top, 24px horizontal, 14px bottom
- **Title:** tab name, Space Grotesk 20px/700, `#f0f0ff`, letter-spacing -0.4px
- **Subtitle:** e.g. "5 parameters", DM Sans 12px/400, `#40405e`, margin-top 3px
- **Close button:** 34×34px circle, `background: rgba(255,60,60,0.1)`, `border: 1px solid rgba(255,60,60,0.22)`, `color: #ff6868`, ✕ symbol 15px
- **Hairline below header:** `height: 1px`, `background: rgba(255,255,255,0.055)`, `margin: 0 24px`

### Tab Bar

- **Container:** `background: rgba(255,255,255,0.035)`, `border-radius: 20px`, `padding: 3px`, padding 8px top / 24px bottom on wrapper
- **Each tab:** `flex: 1`, `border-radius: 16px`, `padding: 10px 0`, column layout (icon above label), font 10px/700 uppercase DM Sans, letter-spacing 0.4px
- **Active state:** `background: rgba(accent,0.13)`, `border: 1px solid rgba(accent,0.22)`, `color: #e8e8ff`, `box-shadow: 0 0 16px rgba(accent,0.12)`
- **Inactive state:** transparent bg, `color: #3a3a58`

**Tab items:**

| Tab | Icon | Panel |
|---|---|---|
| Shape | Hexagon outline SVG | Sliders + orientation |
| Style | 3 overlapping circles | Color swatches + toggles |
| Order | 3 horizontal lines | Instructional state |
| More | 3 dots horizontal | Toggle list |

### Shape Panel (default active tab)

**Sub-section labels:** `height: 14px, width: 3px` accent-colored left bar + uppercase 10.5px/700 label, color `#5a5a80`

**Sub-sections:**
- "Geometry" → Hex radius, Icon size, Icon padding
- "Detail" → Outline width, Corner radius

**Slider component (per slider):**

- Label: DM Sans 13px/500, `#9898c0`, left-aligned
- Value badge: right-aligned, `background: rgba(accent,0.13)`, `border: 1px solid rgba(accent,0.27)`, `color: #f0f0ff`, Space Grotesk 12px/700, padding `3px 11px`, border-radius 8px, min-width 40px
- Track: `height: 8px`, `border-radius: 999px`, unfilled: `rgba(255,255,255,0.07)`, filled: `linear-gradient(to right, accent, cyan)`
- Thumb: `22×22px` circle, white bg, glow shadow (see tokens above)
- Tick marks: 5 marks at 0/25/50/75/100%, DM Sans 9.5px/600, colored `rgba(accent,0.53)` if filled, else `rgba(255,255,255,0.14)`; only show `min` and `max` numeric values at 0% and 100%
- Margin between sliders: 24px

**Default slider values:**

| Slider | Min | Max | Default |
|---|---|---|---|
| Hex radius | 0 | 100 | 72 |
| Icon size | 0 | 100 | 52 |
| Icon padding | 0 | 100 | 40 |
| Outline width | 0 | 100 | 15 |
| Corner radius | 0 | 100 | 95 |

**Orientation Picker (below sliders):**

- Label: "Hex orientation", DM Sans 13px/500, `#9898c0`
- Two side-by-side buttons, `flex: 1`, `padding: 14px 0`, `border-radius: 14px`
- Each shows an SVG hexagon preview (52×52px) — `Pointy` = pointy-top orientation, `Flat` = flat-top
- Active: `border: 1.5px solid accent`, `background: rgba(accent,0.09)`, hex fill `rgba(accent,0.17)`, hex stroke = accent, label `#e0e0ff`
- Inactive: `border: 1.5px solid rgba(255,255,255,0.08)`, hex fill `rgba(255,255,255,0.03)`, hex stroke `rgba(255,255,255,0.18)`, label `#505070`
- Label: 11px/700 uppercase, centered below hex SVG

---

## Screen 2 — Settings

### Overview

Full-screen scrollable settings page. Background has two atmospheric radial glows. Content: header, Customize hero card, three 2-column grid sections (Tools, System, Backup).

### Background Glows

- Glow 1: `width/height: 260px`, `border-radius: 50%`, `background: rgba(accent,0.09)`, `filter: blur(60px)`, positioned `top: -60px, left: -60px`
- Glow 2: `width/height: 220px`, `border-radius: 50%`, `background: rgba(cyan,0.05)`, `filter: blur(60px)`, positioned `top: 100px, right: -80px`

### Header

- Padding: 18px top, 24px horizontal
- **Title:** "Settings", Space Grotesk 38px/700, `#f0f0ff`, letter-spacing -1.5px, line-height 1
- **Subtitle:** "Tune your launcher", DM Sans 13.5px/400, `#383862`, margin-top 8px
- **App icon badge** (top-right): 42×42px, `border-radius: 13px`, `background: linear-gradient(135deg, rgba(accent,0.38), rgba(cyan,0.25))`, `border: 1px solid rgba(accent,0.27)` — contains a small hex SVG (outer polygon filled with accent + inner polygon filled with bg color)

### Customize Hero Card

- Margin: 20px top, 18px horizontal, 24px bottom
- `border-radius: 22px`, `padding: 22px 22px 20px`
- `background: linear-gradient(140deg, rgba(accent,0.16) 0%, rgba(accent,0.06) 50%, rgba(cyan,0.05) 100%)`
- `border: 1px solid rgba(accent,0.22)`
- Grain overlay (same as sheet, opacity 0.025)
- **Hex color preview** (right side, overlapping): SVG with 6 colored hex cells (2 rows × 3 cols), positioned `right: -16px, top: -8px`, opacity 0.55. Cell colors: `#b71c1c`, `#4c1d95`, `#064e3b`, `#0c1a2e`, `#7c3aed`, `#1d4ed8`
- **Label:** "CUSTOMIZE", 10px/700, letter-spacing 2.5px, `rgba(accent, 0.8)`, margin-bottom 10px
- **Title:** "Make it yours", Space Grotesk 30px/700, letter-spacing -0.8px, `background: linear-gradient(120deg, #ffffff, cyan)`, `-webkit-background-clip: text`
- **Subtitle:** "Shape · Style · Order", 13px/400, `rgba(160,150,220,0.65)`, margin-top 6px
- **CTA row:** "Open Edit Mode" 12px/600 `rgba(accent,0.8)` + right arrow SVG in accent color, margin-top 16px

### Section Labels

Each section has a label row: `4×4px` accent-colored circle dot + uppercase text 11px/700, letter-spacing 1.8px, `color: #36366a`, padding `0 24px`, margin-bottom 12px.

### Setting Cards (× 6)

Grid: `display: grid, grid-template-columns: 1fr 1fr, gap: 10px, padding: 0 18px`

**Gradient border technique:**
```
Outer wrapper: border-radius 20px, padding 1px
  background: linear-gradient(140deg, rgba(accent,0.18) 0%, rgba(255,255,255,0.07) 60%, transparent 100%)
Inner card: border-radius 19px, padding 18px 16px
  background: #0d0d1e
```

**Card contents:**
- Icon circle: `44×44px`, `border-radius: 14px`, gradient background (per card), `box-shadow: 0 6px 20px rgba(0,0,0,0.3)`, margin-bottom 12px
- Label: Space Grotesk 11.5px/700, `#d8d8f0`, letter-spacing 0.8px, UPPERCASE
- Description: DM Sans 11px/400, `#3a3a5a`, margin-top 4px

**Card data:**

| Section | Card | Icon | Gradient | Description |
|---|---|---|---|---|
| Tools | Widgets | 4-square grid | `#f59e0b → #ef4444` | Place on launcher |
| Tools | App Visibility | Eye | `#ec4899 → #8b5cf6` | Show or hide apps |
| System | Permissions | Shield + checkmark | `#ef4444 → #f97316` | Manage access |
| System | Default Home | House | `#10b981 → #06b6d4` | Set as default |
| Backup | Backup | Upload arrow | `#6366f1 → #8b5cf6` | Save layout |
| Backup | Restore | Download arrow | `#06b6d4 → #3b82f6` | Load layout |

### Home Indicator

- Centered pill at bottom: `width: 120px, height: 4px, border-radius: 2px`, `background: rgba(255,255,255,0.12)`, padding 8px top / 20px bottom

---

## Interactions & Behavior

### Sliders

- Draggable by mouse and touch
- Value updates live as thumb is dragged
- Thumb glow pulses slightly on press (optional enhancement)
- Value badge background uses accent tint

### Tab Switching (Edit Mode)

- Tapping a tab instantly switches the content panel
- Active tab: accent fill + border + glow
- Inactive: fully transparent, dimmed color
- No transition animation required (instantaneous swap)

### Orientation Picker

- Two-option toggle (Pointy / Flat)
- Tapping switches which option is highlighted
- Selected: accent border + tinted background + full-opacity label
- Unselected: subtle border + dark background + dimmed label

### Close Button (Edit Mode)

- Tapping ✕ exits edit mode and dismisses the sheet
- Red tinted circle, brightens slightly on press

### Settings Cards

- Tappable — navigate to the respective sub-screen
- On press: slight background lightening (optional ripple on Android)

---

## Assets

| Asset | Description |
|---|---|
| `Space Grotesk` | Google Fonts — headings, labels, values |
| `DM Sans` | Google Fonts — body text, sliders |
| All icons | Inline SVG — no external icon library needed |
| Hex shapes | Pure SVG polygon, calculated at runtime from center + radius |

No external image assets are required.

---

## Files

| File | Description |
|---|---|
| `HexGrid Redesign v2.html` | Full interactive prototype — open in browser |
| `screens/overview.png` | Canvas overview screenshot |
| `README.md` | This document |

---

## Notes for Developer

1. **Accent theming:** The design is built around a swappable accent pair (`primary + secondary`). Expose this as a user preference in the app — at minimum support the three palettes listed in Design Tokens above.

2. **Glass effect on Android:** `backdrop-filter` blur is supported in WebView on Android 12+. For older targets, fall back to the solid sheet background (`#0b0b1e`) instead of the blur.

3. **Grain texture:** The SVG `feTurbulence` grain is a subtle detail — skip if it causes performance issues. The design looks fine without it.

4. **Slider interaction:** Implement with Android's `SeekBar` or a custom `Slider` composable in Jetpack Compose. The gradient fill on the track (accent → cyan) is a custom `DrawBehind` modifier in Compose.

5. **Hex orientation picker:** This is a simple two-state toggle — a `Row` of two `OutlinedButton`s with SVG/Canvas hex previews drawn inside. The hex polygon points can be computed the same way as in the prototype (`hexPts` function: 6 points around a center, with a 30° start offset for pointy-top).
