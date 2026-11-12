# Generated timer controls

Created with the built-in image generation tool on 2026-09-20. All three original PNGs retain their generated transparent backgrounds, allowing the app’s existing washi paper to show through. Start and Resume share the play icon. No downloaded icon set is used.

## Final prompts and saved assets

### start

Asset: `app/src/main/res/drawable-nodpi/ink_start.png`

Use case: stylized-concept. Asset type: production Android meditation timer control icon, single square image. Style: beautifully hand-drawn Japanese sumi ink on washi, with natural dry brush fibers, softly uneven edges, restrained wabi-sabi craftsmanship. Near-black warm charcoal ink only. Transparent background so the app's own Japanese paper shows through; actual alpha, no white or beige rectangle, no checkerboard painted into the image. Centered mark occupying about 65% of square with generous clear margins. Bold simple silhouette recognizable at 36dp, calm minimal composition. No text, no border, no shadows, no extra decoration. Subject: a right-pointing filled play triangle painted with a Japanese calligraphy brush; elegant slightly irregular triangular silhouette, ink washes and visible delicate paper-like grain within the ink.

### pause

Asset: `app/src/main/res/drawable-nodpi/ink_pause.png`

Use case: stylized-concept. Asset type: production Android meditation timer control icon, single square image. Style: beautifully hand-drawn Japanese sumi ink on washi, with natural dry brush fibers, softly uneven edges, restrained wabi-sabi craftsmanship. Near-black warm charcoal ink only. Transparent background so the app's own Japanese paper shows through; actual alpha, no white or beige rectangle, no checkerboard painted into the image. Centered mark occupying about 65% of square with generous clear margins. Bold simple silhouette recognizable at 36dp, calm minimal composition. No text, no border, no shadows, no extra decoration. Subject: two parallel upright charcoal brushstrokes forming the universal pause symbol; equal height, clear space between them, gently tapered dry-brush ends.

### stop

Asset: `app/src/main/res/drawable-nodpi/ink_stop.png`

Use case: stylized-concept. Asset type: production Android meditation timer control icon, single square image. Style: beautifully hand-drawn Japanese sumi ink on washi, with natural dry brush fibers, softly uneven edges, restrained wabi-sabi craftsmanship. Near-black warm charcoal ink only. Transparent background so the app's own Japanese paper shows through; actual alpha, no white or beige rectangle, no checkerboard painted into the image. Centered mark occupying about 65% of square with generous clear margins. Bold simple silhouette recognizable at 36dp, calm minimal composition. No text, no border, no shadows, no extra decoration. Subject: one filled compact square forming the universal stop symbol; subtly uneven hand-brushed sides and ink wash texture, recognizable square silhouette.

## Settings and reset (2026-11-08)

Generated with the built-in `image_gen` tool, using `ink_pause.png` as a style reference only.
The original generated RGBA PNGs were copied unchanged, retaining their transparent backgrounds.

### settings

Asset: `app/src/main/res/drawable-nodpi/ink_settings.png`

Use case: stylized-concept. Asset type: single square production Android meditation app control icon. Reference image is STYLE REFERENCE ONLY: match its warm near-black charcoal sumi ink, dry-brush fibers, uneven edges and restrained hand-painted texture. Create a SETTINGS icon: three short horizontal hand-painted brush strokes, with one small solid ink knob on each, at alternating positions, recognizable as sliders. Use bold enough strokes to read at 28dp. A single centered compact silhouette occupying 65 percent of square, generous clear margins. True transparent background with alpha. No paper rectangle, no checkerboard, no text, no border, no shadow, no decorative splashes. This is one settings icon only.

### reset

Asset: `app/src/main/res/drawable-nodpi/ink_reset.png`

