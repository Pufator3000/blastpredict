package com.blastpredict;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client-side commands. These never reach the server. */
public final class BlastCommands {
    private BlastCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /bp           -> toggle prediction
        // /bp on | off  -> set explicitly
        // /bp status    -> show settings
        // /bp match     -> toggle "same block type only" filter
        dispatcher.register(literal("bp")
                .executes(ctx -> {
                    BlastConfig.enabled = !BlastConfig.enabled;
                    BlastConfig.save();
                    send(ctx.getSource(), "Prediction " + (BlastConfig.enabled ? "ON" : "OFF"),
                            BlastConfig.enabled ? Formatting.GREEN : Formatting.RED);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("on").executes(ctx -> {
                    BlastConfig.enabled = true;
                    BlastConfig.save();
                    send(ctx.getSource(), "Prediction ON", Formatting.GREEN);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("off").executes(ctx -> {
                    BlastConfig.enabled = false;
                    BlastConfig.save();
                    send(ctx.getSource(), "Prediction OFF", Formatting.RED);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("match").executes(ctx -> {
                    BlastConfig.matchSameBlock = !BlastConfig.matchSameBlock;
                    BlastConfig.save();
                    send(ctx.getSource(), BlastConfig.matchSameBlock
                            ? "Predicting only blocks of the same type as the mined block"
                            : "Predicting any breakable block in the formation", Formatting.YELLOW);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("status").executes(ctx -> {
                    send(ctx.getSource(), "Prediction " + (BlastConfig.enabled ? "ON" : "OFF")
                            + " | mode " + modeLabel(BlastConfig.mode)
                            + " | match same block: " + (BlastConfig.matchSameBlock ? "yes" : "no")
                            + " | pending: " + BlastPredictor.pendingCount(), Formatting.YELLOW);
                    return Command.SINGLE_SUCCESS;
                })));

        // /fo -> formation follows the face of the mined block
        dispatcher.register(literal("fo").executes(ctx -> setMode(ctx.getSource(), BlastConfig.Mode.FIXED)));

        // /po -> formation follows the direction the player is looking
        dispatcher.register(literal("po").executes(ctx -> setMode(ctx.getSource(), BlastConfig.Mode.PLAYER)));

        // /ho -> pitch hybrid: facing direction normally, mined face when looking steeply up/down
        dispatcher.register(literal("ho").executes(ctx -> setMode(ctx.getSource(), BlastConfig.Mode.HYBRID)));
    }

    private static int setMode(FabricClientCommandSource source, BlastConfig.Mode mode) {
        BlastConfig.mode = mode;
        BlastConfig.save();
        String text = switch (mode) {
            case FIXED -> "Fixed orientation: 3x3x2 follows the mined block's face";
            case PLAYER -> "Player orientation: 3x3x2 follows where you look";
            case HYBRID -> "Pitch hybrid: 3x3x2 follows your facing, or the mined face when you look steeply up/down";
        };
        if (!BlastConfig.enabled) {
            text += " (prediction is OFF, use /bp)";
        }
        send(source, text, Formatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static String modeLabel(BlastConfig.Mode mode) {
        return switch (mode) {
            case FIXED -> "FIXED (/fo)";
            case PLAYER -> "PLAYER (/po)";
            case HYBRID -> "HYBRID (/ho)";
        };
    }

    private static void send(FabricClientCommandSource source, String message, Formatting color) {
        source.sendFeedback(Text.literal("[BlastPredict] ").formatted(Formatting.GOLD)
                .append(Text.literal(message).formatted(color)));
    }
}
