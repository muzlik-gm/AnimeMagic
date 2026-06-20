#!/usr/bin/env python3
"""
Generates the NEW MC 1.21.4+ item model files in assets/minecraft/items/.
These use the new 'select' model type instead of the old 'overrides' array.
"""
import json, os

OVERRIDES = {
    "paper": [
        (1001, "anime_magic:item/paper_1001"), (1002, "anime_magic:item/paper_1002"),
        (1003, "anime_magic:item/paper_1003"), (1004, "anime_magic:item/paper_1004"),
        (1005, "anime_magic:item/paper_1005"), (1010, "anime_magic:item/paper_1010"),
        (1011, "anime_magic:item/paper_1011"), (1012, "anime_magic:item/paper_1012"),
        (7001, "anime_magic:item/paper_7001"),
    ],
    "nether_star": [
        (2001, "anime_magic:item/nether_star_2001"), (2002, "anime_magic:item/nether_star_2002"),
        (2003, "anime_magic:item/nether_star_2003"), (2004, "anime_magic:item/nether_star_2004"),
        (7010, "anime_magic:item/nether_star_7010"),
    ],
    "fire_charge": [
        (3001, "anime_magic:item/fire_charge_3001"),
        (7004, "anime_magic:item/fire_charge_7004"),
        (7011, "anime_magic:item/fire_charge_7011"),
    ],
    "prismarine_shard": [
        (3002, "anime_magic:item/prismarine_shard_3002"),
        (7002, "anime_magic:item/prismarine_shard_7002"),
        (7007, "anime_magic:item/prismarine_shard_7007"),
        (7009, "anime_magic:item/prismarine_shard_7009"),
    ],
    "snowball": [
        (3003, "anime_magic:item/snowball_3003"),
        (7003, "anime_magic:item/snowball_7003"),
        (7008, "anime_magic:item/snowball_7008"),
    ],
    "soul_sand": [(3004, "anime_magic:item/soul_sand_3004")],
    "diamond_sword": [
        (4001, "anime_magic:item/diamond_sword_4001"),
        (7006, "anime_magic:item/diamond_sword_7006"),
    ],
    "black_dye": [(4002, "anime_magic:item/black_dye_4002")],
    "netherite_sword": [(4003, "anime_magic:item/netherite_sword_4003")],
    "water_bucket": [(5001, "anime_magic:item/water_bucket_5001")],
    "blaze_powder": [(5002, "anime_magic:item/blaze_powder_5002")],
    "stone": [(5003, "anime_magic:item/stone_5003")],
    "purple_glazed_terracotta": [
        (6001, "anime_magic:item/purple_glazed_terracotta_6001"),
        (7005, "anime_magic:item/purple_glazed_terracotta_7005"),
    ],
    "obsidian": [(6002, "anime_magic:item/obsidian_6002")],
    "red_dye": [(6003, "anime_magic:item/red_dye_6003")],
}

base = "/home/z/my-project/download/AnimeMagic/resource_pack"
items_dir = os.path.join(base, "assets", "minecraft", "items")
os.makedirs(items_dir, exist_ok=True)

for item_name, overrides in OVERRIDES.items():
    # New MC 1.21.4+ format: assets/minecraft/items/<item>.json
    cases = []
    for cmd, model_ref in overrides:
        cases.append({
            "when": [float(cmd)],
            "model": {
                "type": "minecraft:model",
                "model": model_ref
            }
        })

    new_format = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": cases,
            "fallback": {
                "type": "minecraft:model",
                "model": f"minecraft:item/{item_name}"
            }
        }
    }

    path = os.path.join(items_dir, f"{item_name}.json")
    with open(path, 'w') as f:
        json.dump(new_format, f, indent=2)
    print(f"  Created: assets/minecraft/items/{item_name}.json ({len(overrides)} cases)")

print(f"\nDone! Created {len(OVERRIDES)} item model files for MC 1.21.4+")
