# Speed Zone map metadata

Speed Zone reads ordered points named `checkpoint`. The order of the points is the course direction.
Checkpoint `0` is the start/finish point. A lap is completed when the player crosses checkpoint `0`
after traversing the final segment.

Define section modifiers with the custom top-level `checkpoint_modifiers` key in `map.yml`.
The list index matches the checkpoint index. Crossing a checkpoint clears the previous modifier loadout
and applies the list at the checkpoint that was just crossed.

```yml
id: chromasion
name: Chromasion

points:
  checkpoint:
    - { x: 0,   y: 80, z: 0 }
    - { x: 80,  y: 82, z: 0 }
    - { x: 120, y: 70, z: 70 }
    - { x: 40,  y: 75, z: 120 }
    - { x: -30, y: 80, z: 60 }

checkpoint_modifiers:
  - []                    # C0 -> C1: normal parkour / running
  - [bridge]              # C1 -> C2
  - [riptide, elytra]     # C2 -> C3
  - [lunge, jump_boost]   # C3 -> C4
  - [wind_burst]          # C4 -> C0
```

Supported modifier keys:

- `elytra`
- `bridge`
- `riptide`
- `lunge`
- `wind_burst`
- `jump_boost`
- `boat`

Unknown keys are logged and ignored rather than failing the game.

The metadata may alternatively be written as an index-keyed section:

```yml
checkpoint_modifiers:
  "0": []
  "1": [bridge]
  "2": [riptide, elytra]
```
