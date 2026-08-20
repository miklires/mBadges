# Resource pack guide

## Internal generator

1. Create one PNG per badge in `plugins/mBadges/badges`. Files must be readable PNG images, 1–256 pixels on each side, and must not be placed in subdirectories.
2. Register every file in `badges.yml`.
3. Run `/mbadge resourcepack rebuild` from the game or console.
4. Confirm that `generated/mbadges-resource-pack.zip` contains `pack.mcmeta`, `assets/minecraft/font/default.json`, and the configured textures.
5. Upload the ZIP to a direct HTTP(S) URL. A normal HTML download page is not a direct URL.
6. Copy `sha1` from `generated/resource-pack.yml` to `delivery.sha1`, set `delivery.url`, and enable `delivery.send-on-join`.
7. Rejoin with a test account and confirm that the prompt, download, and glyph rendering all work.

`auto-build: true` rebuilds only when badge IDs, assigned glyphs, relevant font settings, or PNG content changes. The `rebuild` command bypasses that fingerprint check.

## Stable glyphs

Assignments live in `generated/glyphs.yml`. Back up and deploy this file with `badges.yml` across every server. Never delete it during an update unless changing all code points is intentional. A removed badge keeps its old assignment reserved, preventing another badge from silently taking its character.

The default range is `E000`–`F8FF`, Minecraft's private-use area. Change the range only before creating the first production pack.

## Existing server resource pack

Minecraft accepts one server pack, so merge mBadges output into the pack already used by the server. Copy the generated bitmap-provider objects into the existing `assets/minecraft/font/default.json` and copy `assets/mbadges/textures/font` into the combined pack. Recalculate the final ZIP SHA-1 after merging.

Set mode to `EXTERNAL` so mBadges does not overwrite the combined pack:

```yaml
mode: EXTERNAL
auto-build: false
```

The glyph map remains authoritative.

## Oraxen and ItemsAdder

`ORAXEN` and `ITEMSADDER` modes disable internal generation and report when the selected plugin is missing. They intentionally do not rewrite third-party configuration. Configure a font image in the other plugin for every character listed in `generated/glyphs.yml`, then build and distribute that plugin's pack normally.

## Troubleshooting

- `has no PNG file`: check `texture` and ensure the file is directly inside the source directory.
- `invalid PNG size`: export a valid PNG between 1×1 and 256×256.
- Empty square in game: the client did not load the correct pack or the bitmap provider uses a different code point.
- Pack downloads repeatedly: host a stable file and configure the exact SHA-1 of the bytes at that URL.
- Pack is rejected after editing: zip the pack contents, not an extra parent directory.
