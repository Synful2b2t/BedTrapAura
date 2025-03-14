package net.synful.examplemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.Queue;

public class ExampleModClient implements ClientModInitializer {

    // Key binding (default: O)
    private static KeyBinding surroundKey;
    // Queue for all positions we want to place obsidian
    private final Queue<BlockPos> pendingPlacements = new LinkedList<>();
    // The block we're currently trying to place (will retry until successful)
    private BlockPos currentPlacement = null;
    // Flag to ensure we start the build only once when key is held
    private boolean buildStarted = false;
    // Tick counter to throttle placement: process 1 block attempt per tick.
    private int tickCounter = 0;
    // Confirmation counter: number of consecutive ticks the block is confirmed as obsidian.
    private int confirmCounter = 0;  // Added field

    // New fields for camera snap behavior.
    private boolean cameraSnapped = false;
    private int snapWaitCounter = 0;

    @Override
    public void onInitializeClient() {
        // Set key binding to O (GLFW.GLFW_KEY_O)
        surroundKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.obsidianboxmod.surround", GLFW.GLFW_KEY_O, "category.obsidianboxmod")
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (surroundKey.isPressed()) {
                if (!buildStarted) {
                    buildStarted = true;
                    BlockPos bedPos = findClosestBed(client, 4.5);
                    if (bedPos != null) {
                        surroundBedWithChamberAndExtension(client, bedPos);
                    }
                }
                processPendingPlacements(client);
            } else {
                // When key is released, clear pending placements and reset state.
                buildStarted = false;
                pendingPlacements.clear();
                currentPlacement = null;
                tickCounter = 0;
                confirmCounter = 0;
                cameraSnapped = false;
                snapWaitCounter = 0;
            }
        });
    }

    /**
     * Finds the closest bed within maxDistance of the player's position.
     */
    private BlockPos findClosestBed(MinecraftClient client, double maxDistance) {
        if (client.player == null || client.world == null) return null;
        Vec3d playerPos = client.player.getPos();
        BlockPos playerBlockPos = client.player.getBlockPos();

        BlockPos closestBed = null;
        double closestDistance = Double.MAX_VALUE;
        int range = (int) Math.ceil(maxDistance);

        for (int x = playerBlockPos.getX() - range; x <= playerBlockPos.getX() + range; x++) {
            for (int y = playerBlockPos.getY() - range; y <= playerBlockPos.getY() + range; y++) {
                for (int z = playerBlockPos.getZ() - range; z <= playerBlockPos.getZ() + range; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (client.world.getBlockState(pos).getBlock() instanceof BedBlock) {
                        double distance = playerPos.distanceTo(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
                        if (distance <= maxDistance && distance < closestDistance) {
                            closestDistance = distance;
                            closestBed = pos;
                        }
                    }
                }
            }
        }
        return closestBed;
    }

    /**
     * Builds a 3-block-tall obsidian chamber around the bed,
     * leaving a 3×1 hole at the corner closest to the player,
     * then adds a 2×3 extension at that corner.
     */
    private void surroundBedWithChamberAndExtension(MinecraftClient client, BlockPos bedPos) {
        // Verify the block is a bed.
        if (!(client.world.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
            return;
        }
        // Get bed properties.
        Direction facing = client.world.getBlockState(bedPos).get(BedBlock.FACING);
        BedPart part = client.world.getBlockState(bedPos).get(BedBlock.PART);
        BlockPos otherPartPos = (part == BedPart.FOOT)
                ? bedPos.offset(facing)
                : bedPos.offset(facing.getOpposite());
        if (!(client.world.getBlockState(otherPartPos).getBlock() instanceof BedBlock)) {
            return;
        }
        int bedY = bedPos.getY();
        int minX = Math.min(bedPos.getX(), otherPartPos.getX()) - 1;
        int maxX = Math.max(bedPos.getX(), otherPartPos.getX()) + 1;
        int minZ = Math.min(bedPos.getZ(), otherPartPos.getZ()) - 1;
        int maxZ = Math.max(bedPos.getZ(), otherPartPos.getZ()) + 1;
        Vec3d playerPos = client.player.getPos();
        BlockPos[] bottomCorners = {
                new BlockPos(minX, bedY, minZ),
                new BlockPos(minX, bedY, maxZ),
                new BlockPos(maxX, bedY, minZ),
                new BlockPos(maxX, bedY, maxZ)
        };
        BlockPos chosenCorner = null;
        double closestDistance = Double.MAX_VALUE;
        for (BlockPos corner : bottomCorners) {
            double distance = playerPos.distanceTo(new Vec3d(corner.getX() + 0.5, corner.getY() + 0.5, corner.getZ() + 0.5));
            if (distance < closestDistance) {
                closestDistance = distance;
                chosenCorner = corner;
            }
        }
        // Build a 3-tall chamber, skipping the chosen corner.
        int structureHeight = 3;
        for (int yOffset = 0; yOffset < structureHeight; yOffset++) {
            int currentY = bedY + yOffset;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos targetPos = new BlockPos(x, currentY, z);
                    // Skip the actual bed blocks at the base.
                    if ((x == bedPos.getX() && z == bedPos.getZ() && currentY == bedY) ||
                            (x == otherPartPos.getX() && z == otherPartPos.getZ() && currentY == bedY)) {
                        continue;
                    }
                    // Skip the chosen corner hole.
                    if (chosenCorner != null && x == chosenCorner.getX() && z == chosenCorner.getZ()) {
                        continue;
                    }
                    if (client.world.getBlockState(targetPos).isAir()) {
                        pendingPlacements.add(targetPos);
                    }
                }
            }
        }
        // Add a 2×3 extension around the chosen corner (6 blocks total).
        if (chosenCorner != null) {
            placeCornerExtension(client, chosenCorner, minX, maxX, minZ, maxZ, bedY);
        }
    }

    /**
     * Places a 2×3 extension adjacent to the chosen corner (placed outward from the bounding box).
     */
    private void placeCornerExtension(MinecraftClient client, BlockPos chosenCorner,
                                      int minX, int maxX, int minZ, int maxZ, int bedY) {
        int dx = (chosenCorner.getX() == minX) ? -1 : 1;
        int dz = (chosenCorner.getZ() == minZ) ? -1 : 1;
        BlockPos col1Base = chosenCorner.add(dx, 0, 0);
        BlockPos col2Base = chosenCorner.add(0, 0, dz);
        for (int yOffset = 0; yOffset < 3; yOffset++) {
            BlockPos c1 = col1Base.up(yOffset);
            BlockPos c2 = col2Base.up(yOffset);
            if (client.world.getBlockState(c1).isAir()) {
                pendingPlacements.add(c1);
            }
            if (client.world.getBlockState(c2).isAir()) {
                pendingPlacements.add(c2);
            }
        }
    }

    /**
     * Processes pending placements.
     * This version waits 2 ticks between each block attempt.
     * For the current block, it checks every tick if it is obsidian,
     * and only moves on after 1 tick of confirmation.
     */
    private void processPendingPlacements(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        tickCounter++;
        // Process one block attempt every 2 ticks.
        if (tickCounter % 2 != 0) {
            return;
        }

        if (currentPlacement == null && !pendingPlacements.isEmpty()) {
            currentPlacement = pendingPlacements.poll();
            confirmCounter = 0;
            cameraSnapped = false;
            snapWaitCounter = 0;
        }
        if (currentPlacement != null) {
            // Snap the camera to the target block if not already snapped.
            if (!cameraSnapped) {
                snapCameraToBlock(client, currentPlacement);
                cameraSnapped = true;
                snapWaitCounter = 0;
                return; // Wait for the next tick before attempting placement.
            } else {
                snapWaitCounter++;
                // Wait one tick after snapping before attempting placement.
                if (snapWaitCounter < 1) {
                    return;
                }
                tryPlaceBlock(client, currentPlacement);
                if (client.world.getBlockState(currentPlacement).isOf(Blocks.OBSIDIAN)) {
                    confirmCounter++;
                } else {
                    confirmCounter = 0;
                }
                // Only move on if confirmed for 1 consecutive tick.
                if (confirmCounter >= 1) {
                    currentPlacement = null;
                    confirmCounter = 0;
                }
            }
        }
    }

    /**
     * Instantly snaps the player's camera to face the center of the target block.
     */
    private void snapCameraToBlock(MinecraftClient client, BlockPos pos) {
        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY() + 0.5;
        double targetZ = pos.getZ() + 0.5;
        Vec3d eyePos = client.player.getCameraPosVec(1.0F);
        double diffX = targetX - eyePos.x;
        double diffY = targetY - eyePos.y;
        double diffZ = targetZ - eyePos.z;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(diffY, horizontalDist)));
        client.player.setYaw(targetYaw);
        client.player.setPitch(targetPitch);
    }

    /**
     * Searches the player's hotbar (slots 0-8) for an obsidian block.
     * Returns the slot index or -1 if not found.
     */
    private int findObsidianInHotbar(MinecraftClient client) {
        if (client.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == Blocks.OBSIDIAN.asItem()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Simulates a real player block placement by:
     * 1) Switching to the obsidian slot.
     * 2) Rotating the player's camera to face the target surface.
     * 3) Interacting with the neighbor block to place.
     * 4) Restoring the player's rotation and hotbar slot.
     */
    private void tryPlaceBlock(MinecraftClient client, BlockPos pos) {
        int obsidianSlot = findObsidianInHotbar(client);
        if (obsidianSlot == -1) {
            return;
        }
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = obsidianSlot;

        Direction[] candidateDirections = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST, Direction.UP
        };
        BlockPos neighborPos = null;
        Direction placementFace = null;
        for (Direction dir : candidateDirections) {
            BlockPos candidate = pos.offset(dir);
            if (!client.world.getBlockState(candidate).isAir() &&
                    !(client.world.getBlockState(candidate).getBlock() instanceof BedBlock)) {
                neighborPos = candidate;
                placementFace = Direction.fromVector(
                        pos.getX() - candidate.getX(),
                        pos.getY() - candidate.getY(),
                        pos.getZ() - candidate.getZ()
                );
                break;
            }
        }
        if (neighborPos == null || placementFace == null) {
            client.player.getInventory().selectedSlot = prevSlot;
            return;
        }
        Vec3d hitVec = new Vec3d(
                neighborPos.getX() + 0.5 + placementFace.getOffsetX() * 0.5,
                neighborPos.getY() + 0.5 + placementFace.getOffsetY() * 0.5,
                neighborPos.getZ() + 0.5 + placementFace.getOffsetZ() * 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitVec, placementFace, neighborPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.getInventory().selectedSlot = prevSlot;
    }
}
