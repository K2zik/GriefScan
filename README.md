# GriefScan

**Anti-grief scanner for suspicious player actions — one JAR for Paper/Spigot 1.19–1.21+**

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

**Supported servers:** Paper / Folia / Spigot — Minecraft **1.19.x, 1.20.x, 1.21.x**  
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
  website: false

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

## Build

```bash
./gradlew build
```

Output: `build/libs/GriefScan.jar`

---

## License

MIT — see [LICENSE](LICENSE)
