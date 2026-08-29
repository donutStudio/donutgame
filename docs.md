# Donutgame API Notes

This branch is intentionally smaller than the old `test_3` codebase. The base plugin owns module loading, temporary game worlds, registered game players, teams, timers, map loading/placement, and simple UI. Module-specific gameplay rules should stay in the module.

## Plugin

- `Donutgame.fileService()` scans module jars and `.dmap` files.
- `Donutgame.moduleService()` loads/unloads active game modules.
- `Donutgame.mapService()` reads map metadata and assets.
- `Donutgame.worldService()` creates, fills, pastes into, and unloads temporary worlds.

`config.yml` supports:

- `modules_directory`
- `maps_directory`
- `runtime_directory`
- `max-active-games`
- `single-world`
- `join-active-game-world`

When `single-world` is true, active games are capped to one and new unregistered joiners are loosely teleported into the active game world as ordinary Bukkit spectators. They are not registered `GamePlayer`s unless module/admin code registers them.

## Modules

Extend `GameModule`.

- `beforeLoad()` chooses the base world/map.
- `onLoad()` initializes module state after the world is ready.
- `onStart()` starts gameplay after the countdown.
- `onReload()` defaults to `onLoad()`.
- `onUnload()` runs before teardown.
- `reload()` resets the initial map and starts the load/countdown sequence again.
- `unload()` unloads the active game.
- `registerEventHandlers(object)` registers methods annotated with `@GameEventHandler`.

Useful managers:

- `playerManager()`
- `teamManager()`
- `mapManager()`
- `timeManager()`
- `uiManager()`
- `borderManager()`
- `data()`

## Maps

`GameMap` contains:

- `id()`, `name()`, `tags()`
- `points()` and `regions()` for this map object. Maps returned by `placeMap(...)` have world-transformed coordinates.
- custom `metadata()` / `customData()` copied from all non-reserved `map.yml` keys
- embedded `submaps()`
- `isPlaced()`, `world()`, `origin()`, and `rotation()` for placed map objects

Reserved `map.yml` keys are `id`, `name`, `tags`, `points`, and `regions`.

`MapManager.setMap(...)` loads the game world.

`MapManager.placeMap(...)` pastes a schematic-backed map into the current world and returns a placed `GameMap`. Use `GameMap.getPoint(...)` and `GameMap.getRegion(...)` for transformed coordinates belonging to that placed copy. This is the preferred API for games like Lava Run that place repeated parent maps and submaps.

## Players

`PlayerManager` owns registered players only. Unregistered Bukkit players are not game participants.

`GamePlayer` uses fake spectator presentation for registered spectators: adventure mode, flight, invisibility, invulnerability, hidden inventory, and a spectator compass. It keeps a single stored gameplay snapshot for inventory, mode, health, food, XP, effects, and selected attributes, then restores that snapshot when leaving spectator state.

Per-player spectatable targets are still represented by:

- `setSpectatablePlayers(...)`
- `setSpectatableTeams(...)`
- `spectatablePlayers()`
- `spectatableTeams()`

The base spectator compass opens a target menu based on those per-player spectatable players and teams.

## Data And Items

- `GameItems.item(...)` parses compact item strings.
- `GameItems.items(...)` parses item lists or rolls a `LootTable`.
- `GameData.lootTable(key)` first asks Bukkit for a loaded loot table, then falls back to a lightweight module-resource parser under `data/<namespace>/loot_table/<path>.json`.
- `GameData.lootTable(items)` creates a simple equal-weight item pool.
- `GameItemAttributes` owns shared PDC-backed item attributes:
  - `donutgame:auto_ignite`
  - `donutgame:infinite_build`
  - `donutgame:team_sync`

The base item listener applies auto-ignite and infinite-build behavior. Team-sync is exposed as an API attribute; modules decide how that maps to their team palette.

## UI

`UiManager` provides titles, subtitles, action bars, chat messages, sounds, FastBoard sidebars, and entity glow.

`GameSidebar` uses FastBoard. Team glow is restored through `GlowService` and is refreshed from `TeamManager` membership.
