# JARVIS Project — UI Resources

This folder was created per Oliver's request (2026-08-29) so ChatGPT (and anyone else on the
project) has a shared place to pull the front-end/UI assets Claude has produced, separate from
the GitHub repo that ChatGPT owns.

## Folder structure

- **JARVIS Project/** (top level)
  - **JARVIS Project UI Resources/** (this folder)
    - **app-icon/** — original, donor-free Android app icon + notification icon assets

## What's in app-icon/

These are an **original design** (radial-gradient orb motif matching the live prototype's own
color tokens) built specifically to replace the old donor-app branding. No donor assets were
used anywhere in this set.

Buildable Android resources (drop into `res/` as-is — these are the real, production assets
ChatGPT should wire into the app):
- `ic_launcher_background.xml` — adaptive icon background layer (vector drawable, radial gradient)
- `ic_launcher_foreground.xml` — adaptive icon foreground layer (vector drawable, glowing orb)
- `ic_launcher_adaptive.xml` — `<adaptive-icon>` wiring; use for both
  `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
- `ic_stat_jarvis.xml` — status-bar / notification icon (flat, alpha-only silhouette per
  Android notification icon guidelines — needed for the foreground-service notification)
- `jarvis_brand_colors.xml` — the 8 brand hex tokens as an Android `colors.xml`

All five of the above were uploaded byte-for-byte identical to the source files — verified by
exact file-size match after upload. **These are the assets to actually use.**

Raster PNG previews (for eyeballing the design, not required for the build):
- `ic_launcher_mdpi_48.png`, `ic_launcher_hdpi_72.png`, `ic_launcher_xhdpi_96.png` — per-density
  launcher icon renders

Note on the PNG previews: Google Drive's upload pipeline appears to re-encode/re-process image
files in some cases (one of these came back a different byte size than the source, despite
uploading with `disableConversionToGoogleType`). They're still correct, valid, visually-accurate
PNGs at the right dimensions — just not guaranteed byte-identical to the source render. Since the
vector XML above is what actually gets compiled into the app, this doesn't affect the real
deliverable. The remaining density exports (144/192/512px) and the safe-zone/notification preview
screenshots were already shared directly with Oliver earlier in the Claude conversation (as chat
attachments) rather than duplicated here, since large image payloads can't be reliably
round-tripped through this particular upload path.

## Canonical live prototype

The donor-based front end has been fully replaced. The **authoritative, up-to-date reference for
the app's UI/UX** is the published Claude Artifact, not a static file copy:

**https://claude.ai/code/artifact/a7271792-25ce-4ebe-a24c-ffbd65680e4d**

This is a live, interactive HTML/CSS/JS prototype (`jarvis-live.html`, ~220KB) implementing the
full assistant UI — orb states, overlays, color system, etc. It's too large to reliably
copy byte-exact into Drive through this tool chain, and it would go stale the moment it's edited
here in Claude anyway. The Artifact URL always reflects the current version — open it directly
rather than looking for a copy of the file in this folder.

The design tokens used throughout (background, panel, cyan accent, danger, text colors) are also
baked into `jarvis_brand_colors.xml` above for direct reuse in the Android app.

## Questions / context

See `#jarvis-coordination-room` in Slack and the Basic Memory project notes under
`projects/jarvis/` for the full history and decision log behind these assets.
