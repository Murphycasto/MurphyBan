<div align="center">

# MurphyBan

**A complete, production-ready punishment management plugin for Spigot 1.21**

[![Status](https://img.shields.io/badge/status-stable-brightgreen?style=flat-square)](https://github.com/Murphycasto/MurphyBan/releases)
[![Spigot](https://img.shields.io/badge/spigot-1.21-orange?style=flat-square)](https://www.spigotmc.org)
[![Java](https://img.shields.io/badge/java-21-blue?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)](LICENSE)

[Features](#features) · [Installation](#installation) · [Commands](#commands) · [Permissions](#permissions) · [Configuration](#configuration) · [Messages](#messages) · [PlaceholderAPI](#placeholderapi) · [Alt Detection](#alt-detection) · [Building](#building-from-source)

</div>

---

MurphyBan is a self-contained punishment system built to replace LiteBans, AdvancedBan, and similar plugins with a single, clean solution. It handles bans, IP bans, mutes, kicks, and warnings — all backed by a fully async database layer, alt account detection, paginated history GUIs, and PlaceholderAPI support. Every player-facing string is configurable via MiniMessage.

---

## Features

### Punishments
- Permanent and temporary **bans**, **IP bans**, **mutes**, **kicks**, and **warnings**
- Full **offline player** support — punish players who aren't currently online
- **IP bans** accept a player name (resolves their last known IP) or a raw IP address
- **Warn escalation** — automatically applies a configurable punishment when a player hits the warning threshold
- Warns older than `warns.expire-after` (default 30 days) stop counting toward the threshold automatically
- `/unban`, `/unbanip`, `/unmute`, `/unwarn` to lift any active punishment; `/unwarn` supports per-id removal

### Enforcement
- Banned and IP-banned players are blocked at the **pre-login stage** with a fully customisable disconnect screen
- Time-expired punishments are **auto-cleared on the next login attempt** — no manual cleanup needed
- Muted players are blocked from **chat and all commands**, with a configurable whitelist for auth plugins (AuthMe, etc.)
- Muted players are shown their **remaining mute time** whenever they try to chat
- A background **expiry sweeper** runs every 5 minutes to converge all expired and age-expired rows in the database

### Staff Tools
- `/check` — compact at-a-glance summary of a player's active ban, mute, and warn count
- `/history` — paginated chest GUI of every punishment a player has received, colour-coded by type
    - Each entry shows `Active`, `Revoked` (with the revoker's name and timestamp), or `Expired` status
- `/blame` — quick chat dump of a player's last 5 punishments (type, reason, issuer, date)
- `/staffhistory` — paginated GUI of all punishments issued by a specific staff member (case-insensitive)
- `/alts` — paginated GUI of every account sharing an IP with the target, with ban status per player head
- Staff alert channel (`murphyban.alerts`) notified on every ban, mute, kick, warn, and alt join event

### Alt Detection
- Every IP a player connects from is recorded automatically on login
- On join, the incoming IP is cross-checked against all actively banned accounts — staff are alerted instantly
- Optional **auto-ban** for alts of banned players (disabled by default)
- `/alts <player>` shows every account that has ever shared an IP with the target

### Database
- **SQLite** out of the box — zero configuration, file created automatically
- **MySQL** support with **HikariCP** connection pooling
- All queries run fully **async via CompletableFuture** — the main thread is never blocked
- Schema creation and column migrations run automatically on startup

### Developer
- Interface-driven database layer — swap SQLite for MySQL with a single config change
- Full **MiniMessage / Adventure** formatting for every player-facing message
- **PlaceholderAPI** expansion exposing 9 placeholders for ban/mute status and global counts
- `debug` mode toggled live via `/murphyban reload` — no restart needed
- Deterministic shutdown order: tasks cancelled → PAPI unregistered → caches cleared → audiences closed → DB disconnected

---

## Requirements

| Requirement | Version | Notes |
|---|---|---|
| Java | 21+ | Required |
| Spigot / Paper | 1.21 | Required |
| PlaceholderAPI | Any | Optional |

---

## Installation

1. Download the latest `MurphyBan-x.x.x.jar` from the [Releases](https://github.com/Murphycasto/MurphyBan/releases) page.
2. Place the jar in your server's `plugins/` folder.
3. Start the server once — `config.yml`, `messages.yml`, and `data.db` are generated automatically.
4. Edit `plugins/MurphyBan/config.yml` to configure your database and punishment policies.
5. Run `/murphyban reload` or restart the server to apply your changes.

---

## Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/ban <player> [duration] [reason]` | — | `murphyban.ban` | Ban a player. Permanent if no duration given. Supports offline players. |
| `/ipban <player\|ip> [duration] [reason]` | `/banip` | `murphyban.ipban` | Ban a player's last known IP, or a raw IP address. |
| `/mute <player> [duration] [reason]` | — | `murphyban.mute` | Mute a player from chat and non-whitelisted commands. |
| `/kick <player> [reason]` | — | `murphyban.kick` | Kick an online player from the server. |
| `/warn <player> [reason]` | — | `murphyban.warn` | Issue a formal warning. Auto-punishes at the configured threshold. |
| `/unban <player>` | `/pardon` | `murphyban.unban` | Lift an active ban. Records who revoked it and when. |
| `/unbanip <player\|ip>` | `/pardonip` | `murphyban.ipban` | Lift an active IP ban by player name or raw IP. |
| `/unmute <player>` | — | `murphyban.unmute` | Lift an active mute. Records who revoked it and when. |
| `/unwarn <player>` | — | `murphyban.unwarn` | List the player's active warnings with their IDs. |
| `/unwarn <player> <id>` | — | `murphyban.unwarn` | Remove a specific warning by ID. |
| `/check <player>` | `/status` | `murphyban.history` | Show a player's active ban, mute, and warn count at a glance. |
| `/history <player>` | `/ph` | `murphyban.history` | Open a paginated GUI of every punishment a player has received. |
| `/blame <player>` | — | `murphyban.history` | Print a player's last 5 punishments in chat. |
| `/staffhistory <staff>` | `/sh` | `murphyban.history` | Open a paginated GUI of all punishments issued by a staff member. |
| `/alts <player>` | — | `murphyban.alts` | Open a GUI of all accounts sharing any IP with the target. |
| `/murphyban about` | — | *(none — open to all)* | Show plugin version, database type, connection status, and server version. |
| `/murphyban reload` | — | `murphyban.admin` | Reload `config.yml` and `messages.yml` from disk without restarting. |

### Duration Format

Used by `/ban`, `/ipban`, `/mute`, and `warns.auto-punish-duration`.

| Unit | Meaning | Example |
|---|---|---|
| `s` | Seconds | `30s` |
| `m` | Minutes | `30m` |
| `h` | Hours | `12h` |
| `d` | Days | `7d` |
| `w` | Weeks | `2w` |
| `mo` | Months (30 days) | `1mo` |
| `permanent` / `perm` | Never expires | `permanent` |

Units can be combined freely: `1d12h`, `2w3d`, `1mo15d`.  
Omitting duration entirely on a punishment command defaults to permanent.  
If the duration argument fails to parse, it is treated as the start of the reason — `/ban Steve Griefing the spawn` works without a duration slot.

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `murphyban.ban` | Issue bans. | `op` |
| `murphyban.ipban` | Issue and remove IP bans. | `op` |
| `murphyban.mute` | Issue mutes. | `op` |
| `murphyban.kick` | Issue kicks. | `op` |
| `murphyban.warn` | Issue warnings. | `op` |
| `murphyban.unban` | Remove bans. | `op` |
| `murphyban.unmute` | Remove mutes. | `op` |
| `murphyban.unwarn` | Remove warnings. | `op` |
| `murphyban.history` | Use `/check`, `/history`, `/blame`, `/staffhistory`. | `op` |
| `murphyban.alts` | Use `/alts`. | `op` |
| `murphyban.alerts` | Receive staff alerts for bans, mutes, kicks, warns, and alt joins. | `op` |
| `murphyban.admin` | Full admin access including `/murphyban reload`. Bypasses all punishments. | `op` |

---

## Configuration

**`plugins/MurphyBan/config.yml`**

```yaml
# Enable verbose [DEBUG] logging. Picked up live by /murphyban reload — no restart needed.
# Leave false in production. Enable temporarily to trace command execution, player
# resolution, DB writes, and cache hits while reproducing a bug, then turn it off.
debug: false

database:
  # Backend to use. Allowed values: sqlite, mysql
  type: sqlite

  # MySQL connection details. Ignored when type is sqlite.
  host: localhost
  port: 3306
  name: murphyban
  username: root
  password: ''

mute:
  # Commands a muted player is still allowed to run.
  # Useful for auth plugins (AuthMe, etc.) where /login must work while muted.
  # Match command names without the leading slash. Case-insensitive.
  # An empty list blocks all commands while muted.
  command-whitelist:
    - register
    - login

alts:
  # Automatically ban alts of banned players on join.
  # Disabled by default — false positives are real on shared IPs (households, CG-NAT).
  auto-ban: false

  # Broadcast an alert to staff (murphyban.alerts) when an alt of a banned player joins.
  notify-staff: true

warns:
  # Number of active warnings before the auto-punish triggers.
  threshold: 3

  # Punishment type applied when the threshold is reached. Allowed values: mute, ban
  auto-punish: mute

  # Duration of the auto-punish. Supports all duration formats.
  auto-punish-duration: 1d

  # Warns older than this value no longer count toward the threshold.
  # The background sweeper also marks them inactive within 5 minutes.
  # Set to 0 to never expire warns by age.
  expire-after: 30d
```

---

## Messages

All messages live in `plugins/MurphyBan/messages.yml` and use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. Edit any value freely — changes apply after `/murphyban reload`.

```yaml
# ─────────────────────────────────────────
#  General
# ─────────────────────────────────────────

# Prefix prepended to most messages automatically.
prefix: "<gray>[<red>MurphyBan</red>]</gray> "

# Sent when a player lacks permission to run a command.
# Placeholders: {prefix}
no-permission: "{prefix}<red>You don't have permission to do that."

# Sent when a command target cannot be resolved.
# Placeholders: {prefix}, {player}
player-not-found: "{prefix}<red>Player <yellow>{player}</yellow> not found."

# Sent when console attempts to run a player-only command.
# Placeholders: {prefix}
must-be-player: "{prefix}<red>This command can only be run by a player."

# Sent when an unexpected error occurs. Check console for the full stack trace.
# Placeholders: {prefix}
generic-error: "{prefix}<red>An error occurred. Please check the console."

# Sent when a command is run with too few arguments.
# Placeholders: {prefix}, {usage}
usage: "{prefix}<red>Usage: <white>{usage}"

# Used as the reason string when no reason is provided in a command.
default-reason: "No reason provided"

# ─────────────────────────────────────────
#  Disconnect Screens
# ─────────────────────────────────────────

# Shown to banned players on login. {prefix} is NOT prepended.
# Placeholders: {player}, {reason}, {expires}, {issued_by}, {duration}
ban-screen: "<red><bold>You are banned!</bold></red>\n<gray>Reason: <white>{reason}</white></gray>\n<gray>Expires: <white>{expires}</white></gray>\n<gray>Appeal at: <white>yourserver.net</white></gray>"

# Shown to kicked players on disconnect. {prefix} is NOT prepended.
# Placeholders: {reason}
kick-message: "<red><bold>You have been kicked!</bold></red>\n<gray>Reason: <white>{reason}</white></gray>"

# ─────────────────────────────────────────
#  Mute
# ─────────────────────────────────────────

# Sent to a muted player who tries to chat or run a blocked command.
# {remaining} is "Never" for permanent mutes, human-readable time for temporary ones.
# Placeholders: {prefix}, {reason}, {remaining}
mute-screen: "{prefix}<red>You are muted. <gray>Reason: <white>{reason}</white> <gray>| Time remaining: <white>{remaining}</white>"

# ─────────────────────────────────────────
#  Success Messages (sent to the issuing staff member)
# ─────────────────────────────────────────

# Placeholders: {prefix}, {player}, {duration}
ban-success: "{prefix}<green>Successfully banned <white>{player}</white> {duration}."

# Placeholders: {prefix}, {target}, {duration}
ipban-success: "{prefix}<green>Successfully IP banned <white>{target}</white> {duration}."

# Placeholders: {prefix}, {player}, {duration}
mute-success: "{prefix}<green>Successfully muted <white>{player}</white> {duration}."

# Placeholders: {prefix}, {player}
kick-success: "{prefix}<green>Successfully kicked <white>{player}</white>."

# Placeholders: {prefix}, {player}, {count}
warn-success: "{prefix}<green>Successfully warned <white>{player}</white>. They now have <yellow>{count}</yellow> active warning(s)."

# Placeholders: {prefix}, {player}
unban-success: "{prefix}<green><white>{player}</white> has been unbanned."

# {target} is the player name when invoked by name, or the raw IP when invoked by IP.
# Placeholders: {prefix}, {target}
unbanip-success: "{prefix}<green>Successfully removed IP ban for <white>{target}</white>."

# Placeholders: {prefix}, {player}
unmute-success: "{prefix}<green><white>{player}</white> has been unmuted."

# Placeholders: {prefix}, {id}, {player}
unwarn-success: "{prefix}<green>Warning <white>#{id}</white> removed from <white>{player}</white>."

# ─────────────────────────────────────────
#  Not Found / Already Clean
# ─────────────────────────────────────────

# Placeholders: {prefix}, {player}
not-banned: "{prefix}<red><white>{player}</white> is not currently banned."

# Placeholders: {prefix}, {target}
not-ipbanned: "{prefix}<red>No active IP ban found for <white>{target}</white>."

# Placeholders: {prefix}, {player}
not-muted: "{prefix}<red><white>{player}</white> is not currently muted."

# Placeholders: {prefix}, {id}, {player}
warn-not-found: "{prefix}<red>No active warning with ID <white>#{id}</white> found for <white>{player}</white>."

# ─────────────────────────────────────────
#  Notifications (sent to the punished player)
# ─────────────────────────────────────────

# Placeholders: {prefix}, {staff}, {reason}
punishment-notify-ban: "{prefix}<red>You have been banned by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Placeholders: {prefix}, {staff}, {reason}
punishment-notify-mute: "{prefix}<red>You have been muted by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Placeholders: {prefix}, {staff}, {reason}
punishment-notify-warn: "{prefix}<yellow>You have been warned by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Sent as a second line immediately after punishment-notify-warn.
# Placeholders: {prefix}, {count}, {threshold}
warn-count-notify: "{prefix}<yellow>You now have <white>{count}</white> active warning(s). Threshold: <white>{threshold}</white>."

# ─────────────────────────────────────────
#  Staff Alerts (broadcast to murphyban.alerts)
# ─────────────────────────────────────────

# Sent to staff when an alt of a banned player joins.
# Placeholders: {prefix}, {joiner}, {banned}
alt-alert: "{prefix}<yellow><bold>ALT ALERT:</bold></yellow> <white>{joiner}</white> <gray>joined but shares an IP with banned player</gray> <red>{banned}</red>"

# Placeholders: {prefix}, {player}, {staff}, {reason}
staff-alert-ban: "{prefix}<gray>[STAFF] <red>{player}</red> has been banned by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Placeholders: {prefix}, {player}, {staff}, {reason}
staff-alert-mute: "{prefix}<gray>[STAFF] <red>{player}</red> has been muted by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Placeholders: {prefix}, {player}, {staff}, {reason}
staff-alert-kick: "{prefix}<gray>[STAFF] <red>{player}</red> has been kicked by <white>{staff}</white>. Reason: <white>{reason}</white>"

# Placeholders: {prefix}, {player}, {staff}, {reason}
staff-alert-warn: "{prefix}<gray>[STAFF] <yellow>{player}</yellow> has been warned by <white>{staff}</white>. Reason: <white>{reason}</white>"

# ─────────────────────────────────────────
#  Auto-Punish
# ─────────────────────────────────────────

# Sent to staff when a player's warn count hits the threshold and triggers an auto-punish.
# Placeholders: {prefix}, {player}, {threshold}, {action}
auto-punish-notify: "{prefix}<yellow><white>{player}</white> reached <white>{threshold}</white> warnings and was automatically <white>{action}</white>."

# ─────────────────────────────────────────
#  History & Lookup
# ─────────────────────────────────────────

# Placeholders: {prefix}, {player}
no-history: "{prefix}<gray><white>{player}</white> has no punishment history."

# Placeholders: {prefix}, {player}
no-alts: "{prefix}<gray>No alternate accounts found for <white>{player}</white>."

# Placeholders: {prefix}, {staff}
no-staff-history: "{prefix}<gray><white>{staff}</white> has not issued any punishments."

# Shown when a GUI page has no entries to display.
# Placeholders: {prefix}
history-empty-page: "{prefix}<gray>No entries on this page."

# ─────────────────────────────────────────
#  Admin
# ─────────────────────────────────────────

# Sent after /murphyban reload completes successfully.
# Placeholders: {prefix}
reload-success: "{prefix}<green>Configuration and messages reloaded successfully."
```

---

## Database Setup

### SQLite (default)

No setup required. A `data.db` file is created automatically at `plugins/MurphyBan/data.db` on first start.

### MySQL

Set `database.type` to `mysql` and fill in your credentials:

```yaml
database:
  type: mysql
  host: db.yourserver.net
  port: 3306
  name: murphyban
  username: murphyban_user
  password: 'your-password-here'
```

MurphyBan uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for connection pooling on both backends:

| Setting | Value |
|---|---|
| `maximumPoolSize` | `10` |
| `minimumIdle` | `2` |
| `connectionTimeout` | `30,000 ms` |
| `idleTimeout` | `600,000 ms` (10 min) |
| `maxLifetime` | `1,800,000 ms` (30 min) |

> **Async by design.** Every database operation runs off the main thread via `CompletableFuture`. Login checks, history queries, and punishment writes will never cause TPS drops regardless of database load.

---

## PlaceholderAPI

If [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, MurphyBan auto-registers its expansion on startup. The plugin loads cleanly without PAPI — placeholders simply will not resolve.

| Placeholder | Returns |
|---|---|
| `%murphyban_banned%` | `true` or `false` — whether the player has an active ban. |
| `%murphyban_banned_reason%` | Reason of the active ban, or empty string if not banned. |
| `%murphyban_banned_expires%` | Friendly expiry of the active ban (`Never`, remaining time, or empty). |
| `%murphyban_muted%` | `true` or `false` — checks in-memory cache first, then the database. |
| `%murphyban_muted_reason%` | Reason of the active mute, or empty string if not muted. |
| `%murphyban_muted_expires%` | Friendly expiry of the active mute, or empty string if not muted. |
| `%murphyban_warns%` | Total active warning count for the player (e.g. `3`). |
| `%murphyban_total_banned%` | Global active ban count across all players. |
| `%murphyban_total_muted%` | Global active mute count across all players. |

Mute lookups hit the in-memory `MuteCache` first — resolution is effectively free for online players. Global count placeholders use SQL-level expiry filtering for accuracy.

---

## Alt Detection

MurphyBan records every IP a player connects from. On each login, the incoming IP is cross-checked against all banned accounts — if a match is found, online staff are alerted immediately.

**Login flow:**

1. The player's UUID is checked against active `BAN` punishments. Their IP is checked against active `IP_BAN` punishments. Either match disconnects them with the `ban-screen` message. Expired punishments are auto-cleared and the login proceeds.
2. The `(uuid, ip)` pair is upserted into `ip_history`, refreshing their `last_seen` timestamp.
3. Every UUID ever seen on the joining IP is checked for active bans in parallel.
4. If a match is found and `alts.notify-staff: true`, the `alt-alert` message is broadcast to all players with `murphyban.alerts`.
5. If `alts.auto-ban: true`, the joining player is banned automatically with reason `Alt account of <banned>` and the same expiry as the original ban.

**Example alert:**
```
[MurphyBan] ⚠ ALT ALERT: Steve joined but shares an IP with banned player Notch
```

> **Note on false positives.** Shared IPs are common — households, school networks, and mobile carriers behind CG-NAT can all trigger alerts for completely unrelated players. `alts.auto-ban` defaults to `false` for this reason. Treat alerts as a signal to investigate, not as proof of ban evasion.

---

## Building from Source

```bash
git clone https://github.com/Murphycasto/MurphyBan
cd MurphyBan
mvn clean package
```

The shaded jar is output to `target/MurphyBan-<version>.jar`.

**Dependencies** (all managed by Maven):

| Dependency | Version | Scope |
|---|---|---|
| Spigot API | 1.21 | `provided` |
| PlaceholderAPI | 2.11.6 | `provided` |
| HikariCP | 5.1.0 | `shaded` |
| SQLite JDBC | 3.46.1.3 | `shaded` |
| MySQL Connector/J | 8.4.0 | `shaded` |
| Adventure API | 4.17.0 | `shaded` |
| Adventure MiniMessage | 4.17.0 | `shaded` |
| Adventure Platform Bukkit | 4.3.4 | `shaded` |

---

## Changelog

### v1.0.0
- `/ban`, `/ipban`, `/mute`, `/kick`, `/warn` with permanent + temporary durations and full offline player support
- `/unban`, `/unbanip`, `/unmute`, `/unwarn` with revocation tracking (who lifted it and when)
- `/check`, `/history`, `/blame`, `/staffhistory`, `/alts` staff tools
- Paginated chest GUIs for history (colour-coded wool icons) and alts (player heads with ban status)
- History GUI shows `Active`, `Revoked` (revoker name + timestamp), or `Expired` per entry
- Alt detection with full IP history tracking, configurable staff alerts, and optional auto-ban
- Warn threshold escalation with configurable type, duration, and age-based expiry (`warns.expire-after`)
- Muted players see their remaining mute time on every blocked action
- Warned players receive their current active warn count and threshold on every warning
- PlaceholderAPI expansion with 9 placeholders
- Async expiry sweeper running every 5 minutes (time-based + age-based warn expiry)
- `/murphyban reload` hot-reloads config and messages without touching the database pool
- SQLite and MySQL via HikariCP, fully async query layer, auto-migration on startup
- `debug` mode toggled live via reload — no restart needed
- Command aliases: `/banip`, `/pardon`, `/pardonip`, `/status`, `/ph`, `/sh`

---

## License

This project is licensed under the [MIT License](LICENSE).