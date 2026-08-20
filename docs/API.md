# API and REST guide

## Java API

Compile against `mBadges-API-1.0.0.jar`; do not bundle it and do not place it in the server's plugin directory. Add `mBadges` as a soft or hard dependency in your plugin metadata.

Retrieve the registered service after both plugins are enabled:

```java
RegisteredServiceProvider<MBadgesApi> registration =
        Bukkit.getServicesManager().getRegistration(MBadgesApi.class);
if (registration == null) return;

MBadgesApi badges = registration.getProvider();
badges.giveBadge(
        playerId,
        "supporter",
        null,
        getName(),
        "{}",
        BadgeOperationReason.PLUGIN
).thenAccept(result -> getLogger().info(result.status().name()));
```

Collection and mutation methods return `CompletableFuture` because storage is asynchronous. Do not call `join()` or `get()` on the server thread. `getBadge`, `getBadges`, and `render` use in-memory state.

Events:

- `BadgeGiveEvent`
- `BadgeRemoveEvent`
- `BadgeEquipEvent`
- `BadgeUnequipEvent`
- `BadgeExpireEvent`

Badge-operation events are asynchronous. Event listeners must not call thread-confined Bukkit APIs directly; schedule player or world work through the appropriate Paper/Folia scheduler. Equip and unequip events are cancellable.

## REST API

Enable the service only after creating a random token of at least 24 characters:

```yaml
rest-api:
  enabled: true
  bind: 127.0.0.1
  port: 8766
  token: "replace-with-a-long-random-secret"
  allowed-addresses:
    - 127.0.0.1
    - "::1"
  requests-per-minute: 60
```

Every request requires `Authorization: Bearer <token>`.

### Routes

```text
GET    /v1/players/<uuid>/badges
POST   /v1/players/<uuid>/badges/<badge-id>
POST   /v1/players/<uuid>/badges/<badge-id>?duration_seconds=3600
DELETE /v1/players/<uuid>/badges/<badge-id>
```

Example:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8766/v1/players/00000000-0000-0000-0000-000000000001/badges
```

The API accepts UUIDs only. It never logs the configured token. The IP allowlist is checked before authentication, but it is not a substitute for a firewall. Use TLS at a reverse proxy if requests cross a trusted host boundary.

## Multi-server polling

Use one MySQL or PostgreSQL database, identical badge definitions, and the same `generated/glyphs.yml` on all backends:

```yaml
sync:
  enabled: true
  polling-interval-seconds: 10
```

Changes made on one backend invalidate cached data for affected online players on the others. SQLite is intended for one server and should not be shared over a network filesystem.
