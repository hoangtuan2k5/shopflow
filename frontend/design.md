---
version: alpha
name: ShopFlow
description: Quiet commerce operations UI led by indigo structure and emerald status accents.
source: /final.png
colors:
  primary: "#1E2A5A"
  primary-hover: "#172044"
  secondary: "#5E667D"
  tertiary: "#00C391"
  tertiary-dark: "#007A5C"
  neutral: "#FAFAF8"
  muted: "#F0F2F5"
  surface: "#FFFFFF"
  foreground: "#18213F"
  border: "#DDE1E8"
  destructive: "#C73E3E"
  destructive-muted: "#FBEAEA"
  on-primary: "#FFFFFF"
  on-tertiary: "#18213F"
typography:
  display:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 3rem
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "0"
  h1:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 2.25rem
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "0"
  h2:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 1.5rem
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "0"
  h3:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 1.125rem
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "0"
  body:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 1rem
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "0"
  body-small:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 0.875rem
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0"
  label:
    fontFamily: "Inter, Segoe UI, ui-sans-serif, system-ui, sans-serif"
    fontSize: 0.75rem
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "0"
rounded:
  sm: 4px
  md: 6px
  lg: 8px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
shadows:
  sm: "0 1px 2px rgb(24 33 63 / 0.08)"
  md: "0 8px 24px rgb(24 33 63 / 0.12)"
breakpoints:
  sm: 640px
  lg: 1024px
  xl: 1280px
motion:
  fast: 150ms
  standard: 200ms
  easing: "ease-out"
icons:
  library: "@tabler/icons-vue"
  style: outline
  defaultSize: 20px
  defaultStrokeWidth: 1.8
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    hoverBackgroundColor: "{colors.primary-hover}"
    textColor: "{colors.on-primary}"
    focusColor: "{colors.primary}"
    minHeight: 40px
    rounded: "{rounded.md}"
    padding: 10px 16px
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    borderColor: "{colors.border}"
    minHeight: 40px
    rounded: "{rounded.md}"
    padding: 10px 16px
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.foreground}"
    borderColor: "{colors.border}"
    rounded: "{rounded.lg}"
    padding: 24px
    shadow: "{shadows.sm}"
  dialog:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.foreground}"
    borderColor: "{colors.border}"
    rounded: "{rounded.lg}"
    padding: 24px
    maxWidth: 448px
    shadow: "{shadows.md}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.foreground}"
    borderColor: "{colors.border}"
    focusColor: "{colors.primary}"
    minHeight: 40px
    rounded: "{rounded.md}"
    padding: 8px 12px
  status-in-stock:
    backgroundColor: "#E5F8F2"
    textColor: "{colors.tertiary-dark}"
    rounded: "{rounded.sm}"
    padding: 4px 8px
  status-out-of-stock:
    backgroundColor: "{colors.destructive-muted}"
    textColor: "{colors.destructive}"
    rounded: "{rounded.sm}"
    padding: 4px 8px
  navigation-item:
    textColor: "{colors.secondary}"
    activeBackgroundColor: "{colors.surface}"
    activeTextColor: "{colors.primary}"
    rounded: "{rounded.md}"
    padding: 8px 12px
---

## Overview

ShopFlow is a commerce and inventory operations product. Its interface should feel dependable, efficient, and easy to scan. Indigo provides structure and authority; Emerald is a precise signal for availability, success, and forward movement.

The visual system is intentionally quiet. It should support repeated operational work rather than resemble a marketing landing page. Prefer clear hierarchy, compact controls, stable layouts, and direct language.

## Design Principles

1. **Operations first.** Optimize screens for scanning, comparison, and repeated action.
2. **Indigo structures; Emerald signals.** Indigo owns navigation and primary actions. Emerald identifies positive state and limited highlights.
3. **Information before decoration.** Every visual element must improve hierarchy, state recognition, or task completion.
4. **Stable by default.** Loading, error, and dynamic content must not cause controls or fixed-format layouts to jump.
5. **One obvious next action.** Each view should have a clear primary command without competing accent treatments.

## Logo System

The logo geometry and colors come from the approved source at `/final.png`. Project-ready exports live in `public/brand`.

| Asset | Dimensions | Preferred use |
| --- | ---: | --- |
| `shopflow-logo.png` | 1019 x 222 | Wide brand placements and documentation covers |
| `shopflow-wordmark.png` | 700 x 157 | Application header and horizontal navigation |
| `shopflow-mark.png` | 248 x 222 | Square placements, app tiles, and compact identity |
| `/favicon.png` | 64 x 64 | Browser favicon |

