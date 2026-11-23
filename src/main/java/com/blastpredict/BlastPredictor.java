package com.blastpredict;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client-side visual prediction of a server-side 3x3x2 "Blast".
 *
 * Flow:
 *  1. ClientPlayerInteractionManager#breakBlock(HEAD)  -> captureBreak(): remember what is about to break and the depth direction
 *  2. ClientPlayerInteractionManager#breakBlock(RETURN) -> confirmBreak(): if the local break succeeded, predict() the other 17 blocks
 *  3. ClientWorld#handleBlockUpdate(HEAD) -> onServerBlockUpdate(): the server spoke about a position, so drop our guess for it
 *     (that method is the single entry point for both single block updates and chunk delta updates)
 *  4. tick(): anything the server never confirmed within the timeout is restored
 *
 * Nothing here sends packets, creates drops or touches the server: it only changes the local world, and the server's
 * own updates always win.
 */
public final class BlastPredictor {
    /** NOTIFY_LISTENERS (2) | REDRAW_ON_MAIN_THREAD (8): re-render immediately, but no neighbour updates. */
    private static final int SET_FLAGS = 2 | 8;

    private record Prediction(BlockState original, long deadlineMs) {}

    private static final Map<BlockPos, Prediction> PENDING = new HashMap<>();
    private static ClientWorld trackedWorld;

    // State captured between breakBlock HEAD and RETURN.
    private static BlockPos capturedPos;
    private static BlockState capturedState;
    private static Direction capturedDepth;

    private BlastPredictor() {}

    // ---------------------------------------------------------------- hooks

    public static void captureBreak(BlockPos pos) {
        capturedPos = null;
        capturedState = null;
        capturedDepth = null;
        if (!BlastConfig.enabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity()) return;

        Direction depth;
        if (BlastConfig.mode == BlastConfig.Mode.PLAYER) {
            depth = lookDirection(mc.player);
        } else {
            Direction face = null;
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult blockHit
                    && blockHit.getType() == HitResult.Type.BLOCK
                    && blockHit.getBlockPos().equals(pos)) {
                face = blockHit.getSide();
            }
            // The formation extends INTO the block, i.e. opposite to the face that was hit.
            depth = face != null ? face.getOpposite() : lookDirection(mc.player);
        }

        capturedPos = pos.toImmutable();
        capturedState = state;
        capturedDepth = depth;
    }

    public static void confirmBreak(BlockPos pos, boolean broke) {
        BlockPos main = capturedPos;
        BlockState mainState = capturedState;
        Direction depth = capturedDepth;
        capturedPos = null;
        capturedState = null;
        capturedDepth = null;

        if (!broke || main == null || mainState == null || depth == null || !main.equals(pos)) return;
        predict(main, mainState, depth);
    }

    /** Called for every authoritative server block change (single update or chunk delta entry). */
    public static void onServerBlockUpdate(BlockPos pos) {
        if (!PENDING.isEmpty()) {
            PENDING.remove(pos);
        }
    }

    // ---------------------------------------------------------------- prediction

    private static void predict(BlockPos main, BlockState mainState, Direction depth) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        if (world != trackedWorld) {
            PENDING.clear();
            trackedWorld = world;
        }

        long deadline = System.currentTimeMillis() + timeoutMs(mc);
        Block target = mainState.getBlock();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : formation(main, depth)) {
            if (pos.equals(main) || PENDING.containsKey(pos)) continue;

            BlockState state = world.getBlockState(pos);
            if (!eligible(world, pos, state, target)) continue;

            PENDING.put(pos, new Prediction(state, deadline));
            world.setBlockState(pos, air, SET_FLAGS);
        }
    }

    private static boolean eligible(ClientWorld world, BlockPos pos, BlockState state, Block target) {
        if (state.isAir()) return false;
        if (BlastConfig.matchSameBlock && !state.isOf(target)) return false;
        if (state.hasBlockEntity() || world.getBlockEntity(pos) != null) return false; // chests, furnaces, ...
        if (!state.getFluidState().isEmpty()) return false;
        return state.getHardness(world, pos) >= 0; // never predict bedrock & co.
    }

    /** 2 layers of 3x3: the layer containing the mined block, then the layer directly behind it. */
    static List<BlockPos> formation(BlockPos origin, Direction depth) {
        int dx = depth.getOffsetX();
        int dy = depth.getOffsetY();
        int dz = depth.getOffsetZ();

        // Two unit vectors spanning the plane perpendicular to the depth axis.
        int[] u;
        int[] v;
        if (depth.getAxis() == Direction.Axis.X) {
            u = new int[] {0, 1, 0};
            v = new int[] {0, 0, 1};
        } else if (depth.getAxis() == Direction.Axis.Y) {
            u = new int[] {1, 0, 0};
            v = new int[] {0, 0, 1};
        } else {
            u = new int[] {1, 0, 0};
            v = new int[] {0, 1, 0};
        }

        List<BlockPos> result = new ArrayList<>(18);
        for (int layer = 0; layer < 2; layer++) {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    result.add(new BlockPos(
                            origin.getX() + dx * layer + u[0] * a + v[0] * b,
                            origin.getY() + dy * layer + u[1] * a + v[1] * b,
                            origin.getZ() + dz * layer + u[2] * a + v[2] * b));
                }
            }
        }
        return result;
    }

    /** Nearest of the six cardinal directions to where the entity is looking. */
    static Direction lookDirection(Entity entity) {
        double yaw = Math.toRadians(entity.getYaw());
        double pitch = Math.toRadians(entity.getPitch());
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);

        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);

        if (ay >= ax && ay >= az) return y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return x > 0 ? Direction.EAST : Direction.WEST;
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ---------------------------------------------------------------- reconciliation

    /** How long we wait for the server before undoing a guess: 1.5 s + 3x ping, capped at 5 s. */
    private static long timeoutMs(MinecraftClient mc) {
        int latency = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) latency = Math.max(0, entry.getLatency());
        }
        return Math.min(5000L, 1500L + 3L * latency);
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world != trackedWorld) { // dimension change or disconnect: the old world is gone, nothing to restore
            PENDING.clear();
            trackedWorld = world;
        }
        if (world == null || PENDING.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Prediction>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Prediction> entry = it.next();
            if (now < entry.getValue().deadlineMs()) continue;

            // Still our predicted air and the server never said anything -> the server did not break it. Undo.
            // If it is no longer air, something newer replaced it; leave it alone.
            if (world.getBlockState(entry.getKey()).isAir()) {
                world.setBlockState(entry.getKey(), entry.getValue().original(), SET_FLAGS);
            }
            it.remove();
        }
    }

    /** Forget everything without touching the world (join, disconnect). */
    public static void clear() {
        PENDING.clear();
        trackedWorld = null;
        capturedPos = null;
        capturedState = null;
        capturedDepth = null;
    }

    public static int pendingCount() {
        return PENDING.size();
    }
}
