# AnimeMagic — Anime-inspired Magic Plugin for Paper

A magical-combat plugin for **Paper 1.17 → 1.21+** that brings four anime worlds into Minecraft:
**Naruto**, **Tensura** (That Time I Got Reincarnated as a Slime), **Mushoku Tensei**, and **One Piece**.

## Features

- **4 anime schools** with **13 spells** total
- **Custom 3D models** (Blockbench-style, via ItemDisplay entities)
- **Keyframe animations** (Blockbench `.anim.json` format, 22 easing functions)
- **5 control schemes** — command, hotbar, spell wheel, sneak-cast, hand-seal combos
- Custom **spellbook GUI** + **magical-duel minigame arena**
- Full **mana system** with cooldowns, bStats metrics, JUnit tests

## Build

```bash
mvn clean package
# Output: target/AnimeMagic-1.1.0.jar
```

Requires **Java 17+** and **Maven 3.6+**.

## Install

1. Drop `AnimeMagic-1.1.0.jar` into your Paper server's `plugins/` folder.
2. Start the server once to generate config files.
3. Edit `plugins/AnimeMagic/config.yml` to your liking.
4. (Optional) Run `python3 scripts/generate_resource_pack.py`, upload the
   resulting `AnimeMagicResourcePack.zip` to a static host, set
   `gui.resource-pack-url` in `config.yml`.
5. Restart or `/magic reload`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/magic reload` | Reload config + messages + models + animations | `animemagic.admin` |
| `/magic info` | Plugin info | — |
| `/spell cast <id>` | Cast a spell | `animemagic.command.spell` + `animemagic.spell.<school>` |
| `/spell list` | List known spells | `animemagic.command.spell` |
| `/mana show [player]` | Show mana | `animemagic.command.mana` |
| `/spellbook` | Open spellbook GUI | `animemagic.command.spellbook` |
| `/bind hotbar <0-8> [spell_id]` | Bind/clear a hotbar slot | `animemagic.command.bind` |
| `/bind sneak [spell_id]` | Bind/clear sneak-cast | `animemagic.command.bind` |
| `/bind combo <L/R seq> [spell_id]` | Bind/clear a hand-seal combo | `animemagic.command.bind` |
| `/arena create <name>` | Create arena at your location | `animemagic.arena.manage` |
| `/arena join <name>` | Join queue | `animemagic.arena.join` |

## Project Structure

```
AnimeMagicPlugin/
├── pom.xml                           # Maven build (Java 17, Paper API, bStats, org.json, JUnit 5)
├── src/main/java/com/anime/magic/
│   ├── AnimeMagicPlugin.java         # Main class
│   ├── core/                         # VersionManager, ModuleManager, MessageService
│   ├── api/                          # Spell, Caster, SpellSchool, SpellRegistry, CastingService
│   ├── schools/{naruto,tensura,mushoku,onepiece}/   # 13 spells across 4 schools
│   ├── effects/                      # ParticleEngine + 5 particle animation primitives
│   ├── models/                       # CustomModel, ModelRegistry, ModelDisplay (Blockbench-style)
│   ├── animation/                    # Easing (22 funcs), KeyframeAnimation, AnimationPlayer
│   ├── controls/                     # 5 control schemes + ControlManager + ControlListener
│   ├── gui/                          # GUIManager + SpellSelectionGUI
│   ├── minigame/                     # ArenaManager + MagicArena + GameState
│   ├── commands/                     # 6 commands (Magic, Spell, Mana, Arena, Spellbook, Bind)
│   ├── listeners/                    # Player, Combat, GUI listeners
│   ├── mana/                         # ManaManager + ManaRegenTask
│   ├── metrics/                      # bStats wrapper
│   └── util/                         # MathUtil, LocationUtil, TextUtil, SpellEffects
├── src/main/resources/
│   ├── plugin.yml, config.yml, messages.yml, gui_textures.yml
│   ├── models/                       # 3 sample Blockbench-style model JSONs
│   └── animations/                   # 3 sample Blockbench-style animation JSONs
├── src/test/java/com/anime/magic/    # 6 JUnit test files (24+ tests)
├── scripts/generate_resource_pack.py # Generates AnimeMagicResourcePack.zip (28 pixel-art textures)
├── resource_pack/                    # Generated resource pack assets
└── index.html                        # Fun interactive texture preview page
```

## License

MIT — anime references are for flavor only; this project is not affiliated with any anime rights holder.
