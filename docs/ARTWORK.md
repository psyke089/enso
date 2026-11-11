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

Close, brush, apply/checkmark and restore-standard controls are code-native calligraphic icons in
`InkPainterIcon.kt`. The existing SumiBrush deposits textured strokes into a cached transparent
128px image for each symbol; these are not generated image assets or an external icon font.
Reset continues to use `ink_reset.png`. Spoken action labels and 48dp touch targets are retained.
