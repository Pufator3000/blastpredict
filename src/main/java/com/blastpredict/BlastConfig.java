package com.blastpredict;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Tiny persisted settings (config/blastpredict.properties). Only touched from the client thread. */
public final class BlastConfig {
    public enum Mode { FIXED, PLAYER }

    /** /bp on|off */
    public static boolean enabled = true;
    /** /fo = FIXED, /po = PLAYER */
    public static Mode mode = Mode.FIXED;
    /** /bp match : true = only predict blocks of the same type as the mined block */
    public static boolean matchSameBlock = true;

    private BlastConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("blastpredict.properties");
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            BlastPredictClient.LOGGER.warn("Could not read config: {}", e.toString());
            return;
        }
        enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
        matchSameBlock = Boolean.parseBoolean(props.getProperty("matchSameBlock", "true"));
        try {
            mode = Mode.valueOf(props.getProperty("mode", "FIXED").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.FIXED;
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("enabled", Boolean.toString(enabled));
        props.setProperty("mode", mode.name());
        props.setProperty("matchSameBlock", Boolean.toString(matchSameBlock));
        try (var out = Files.newOutputStream(file())) {
            props.store(out, "Blast Predict settings");
        } catch (IOException e) {
            BlastPredictClient.LOGGER.warn("Could not save config: {}", e.toString());
        }
    }
}