Use case: stylized-concept. Asset type: single square production Android meditation app control icon. Reference image is STYLE REFERENCE ONLY: match its warm near-black charcoal sumi ink, dry-brush fibers, uneven edges and restrained hand-painted texture. Create a RESET icon: a single counterclockwise nearly circular brush-painted arrow, with a clear small triangular arrowhead at its upper-left end pointing down-left, familiar restart/reset symbol. Clearly visible gap between arrowhead and tail; bold simple silhouette recognizable at 32dp, natural irregular calligraphic brush width, unfilled transparent center. Center mark occupying 65 percent of square, generous clear margins. True transparent background with alpha. No paper rectangle, no checkerboard, no text, no border, no shadow, no extra ornament. One reset icon only.

## Infinity (2026-11-08)

Asset: `app/src/main/res/drawable-nodpi/ink_infinity.png`

Generated with the built-in `image_gen` tool using `ink_reset.png` as a style reference only. The original RGBA image is copied unchanged, preserving transparency. Displayed at 48 × 32 dp for a larger, quieter replacement of the font glyph.

Use case: stylized-concept. Asset type: single production Android meditation timer icon. Reference image is STYLE REFERENCE ONLY: match its warm near-black charcoal sumi ink, dry brush fibers, softly uneven edges, subtle ink texture and restrained Japanese calligraphy. Subject: a single horizontally elongated infinity symbol, two graceful clearly open loops meeting at the center, painted in one flowing calligraphy brush gesture. Bold simple familiar infinity silhouette legible at 40dp wide. Composition: centered, symbol occupies about 82 percent of image width and 40 percent of image height; generous transparent margins, balanced loops with subtle natural asymmetry. True transparent background with alpha, including both loop interiors. No paper rectangle, no checkerboard, no shadows, no border, no letters, no other symbols or decorations. This is one standalone infinity icon only.

## Painter controls (2026-11-11)

Close, brush-swash, apply/checkmark and trash/restore-standard controls are code-native calligraphic icons in
`InkPainterIcon.kt`. The existing SumiBrush deposits textured strokes into a cached transparent
128px image for each symbol; these are not generated image assets or an external icon font.
Reset continues to use `ink_reset.png`. Spoken action labels and 48dp touch targets are retained.

Brush settings uses the supplied `tmp/brush_settings_sliders.svg`, converted losslessly to
`app/src/main/res/drawable/ink_brush_settings.xml`, retaining its original paths and even-odd fill.
The PNG reference is superseded by this SVG.

The native splash references `drawable-nodpi/native_splash_enso.png` directly. The original
transparent `enso_splash.png` is trimmed, fitted to 288px and centered on a transparent 432px canvas
with ImageMagick, preserving alpha and fitting Android's safe icon region. There is no XML icon
wrapper, in-app splash or artificial delay. Android 13+ uses icon_preferred for warm launches too.

The supplied brush-settings vector keeps its silhouette but is rasterized once at 256px with the
same seeded ink-grain function used by SumiBrush. Warm charcoal #292823, mottled opacity and dry
fibers replace the original cool flat fill, matching the other painter controls. Its 48dp touch
target and 38dp displayed image size remain consistent with the other buttons.
The native splash already fills the Android-safe 192dp motif circle in a 288dp transparent icon
canvas (equivalently 288px of ink on the 432px source PNG); keep that maximum uncropped size.

## Quiet transitions and shared brush (2026-11-12)

The brush settings button now combines the code-native main-view `BrushMark` silhouette with
`ink_settings.png`, superseding the textured vector above. The drawn-today variant combines that
same brush with a miniature `enso_brush.png`; no new generated assets are needed.
The native splash now hands off to a Compose layer that moves and crossfades into the timer
artwork before controls appear, superseding the earlier native-only transition description.

## Launcher icon (2026-11-12)

The launcher now reuses `native_splash_enso.png`, preserving the splash mark's ink texture and
orientation. `enso_icon.xml` fits its transparent foreground into the adaptive icon's 66dp safe
region; both the regular and themed/monochrome adaptive icons use this silhouette over the warm
paper background. Android 7 uses the same PNG in a paper-backed layer list. No new artwork was
generated, and the splash asset itself is unchanged.
