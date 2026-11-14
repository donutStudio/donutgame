# Architecture refactor: migration and verification

## Migration

| Before | Use |
| --- | --- |
| Module world map, `defaultWorld()`, or `mapManager().world()` | `game.world()` |
| `world.spawn(location, type)` | `world.summon(location, GameEntities.ZOMBIE)` |
| `teleportNow` or an unscoped Bukkit teleport | `teleport(GameLocation)`, then check the future |
| Entity `location()`, `velocity()`, `health()`, `fireTicks()` | `getLocation()`, `getVelocity()`, `getHealth()`, `getFireTicks()` |
| `worldSpawn()`, `setWorldSpawn()` | `getSpawnLocation()`, `setSpawnLocation()` |
| Item `material()`, `type()`, `amount()` | `getType()`, `getAmount()` |
| `setHunger`, `clearEffects`, `clearEffect` | `setFoodLevel`, `clearActivePotionEffects`, `removePotionEffect` |
| Raw loot tables and stacks from a roll | `GameLootTable`, `roll()`, and `GameItem` |
| Raw event/actor access or mutable edit callbacks | Scoped operations and `@GameEventHandler`; extend the explicit delegate policy if needed |

Paper event types are tokens in handlers. Event properties are explicit and fail visibly when they do not apply to the event. Mixed-world movement and damage events are filtered; an owned victim still receives death cleanup while a foreign killer is omitted. The registrar owns listener cleanup. Deploy the framework and rebuilt modules together because this changes method signatures.

The compiler verification rejects raw live types and framework bridges in game module source, including fully qualified references, static calls, inferred results, and nested generic arguments. Java visibility also removes several entry points. A few cross-package bridges remain for the framework's own Paper integration and are rejected by the checker. This is an API boundary for module builds; arbitrary code already running inside Paper is outside that boundary. Datapack resources likewise run under Paper's rules; module Java no longer has a helper that executes a function as the server console.

## Reproduce the checks

Run with JDK 25. The optional output directory is useful when an older JAR is still open on Windows:

```powershell
.gradlew.bat --offline -PverificationBuildDir=build/audit-build build
```

`build` compiles the framework and all three modules, runs the module compiler fixtures, and checks world binding and attribute math.

The original verification used Java 25 and passed all three module builds, 13 compiler fixtures, six state checks, and byte-for-byte regeneration of all eight generated Java files. The disposable AdvancedSlimePaper server and its fixture were removed after verifying world isolation, NPC death events, spectator reconnect, and delayed respawn. All three games loaded. Speed Zone used a two-checkpoint test course because its original map was unavailable.

## Size

| Measurement | Baseline | Refactored |
| --- | ---: | ---: |
| Handwritten framework Java lines | 15,409 | 14,739 |
| Handwritten generator Java lines | 1,336 | 523 |
| Generated Java lines | 4,710 | 2,691 |
| `GameEntityBase` handwritten lines | 453 | 290 |
| `GamePlayer` handwritten lines | 1,188 | 1,072 |
| Handwritten public/default API declarations | 707 | 637 |
| Handwritten private API declarations | 220 | 190 |
| Additional handwritten overload declarations | 113 | 93 |

Declaration counts use a source scan of `api` and `GameModule`, including framework-only bridges; generated and test sources are excluded. The targeted raw escape categories (plugin access, raw registration or event extraction, live edit callbacks, and direct bridge use) are rejected in modules. The spawning, synchronous teleport, and default-world aliases were removed.
