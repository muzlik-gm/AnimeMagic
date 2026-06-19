# AnimeMagic — Anime-inspired Magic Plugin for Paper

A magical-combat plugin for **Paper 1.17 → 1.21+** that brings four anime worlds into Minecraft:
**Naruto**, **Tensura** (That Time I Got Reincarnated as a Slime), **Mushoku Tensei**, and **One Piece**.

## v2 Overhaul Features (NEW)

This release completely overhauls the spells with production-grade choreography and adds several major systems:

### Overhauled Spells (multi-phase, anime-accurate)

| Spell | Phases | New effects |
|---|---|---|
| **Rasengan** | Formation → Charge → Thrust → Detonation | 3D sphere model + spin animation, lunge dash, 3-layer explosion |
| **Chidori** | Channel → Dash Strike → Impact | 3D lightning blade + slash animation, teleport-dash to target, paralysis |
| **Fireball Jutsu** | Hand Seal → Inhale → Exhale → Impact | 3D fireball_orb model + charge_v2 animation, Bezier trail, 3-sphere blast |
| **Conqueror's Haki** | Charge → Burst → Dominate → Collapse | 3D haki_dome model + burst animation, 15-block shockwave, mass paralysis |
| **Magicule Blade** | Draw → Stance → Slash | 3D magicule_sword + draw/slash_heavy animations, dash-strike |
| **Gluttony** | Mark → Drain → Absorb | 3D orb hovering above target, continuous drain beam, life transfer |
| **Gomu Gomu Pistol** | Windup → Stretch → Impact → Recoil | Bezier arm trail, red dust impact sphere, knockback 2.5 |

### New Control Schemes (7 total)

| Scheme | Trigger | Example |
|---|---|---|
| **Hotbar v2** (sneak-modifier) | RMB = normal; Sneak+RMB = variant | `/bind hotbar 1 naruto:fireball` |
| **Spell Wheel** | Sneak + RMB opens 3×3 wheel of recent spells | (no bind needed) |
| **Sneak-Cast** | Sneak while looking at a target | `/bind sneak naruto:rasengan` |
| **Combo (Hand Seals)** | L/R click sequence within 1.5s | `/bind combo LRL naruto:chidori` |
| **Cast Bar** | Boss-bar channeling (internal) | (programmatic) |
| **Look-Cast** (NEW) | Hold sneak + look at target 1.5s to channel | `/bind look onepiece:conquerors_haki` |
| **Double-Jump** (NEW) | Attempt a double-jump in mid-air | `/bind doublejump onepiece:gomu_pistol` |

### New Default Bindings System

Every school now has a default hotbar loadout. On first join, players get the **Naruto loadout**:

- **Slot 1** → naruto:fireball (+ sneak variant: mushoku:saint_fire)
- **Slot 2** → naruto:chidori (+ sneak variant: mushoku:saint_water)
- **Slot 3** → naruto:rasengan (+ sneak variant: mushoku:emperor_earth)
- **Slot 4** → naruto:shadow_clone

Switch loadouts with `/school <naruto|tensura|mushoku|onepiece>` or open the GUI with `/school`.

### New GUIs (3 total)

1. **Spellbook GUI** — multi-page school-filterable spell grid (existing)
2. **School Selector GUI** (NEW) — 27-slot GUI with icons for each of the 4 schools. Click to swap your hotbar loadout. `/school` to open.
3. **Mastery Tree GUI** (NEW) — 54-slot tiered spell tree per school. Spells organized by tier (T1 Novice, T2 Advanced, T3 Ultimate). Click any spell to cast. School filter tabs at the bottom. `/school mastery` to open.

### New 3D Models (7 total)

| Model | Used by | Description |
|---|---|---|
| `magic_orb` | Gluttony, Fireball (legacy) | Glowing purple orb cube |
| `chidori_blade` | Chidori | Vertical lightning blade + hilt |
| `rasengan_sphere` | Rasengan | Swirling blue sphere cube |
| `fireball_orb` (NEW) | Fireball Jutsu | Fire-charge orb cube |
| `haki_dome` (NEW) | Conqueror's Haki | Large purple dome cube |
| `magicule_sword` (NEW) | Magicule Blade | Diamond-sword model with magicule texture |
| `lightning_aura` (NEW) | (Available for future use) | Vertical lightning aura prismarine shard |

### New Animations (8 total)

| Animation | Length | Loop | Used by |
|---|---|---|---|
| `orb_spin` | 2.0s | yes | Rasengan |
| `cast_charge` | 1.5s | no | Gluttony |
| `slash_arc` | 0.6s | no | Chidori, Magicule Blade |
| `fireball.charge_v2` (NEW) | 1.2s | no | Fireball Jutsu |
| `fireball.launch` (NEW) | 0.4s | no | Fireball Jutsu |
| `haki.burst` (NEW) | 1.5s | no | Conqueror's Haki |
| `haki.collapse` (NEW) | 0.3s | no | Conqueror's Haki |
| `sword.draw` (NEW) | 0.5s | no | Magicule Blade |
| `sword.slash_heavy` (NEW) | 0.8s | no | Magicule Blade |
| `lightning.strike` (NEW) | 0.8s | no | Chidori |
| `lightning.dash` (NEW) | 0.3s | no | (Available for future use) |

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
| `/school` (NEW) | Open School Selector GUI | `animemagic.command.school` |
| `/school <name>` (NEW) | Switch active school loadout | `animemagic.command.school` |
| `/school mastery` (NEW) | Open Mastery Tree GUI | `animemagic.command.school` |
| `/bind hotbar <0-8> [spell_id]` | Bind/clear a hotbar slot (with sneak variant) | `animemagic.command.bind` |
| `/bind sneak [spell_id]` | Bind/clear sneak-cast | `animemagic.command.bind` |
| `/bind combo <L/R seq> [spell_id]` | Bind/clear a hand-seal combo | `animemagic.command.bind` |
| `/bind look [spell_id]` (NEW) | Bind/clear look-cast | `animemagic.command.bind` |
| `/bind doublejump [spell_id]` (NEW) | Bind/clear double-jump cast | `animemagic.command.bind` |
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
│   ├── controls/                     # 7 control schemes + ControlManager + ControlListener + DefaultBindings
│   ├── gui/                          # GUIManager + SpellSelectionGUI + SchoolSelectorGUI + MasteryGUI
│   ├── minigame/                     # ArenaManager + MagicArena + GameState
│   ├── commands/                     # 7 commands (Magic, Spell, Mana, Arena, Spellbook, Bind, School)
│   ├── listeners/                    # Player, Combat, GUI listeners
│   ├── mana/                         # ManaManager + ManaRegenTask
│   ├── metrics/                      # bStats wrapper
│   └── util/                         # MathUtil, LocationUtil, TextUtil, SpellEffects
├── src/main/resources/
│   ├── plugin.yml, config.yml, messages.yml, gui_textures.yml
│   ├── models/                       # 7 Blockbench-style model JSONs
│   └── animations/                   # 8 Blockbench-style animation JSONs
├── src/test/java/com/anime/magic/    # 6 JUnit test files (24+ tests)
├── scripts/generate_resource_pack.py # Generates AnimeMagicResourcePack.zip (28 pixel-art textures)
├── resource_pack/                    # Generated resource pack assets
└── index.html                        # Interactive pixel-art texture preview page
```

## License

MIT — anime references are for flavor only; this project is not affiliated with any anime rights holder.
