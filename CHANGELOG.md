# Changelog

## 1.0.2 - 2026-10-28

### Fixed

- Permanent badge grants are no longer read from storage as expired at the Unix epoch.
- Badge texture files, including 256×256 PNGs, remain independent from ownership expiration.

## 1.0.1 - 2026-10-10

- Added Minecraft 26.3 compatibility, including the updated checked-exception contract in Paper's command API, and verified the build and tests.

## 1.0.0

### Added

- Player badge collections with ordered equipment slots.
- Stable custom font glyph and resource pack generation.
- SQLite, MySQL, and PostgreSQL storage backends.
- Collection GUI, player and administrator commands, PlaceholderAPI, Java API, and events.
- Optional REST API and multi-server polling.
- English and Russian GUI text, Paper/Purpur/Folia scheduling, config migration, bStats, and update checks.