### Logo Rules

- Preserve the supplied aspect ratio. Never stretch, rotate, skew, outline, recolor, or add shadows.
- Keep clear space of at least one quarter of the rendered mark height on every side.
- Render the wordmark at 120 px wide or larger. Render the mark at 24 px or larger.
- Use these transparent exports only on light, quiet backgrounds.
- Do not invert the PNG files in CSS. A dark-background logo must come from approved source artwork.
- Use `alt="ShopFlow"` when the logo is the only visible brand label. Use `alt=""` when nearby text already provides the name.
- Do not recreate the mark with CSS, SVG tracing, or generative tools from the raster reference.

## Colors

The palette combines a dark structural color, a high-energy brand accent, and neutral operational surfaces.

- **Primary (`#1E2A5A`):** Primary buttons, selected navigation, headings, and focus rings.
- **Primary hover (`#172044`):** Hover and pressed treatment for Indigo controls.
- **Secondary (`#5E667D`):** Supporting copy, metadata, and inactive navigation.
- **Tertiary (`#00C391`):** Brand accents, success fills, and prominent positive indicators.
- **Tertiary dark (`#007A5C`):** Accessible positive-status text on light backgrounds.
- **Neutral (`#FAFAF8`):** Application canvas.
- **Surface (`#FFFFFF`):** Header, dialogs, cards, menus, and inputs.
- **Foreground (`#18213F`):** Default body text.
- **Border (`#DDE1E8`):** Dividers, control outlines, and card boundaries.
- **Destructive (`#C73E3E`):** Errors, destructive actions, and unavailable inventory.

Emerald `#00C391` does not provide enough contrast for small text on white. Use `#007A5C` for green text and reserve the brighter Emerald for fills, marks, and larger decorative accents. Never use color as the only carrier of state.

## Typography

The source artwork does not identify or license a brand font. The frontend therefore uses the existing sans-serif stack rather than guessing from the raster logo. If an approved font is supplied later, update both this file and `src/assets/main.css` in the same change.

- **Display:** 3rem / 700 for a genuine hero or major empty state only.
- **H1:** 2.25rem / 700 for page titles.
- **H2:** 1.5rem / 600 for primary sections.
- **H3:** 1.125rem / 600 for cards, dialogs, and compact panels.
- **Body:** 1rem / 1.6 for standard content.
- **Body small:** 0.875rem / 1.5 for metadata and supporting copy.
- **Label:** 0.75rem / 700 for short statuses and field labels.

Use sentence case for headings, controls, and navigation. Letter spacing remains `0` at every level. Use tabular numerals for prices, quantities, inventory, and totals. Do not scale type with viewport width.

## Spacing And Layout

Spacing uses a 4 px base and an 8 px working rhythm. Use the named values before introducing intermediate spacing.

- `4px`: icon-to-label details and tightly related inline content.
- `8px`: control internals, compact stacks, and navigation gaps.
- `16px`: standard field and component gaps.
- `24px`: card padding and section internals.
- `32px`: major content groups.
- `48px`: page-level separation.

The application shell is capped at `1280px`. On large screens it uses a `256px` sidebar and a flexible content region. On small screens, navigation moves above content and page padding reduces without shrinking controls below their target size.

Do not put cards inside cards or turn full page sections into floating cards. Use cards only for repeated records, framed tools, and dialogs. Fixed-format elements need stable grid tracks, aspect ratios, or minimum dimensions so state changes cannot resize the surrounding layout.

## Components

### Icons

- Use `@tabler/icons-vue` exclusively for interface icons.
- Use outline icons at 20 px with a `1.8` stroke by default; use 16 px inside compact labels and 24 px for empty states or product placeholders.
- Keep decorative icons hidden from assistive technology. Icon-only controls require an accessible name and tooltip.
- Do not mix Tabler icons with hand-drawn SVG, emoji, or a second icon library.
- Icons support labels and status text; they do not replace them when meaning would become ambiguous.

### Buttons

- Use one Indigo primary button for the main command in a region.
- Use bordered secondary buttons for alternate commands.
- Use icon-only buttons for familiar actions when an icon exists; include a tooltip and accessible name.
- Keep controls at least 40 px high. Disabled buttons retain their dimensions and communicate state beyond opacity when context requires it.
- Never use Emerald for a competing second primary action.

### Cards

- Use an 8 px radius, 1 px border, and subtle shadow.
- Product cards expose name, price, and stock status without interaction.
- Keep card headings compact; card type must not compete with the page heading.
- Hover may strengthen the shadow but must not move or resize the card.

