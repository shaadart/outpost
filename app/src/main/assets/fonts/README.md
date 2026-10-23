# Bundled typefaces

Geist Pixel Square is used for headings. Bytesized is reserved for the Outpost wordmark. Dongle Regular and Bold are used for body text and controls.

Fonts are bundled locally; the app does not contact a font provider to render text. Dongle uses the official Google Fonts static subset covering Latin, Latin Extended A, punctuation, and currency symbols. Android supplies fallback glyphs for other scripts and emoji. All font assets together are under 200 KB.

## Sources

* Geist Pixel Square: https://github.com/vercel/geist-font
* Bytesized: https://github.com/google/fonts/tree/main/ofl/bytesized
* Dongle: https://github.com/google/fonts/tree/main/ofl/dongle

The matching SIL Open Font License files are bundled in this directory. Exact asset URLs, upstream revisions where applicable, and SHA256 checksums are recorded in `sources.json`. Dongle point sizes are adjusted for its small x-height; heading and wordmark fonts are not synthetically emboldened.
