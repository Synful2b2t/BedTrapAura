# BedTrapAura
BedTrapAura 1.0,
This Fabric mod automates building an obsidian “chamber” around a nearby bed. When activated, it searches for the closest bed within about 4.5 blocks and then constructs a 3‐block tall box around it, leaving one corner open. In addition, it extends that open corner with a 2×3 arrangement of obsidian blocks (6 extra blocks) to form a secure “chamber.”

Key Features:

Bed Detection:
The mod scans the nearby area (within 4.5 blocks) for a bed. Once found, it uses that bed as the reference point for building.

Chamber Construction:
It builds a 3‐block tall structure around the bed, leaving one corner open (the one closest to the player) to allow an entrance or exit. Then it adds a 2×3 extension at that corner for extra security.

Block Placement Logic:
The mod processes one block placement every tick. For each block, it “snaps” the player’s camera instantly to the center of the target block before attempting placement. After each placement attempt, the mod immediately checks whether the block has become obsidian. If it is, the mod moves on to the next block; if not, it continues to retry the same block the following tick.

Key Binding:
The entire process is activated by holding down the O key. When you press and hold O, the mod starts and continues building until you release the key.

This mod is designed to assist in quickly “surrounding” a bed with obsidian, which is useful for certain PvP or survival scenarios in Minecraft.
- _SynFul
(video tutorial https://www.youtube.com/watch?v=Bwgpe1weee8)
=================================================================================================================================
ONLY TESTED ON 1.21.1 CAN NOT VERIFY WORKING ON OTHER VERISONS
=================================================================================================================================