### Status

- `In stock`: pale Emerald background, dark Emerald text, literal label.
- `Out of stock`: pale red background, destructive text, literal label.
- Status labels use a 4 px radius rather than pill styling.
- Success and failure messages must include text or an icon in addition to color.

### Dialogs

- Use dialogs for focused inspection or a short decision, not multi-step workflows.
- Keep the default dialog at or below 448 px unless its content demonstrably requires more space.
- Provide an explicit close control and preserve Escape and outside-click behavior.
- Keep title, description, state, and commands in a predictable reading order.

### Forms

- Labels remain visible; placeholders do not replace labels.
- Inputs are at least 40 px high with Indigo focus treatment.
- Place validation feedback next to the affected field and summarize submission errors when several fields fail.
- Use native input types and browser behavior before adding custom controls.

### Navigation

- The current destination uses Indigo text on a white surface with a border or equivalent non-color cue.
- Keep labels short and role-oriented.
- Mobile navigation must not overlay or obscure page content.

## Data And Async States

Every data-driven screen must implement four explicit states:

1. **Loading:** Reserve the final layout dimensions with skeletons or stable placeholders.
2. **Empty:** State that no records are available and offer an action only when one is useful.
3. **Error:** Name what failed in user terms and provide retry when the operation is recoverable.
4. **Loaded:** Present data in a scan-friendly order with status visible before interaction.

Do not show raw HTTP errors, stack traces, database terms, or internal identifiers to users. Retrying must reuse the existing query flow rather than reloading the entire application.

## Responsive Behavior

- **Below 640 px:** Single-column content, reduced page padding, full-width primary actions where useful.
- **640-1023 px:** Two-column catalog layouts when card content still fits.
- **1024 px and above:** Persistent sidebar and denser multi-column layouts.
- **1280 px and above:** Keep the shell capped; add breathing room rather than scaling type.

Long names and prices must wrap or truncate intentionally without covering status or actions. Test the longest expected label at mobile and desktop widths.

## Motion

Motion confirms state changes; it is not decoration.

- Use 150 ms for hover and focus transitions.
- Use 200 ms for dialogs, menus, and content state changes.
- Prefer opacity and color changes over movement.
- Respect `prefers-reduced-motion` and disable non-essential animation.
- Do not animate layout dimensions for loading content.

## Accessibility

- Target WCAG AA contrast for text and interactive controls.
- Keep a visible 2 px Indigo focus ring with sufficient offset from the component edge.
- Interactive controls require accessible names and practical 40 px minimum targets.
- Status must never rely on color alone.
- Dialog focus must be trapped and returned to the trigger on close.
- Decorative logo instances use empty alt text; meaningful instances use `ShopFlow`.
- Maintain logical heading order and DOM order across responsive layouts.

## Content Style

- Use direct, concise, action-oriented language.
- Prefer `Try again` over technical recovery instructions.
- Use consistent product vocabulary: `product`, `price`, `in stock`, and `out of stock`.
- Avoid marketing superlatives inside operational workflows.
- Do not place visible instructional copy that explains obvious controls or visual styling.

## Do's And Don'ts

- **Do** use Indigo to establish hierarchy and interaction.
- **Do** reserve Emerald for positive state and limited brand emphasis.
- **Do** keep repeated data compact, aligned, and easy to compare.
- **Do** preserve stable dimensions across loading and loaded states.
- **Do** use the supplied logo exports without modification.
- **Don't** introduce gradients, decorative orbs, or atmospheric blobs.
- **Don't** create a one-hue Indigo interface; neutral surfaces and Emerald status provide balance.
- **Don't** use oversized hero typography inside dashboards, cards, or dialogs.
- **Don't** use pill-shaped containers for ordinary labels or navigation.
- **Don't** nest cards or float entire page sections inside decorative containers.
- **Don't** add new colors, fonts, shadows, or radii without updating the token contract above.

## Implementation Checklist

Before accepting a frontend change, verify:

- The screen uses the documented tokens or existing Tailwind aliases.
- Primary and secondary actions are visually unambiguous.
- Loading, empty, error, and loaded states are covered where applicable.
- Text and controls fit at mobile and desktop widths without overlap.
- Keyboard focus, accessible names, contrast, and status labels are present.
- Logo usage follows the minimum size, clear-space, and background rules.
- `npm run lint:ci` and `npm run build` pass.

## Source Of Truth

`/final.png` is the visual source for logo geometry and the two core brand colors. This document is the implementation contract for frontend usage. Approved source artwork must precede any new logo variant; do not derive new marks from the raster sheet.
