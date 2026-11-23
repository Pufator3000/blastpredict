package com.blastpredict;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlastPredictClient implements ClientModInitializer {
    public static final String MOD_ID = "blastpredict";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        BlastConfig.load();

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> BlastCommands.register(dispatcher));

        // Reconcile / time out predictions once per client tick.
        ClientTickEvents.END_CLIENT_TICK.register(BlastPredictor::tick);

        // Drop all pending predictions (without touching the world) when the connection or world changes.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BlastPredictor.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlastPredictor.clear());

        LOGGER.info("Blast Predict loaded: /bp, /fo, /po");
    }
}
