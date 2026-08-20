<div align="center">
  <h1>mBadges</h1>
  <p>Graphical player badges with collections, ordered equipment slots, and a generated resource pack.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="docs/assets/folia-available.png"></a>
  </p>

  <p>
    <a href="https://github.com/miklires/mBadges"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
  </p>

  <p>
    <a href="https://bstats.org/plugin/bukkit/mBadges/33353"><img alt="bStats" src="https://img.shields.io/badge/bStats-33353-2F9BE6?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## What it does

- Stores permanent and expiring badges for online or offline players.
- Lets players browse, equip, remove, filter, and reorder badges in a GUI.
- Generates a deterministic font resource pack from PNG files and preserves assigned glyphs across restarts.
- Displays equipped badges in the player list and, optionally, scoreboard nametags.
- Supports SQLite, MySQL, and PostgreSQL without running database work on the server thread.
- Provides PlaceholderAPI placeholders, a Java API with events, an authenticated REST API, and database polling for server networks.
- Includes English and Russian messages, automatic config-default migration, bStats metrics, and update checks.

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2
- A way to host the generated resource-pack ZIP when automatic delivery is enabled

PlaceholderAPI, LuckPerms, Oraxen, and ItemsAdder are optional. mBadges also works when none of them are installed.

## Install

1. Download `mBadges-1.0.0.jar` and place it in the server's `plugins` directory.
2. Start and stop the server once to create `plugins/mBadges`.
3. Add badge PNG files and definitions as described below.
4. Start the server and run `/mbadge resourcepack rebuild`.
5. Host the generated ZIP, then configure resource-pack delivery if players should receive it automatically.

Do not install the API JAR as a server plugin. It is only a compile-time artifact for developers.

## Add a badge

Put a PNG no larger than 256×256 directly in `plugins/mBadges/badges`, then add its definition to `badges.yml`:

```yaml
badges:
  developer:
    name: "<aqua>Developer"
    description: "Server development team"
    texture: developer.png
    permission: mbadge.badge.developer
    category: staff
    priority: 100
    hidden: false
    enabled: true
    temporary: true
```

Badge IDs use lowercase letters, numbers, `_`, or `-`. Removing or reordering definitions does not reassign existing code points; the stable map is kept in `generated/glyphs.yml`.

## Resource pack

The default `INTERNAL` mode writes `generated/mbadges-resource-pack.zip` and `generated/resource-pack.yml`. Upload the ZIP to a direct HTTP(S) URL and copy the generated SHA-1 into `resourcepack.yml`:

```yaml
delivery:
  send-on-join: true
  url: "https://cdn.example.com/mbadges-resource-pack.zip"
  required: true
  sha1: "the-40-character-sha1-from-generated-resource-pack-yml"
```

Use `EXTERNAL`, `ORAXEN`, or `ITEMSADDER` mode when another pack pipeline owns font files. In those modes mBadges keeps the glyph map but does not edit third-party configuration or generate its own pack; add the matching bitmap providers to that pipeline yourself. See [Resource pack guide](docs/RESOURCE_PACK.md).

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | language, slots, display, expiry, sync, REST, metrics, and updates |
| `badges.yml` | badge definitions, permissions, categories, and priorities |
| `database.yml` | SQLite, MySQL, or PostgreSQL connection |
| `resourcepack.yml` | pack generation, external mode, and delivery |
| `gui.yml` | inventory size, materials, and navigation slots |
| `lang/en_US.yml` | English messages |
| `lang/ru_RU.yml` | Russian messages |

Missing settings are added on startup. `/mbadge reload` reloads messages, GUI settings, safe main settings, badge definitions, and the internal pack. Database type and connection settings require a full restart.

Slot limits start at `equipment.default-slots`. Grant `mbadge.max.2`, `mbadge.max.3`, and so on to increase an individual player's limit up to `equipment.max-badges-hard-limit`.

## Commands

| Command | Description |
|---|---|
| `/badge` or `/badge gui` | Open the collection GUI |
| `/badge list` | List owned badges |
| `/badge set <id>` | Equip a badge |
| `/badge remove <id>` | Unequip a badge |
| `/badge clear` | Unequip every badge |
| `/badge info <id>` | Show badge details |
| `/mbadge give <player\|uuid> <id> [--duration 1w2d]` | Give a badge |
| `/mbadge take <player\|uuid> <id>` | Remove a badge |
| `/mbadge equip <player\|uuid> <id>` | Force-equip a badge |
| `/mbadge unequip <player\|uuid> <id>` | Unequip a badge |
| `/mbadge clear <player\|uuid>` | Clear equipped badges |
| `/mbadge list <player\|uuid>` | Show collection counts |
| `/mbadge reload` | Reload safe settings and definitions |
| `/mbadge resourcepack rebuild` | Force a pack rebuild |

Names work for players previously seen by the server. UUIDs work without requiring the player to have joined.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `mbadge.use` | everyone | Player commands |
| `mbadge.gui` | everyone | Collection GUI |
| `mbadge.badge.<id>` | unset | Use a configured badge |
| `mbadge.max.<number>` | unset | Increase equipped slots |
| `mbadge.admin` | operators | Every administrative permission |
| `mbadge.admin.give` | operators | Give badges |
| `mbadge.admin.take` | operators | Remove badges |
| `mbadge.admin.equip` | operators | Equip or unequip for players |
| `mbadge.admin.clear` | operators | Clear equipped badges |
| `mbadge.admin.list` | operators | Read collection counts |
| `mbadge.admin.reload` | operators | Reload configuration |
| `mbadge.admin.resourcepack` | operators | Rebuild the pack |

## PlaceholderAPI

- `%mbadge_equipped%` — rendered equipped glyphs
- `%mbadge_count%` — equipped count
- `%mbadge_owned_count%` — owned count
- `%mbadge_slots%` — available slots
- `%mbadge_slot_1%`, `%mbadge_slot_2%`, ... — glyph in a specific slot

Empty slots return an empty string. Placeholders use cached data and do not query the database on the server thread.

## Storage and networks

SQLite is the zero-configuration default. For a network, point every server at the same MySQL or PostgreSQL database and enable `sync.enabled`. Polling refreshes cached online players; it does not require Redis or a proxy plugin. Each server must use the same `badges.yml` and glyph map.

The optional REST service binds to localhost by default, requires a token of at least 24 characters, checks an IP allowlist, and rate-limits callers. Keep it behind a firewall or reverse proxy. See [API and REST guide](docs/API.md).

## Compatibility notes

- The player-list display is enabled by default.
- Scoreboard nametags are disabled by default because scoreboard/team plugins may own the same presentation layer.
- Chat formatting is exposed through PlaceholderAPI and the Java renderer; mBadges does not replace a chat plugin.
- Velocity is not required or supported because badges render on the backend server and client resource pack.

## Telemetry and updates

mBadges uses [bStats plugin ID 33353](https://bstats.org/plugin/bukkit/mBadges/33353) for anonymous usage statistics. Disable collection with `metrics.enabled: false`. The update checker can be disabled independently with `updates.enabled: false`; a Modrinth link will be added only after the project exists.

## Build

```bash
./gradlew clean build
```

Artifacts:

- `build/libs/mBadges-1.0.0.jar`
- `api/build/libs/mBadges-API-1.0.0.jar`

The project is licensed under the MIT License.
