# GriefScan

**Anti-grief scanner for suspicious player actions — one JAR for Paper/Spigot/Folia 1.19–26.1**

Detects lava/TNT grief, arson, chest breaking, villager kills, and multi-location theft. Alerts staff via console, Discord, and optional website webhooks.

---

## Features

- **Configurable filters** — place/break blocks, place/kill entities, with limits and time windows
- **Anti-theft** — detects suspicious valuables taken from many distant containers
- **Playtime exemption** — skip trusted veterans (`playtime: 30h`)
- **Auto-ban** — optional ban after repeated violations
- **Localization** — English and Russian (`messages.yml`, `messages_ru.yml`)
- **Shulker ownership** — own shulkers are ignored by anti-theft

---

## Installation

1. Download `GriefScan.jar`.
2. Put it in `plugins/`.
3. Start the server once to generate config and message files.
4. Edit `plugins/GriefScan/config.yml` (Discord webhook, filters, etc.).
5. Run `/griefscan reload`.

**Supported servers:** Paper / Folia / Spigot — Minecraft **1.19.x, 1.20.x, 1.21.x, 26.1.x**  
**Java:** 17+ (use the JVM required by your server)

### Reliability
- SQLite persistence for violations (`violations.db`)
- Alert cooldown / anti-spam
- `griefscan.bypass` + exempt player/UUID lists
- CoreProtect / LiteBans quick commands in Discord embeds
- Folia-safe scheduler
- Adventure clickable teleport coords for admins
- `/griefscan inspect <player>` session summary

---

## Commands

| Command | Description |
|---------|-------------|
| `/griefscan reload` | Reload config and messages |
| `/griefscan status` | Active filters and log stats |
| `/griefscan toggle <filter>` | Enable/disable a filter |
| `/griefscan logs report` | Write violations report |
| `/griefscan logs clear [player]` | Clear all or one player's stats |
| `/griefscan logs stats [player]` | Overall or player violation stats |
| `/griefscan antitheft status` | Anti-theft status |
| `/griefscan antitheft clear <player>` | Clear player anti-theft history |
| `/griefscan antitheft clearall` | Clear all anti-theft history |
| `/griefscan language <code>` | Change language |
| `/griefscan inspect <player>` | Session violation summary |

Aliases: `/gs`, `/grief`  
Permission: `griefscan.admin` (default: op)

---

## Configuration

File: `plugins/GriefScan/config.yml`

```yaml
language: en   # en, ru

notify:
  console: true
  discord: true
  webhook: 'https://discord.com/api/webhooks/...'
  website: true
  website_url: 'https://example.com/api/griefscan'
  messageWebsite: 'Alert: Player %player% triggered %filter% filter with %count% actions at %x% %y% %z%'

filters:
  LAVA_PLACEMENT:
    enabled: true
    limit: 5
    time_window: 30
    blocks: [LAVA]
    action: notify
    worlds: [world]

anti_theft:
  enabled: true
  min_containers: 3
  min_locations: 3
  min_distance: 20
  time_window: 300

auto_ban:
  enabled: false
  violations_threshold: 3

playtime: 30h
```

After editing, run `/griefscan reload`.

---

## Website API

GriefScan can POST every filter alert to your site. Enable it in `config.yml`, point `notify.website_url` at an HTTPS endpoint, then `/griefscan reload`.

```yaml
notify:
  website: true
  website_url: 'https://your-site.example/api/griefscan'
  messageWebsite: 'Alert: Player %player% triggered %filter% filter with %count% actions at %x% %y% %z%'
```

The same alert cooldown as Discord/console applies (`alerts.cooldown_seconds`).

### Request

| | |
|---|---|
| Method | `POST` |
| URL | `notify.website_url` |
| `Content-Type` | `application/json` |
| `User-Agent` | `GriefScan-Plugin` |
| Success | HTTP `2xx` |

Body:

```json
{
  "content": "Alert: Player Steve triggered LAVA_PLACEMENT filter with 5 actions at 120 64 -30",
  "player": "Steve",
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "filter": "LAVA_PLACEMENT",
  "count": 5,
  "world": "world",
  "x": 120,
  "y": 64,
  "z": -30,
  "timestamp": 1725148800000
}
```

| Field | Type | Description |
|-------|------|-------------|
| `content` | string | `notify.messageWebsite` after placeholder replacement |
| `player` | string | Player name |
| `uuid` | string | Player UUID |
| `filter` | string | Filter key (`LAVA_PLACEMENT`, …) |
| `count` | number | Actions in the time window |
| `world` | string | World name |
| `x` `y` `z` | number | Block coordinates |
| `timestamp` | number | Unix time in **milliseconds** |

Connect/read timeout: **5 seconds**. Non-`2xx` responses are logged as `Failed to send website notification`.

### Placeholders in `messageWebsite`

| Placeholder | Value |
|-------------|--------|
| `%player%` | Player name |
| `%uuid%` | Player UUID |
| `%filter%` | Filter key (`LAVA_PLACEMENT`, …) |
| `%count%` | Actions in the time window |
| `%world%` | World name |
| `%x%` `%y%` `%z%` | Block coordinates |
| `%tp%` | Teleport command from `integrations.teleport_command` |
| `%coreprotect%` | CoreProtect lookup command |
| `%litebans%` | LiteBans history command |
| `%kick%` | Kick command |
| `%ban%` | Ban command |

Default template if `messageWebsite` is omitted:

```text
Alert: %player% triggered %filter% (%count%) at %x% %y% %z%
```

### Example receiver (Node)

```js
import express from 'express';

const app = express();
app.use(express.json());

app.post('/api/griefscan', (req, res) => {
  const { content, player, filter, count, world, x, y, z, timestamp } = req.body ?? {};
  if (typeof content !== 'string' || typeof timestamp !== 'number') {
    return res.status(400).json({ error: 'invalid payload' });
  }
  console.log(new Date(timestamp).toISOString(), player, filter, count, world, x, y, z, content);
  res.status(204).end();
});

app.listen(3000);
```

---

## Build

```bash
./gradlew build
```

Output: `build/libs/GriefScan.jar`

---

## License

MIT — see [LICENSE](LICENSE)
