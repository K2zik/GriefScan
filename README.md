# GriefScan

**Anti-grief scanner for suspicious player actions — one JAR for Paper, Spigot, and Folia (1.19–26.1).**

GriefScan watches the usual grief patterns in real time: lava and TNT dumps, arson, chest breaking, villager kills, and players looting valuables from many distant containers. When something looks wrong, staff get an alert in console, admin chat, Discord, or an optional website webhook.

---

## Features

- **Configurable filters** — place/break blocks and place/kill entities, with limits, time windows, worlds, and optional height ranges
- **Anti-theft** — flags suspicious valuables taken from many distant containers (own shulkers are ignored)
- **Playtime exemption** — skip trusted veterans (`playtime: 30h`, also `90m` / `2d`)
- **Auto-ban** — optional ban after repeated violations
- **Localization** — `en`, `ru`, `es`, `zh`, `hi`, `ar`, `fr`, `de`, `ja`, `pt`
- **SQLite persistence** — violations stored in `violations.db`
- **Alert cooldown / anti-spam** — no Discord flood
- **Bypass** — `griefscan.bypass` plus exempt player/UUID lists
- **Integrations** — CoreProtect and LiteBans quick commands in Discord embeds
- **Website webhook** — POST structured JSON alerts to your panel/site
- **Folia-safe scheduler**
- **Clickable teleport coords** for admins (Adventure)
- **`/griefscan inspect <player>`** — session violation summary

### Built-in filters

| Filter | What it watches |
|--------|-----------------|
| `LAVA_PLACEMENT` | Placing lava |
| `TNT_PLACEMENT` | Placing TNT |
| `ARSON` | Setting fire |
| `CHEST_BREAKING` | Breaking chests / barrels |
| `FRAME_BREAKING` | Breaking item frames |
| `VILLAGER_KILLING` | Killing villagers |

You can add your own filters in `config.yml` (armor stands, custom blocks, extra entities, etc.).

---

## Installation

1. Download `GriefScan.jar`
2. Put it in `plugins/`
3. Start the server once to generate config and message files
4. Edit `plugins/GriefScan/config.yml` (Discord webhook, filters, worlds)
5. Run `/griefscan reload`

**Supported servers:** Paper / Folia / Spigot  
**Minecraft:** 1.19.x, 1.20.x, 1.21.x, 26.1.x  
**Java:** 17+ (use the JVM required by your server)

Soft-depends: [CoreProtect](https://modrinth.com/plugin/coreprotect), LiteBans

---

## Commands

Aliases: `/gs`, `/grief`  
Permission: `griefscan.admin` (default: op)

| Command | Description |
|---------|-------------|
| `/griefscan reload` | Reload config and messages |
| `/griefscan status` | Active filters and log stats |
| `/griefscan toggle <filter>` | Enable or disable a filter |
| `/griefscan logs report` | Write a violations report |
| `/griefscan logs clear [player]` | Clear all or one player's stats |
| `/griefscan logs stats [player]` | Overall or player violation stats |
| `/griefscan antitheft status` | Anti-theft status |
| `/griefscan antitheft clear <player>` | Clear player anti-theft history |
| `/griefscan antitheft clearall` | Clear all anti-theft history |
| `/griefscan language <code>` | Change language |
| `/griefscan inspect <player>` | Session violation summary |

**Permissions**

- `griefscan.admin` — all commands (default: op)
- `griefscan.bypass` — skip filters and anti-theft

---

## Configuration

`plugins/GriefScan/config.yml`

```yaml
language: en   # en, ru, es, zh, hi, ar, fr, de, ja, pt

notify:
  console: true
  discord: true
  webhook: 'https://discord.com/api/webhooks/...'
  website: false
  website_url: 'https://your-site.example/api/griefscan'

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
| Timeout | 5 seconds connect / read |
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
