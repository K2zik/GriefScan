# Modrinth listing copy

Paste these into the Modrinth project form.

---

## Summary (short)

Anti-grief scanner for Paper/Folia/Spigot 1.19–1.21+. Detects lava/TNT grief, arson, theft across locations. SQLite logs, Discord quick-actions, EN+9 languages.

---

## Description (full)

**GriefScan** watches for suspicious player actions on survival and SMP servers and alerts staff before the damage gets out of hand.

### What it detects
- Lava and TNT placement spikes
- Arson (fire placement)
- Rapid chest/barrel breaking
- Item frame and villager kills
- Multi-location theft of valuables (diamonds, netherite, etc.)

### Highlights
- One JAR for **Paper/Spigot 1.19.x – 1.21.x**
- Custom filters with limits, time windows, worlds, and height ranges
- Discord + console (+ optional website) notifications
- Optional auto-ban after repeated violations
- Playtime exemption for trusted players
- Localization: **English** and **Russian**
- Shulker ownership awareness for anti-theft

### Commands
`/griefscan` (aliases: `/gs`, `/grief`)  
`reload` · `status` · `toggle` · `logs` · `antitheft` · `language`

Permission: `griefscan.admin`

### Setup
1. Drop `GriefScan.jar` into `plugins/`
2. Start the server
3. Set your Discord webhook in `config.yml`
4. `/griefscan reload`

### Requirements
- Paper or Spigot **1.19+**
- Java **17+**
