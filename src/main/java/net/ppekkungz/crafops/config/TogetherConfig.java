package net.ppekkungz.crafops.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.ppekkungz.crafops.CrafOpsUtils;
import net.ppekkungz.crafops.interaction.FormationSettings;
import java.io.*;
import java.nio.file.*;

/** Server-owned, JSON-backed configuration. */
public final class TogetherConfig {
    public static final int CURRENT_VERSION = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile TogetherConfig INSTANCE = defaults();

    public int configurationVersion = CURRENT_VERSION;

    // Requests and chains
    public int maxPlayersPerChain = 2;
    public boolean requireConsent = true;
    public int maxPendingRequests = 5;
    public int requestTimeoutSeconds = 10;
    public int requestCooldownMillis = 1200;
    public double interactionDistance = 4.5;
    public double disconnectDistance = 8.0;

    // Travelling together
    public boolean teleportTogether = true;
    public double teleportDetectDistance = 6.0;

    // Formation (synced to clients, which drive the follower's movement)
    public double formationSideOffset = 1.04;
    public double formationBehindOffset = 0.0;
    public double movingFormationBehindOffset = 0.08;
    public double movementDetectionSpeed = 0.025;
    public double movementBlendSpeed = 0.12;
    public double formationYawSpeed = 14.0;
    public double followSpringStrength = 0.35;
    public double followDeadzone = 0.06;
    public double catchUpThreshold = 0.3;
    public double catchUpStrength = 0.22;
    public boolean swimTogether = true;

    // Visuals
    public double visualHandGap = 0.08;
    public double maxJumpArmLift = 0.55;
    public double armSwayAmount = 0.12;
    public double maxVisualPositionCorrection = 1.25;
    public boolean enableParticles = true;
    public boolean enableSoundEffects = true;

    public static TogetherConfig get() { return INSTANCE; }

    public FormationSettings formation() {
        return new FormationSettings(formationSideOffset, formationBehindOffset, movingFormationBehindOffset,
                movementDetectionSpeed, movementBlendSpeed, formationYawSpeed, followSpringStrength, followDeadzone,
                catchUpThreshold, catchUpStrength, swimTogether, visualHandGap, maxJumpArmLift, armSwayAmount,
                maxVisualPositionCorrection);
    }

    public static synchronized boolean load() {
        TogetherConfig loaded = defaults();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("crafops_together.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    TogetherConfig parsed = GSON.fromJson(json, TogetherConfig.class);
                    if (parsed != null) loaded = migrate(parsed, json);
                }
            }
            loaded.validate();
            INSTANCE = loaded;
            try (Writer writer = Files.newBufferedWriter(path)) { GSON.toJson(loaded, writer); }
            return true;
        } catch (IOException | RuntimeException error) {
            CrafOpsUtils.LOGGER.error("Unable to load {}. Safe defaults will be used.", path, error);
            INSTANCE = defaults();
            return false;
        }
    }

    /** Pre-v6 files tuned a server-side velocity solver that no longer exists; keep only the gameplay rules. */
    private static TogetherConfig migrate(TogetherConfig parsed, JsonObject json) {
        if (json.has("configurationVersion") && parsed.configurationVersion >= CURRENT_VERSION) return parsed;
        TogetherConfig fresh = defaults();
        fresh.maxPlayersPerChain = parsed.maxPlayersPerChain;
        fresh.requireConsent = parsed.requireConsent;
        fresh.maxPendingRequests = parsed.maxPendingRequests;
        fresh.requestCooldownMillis = parsed.requestCooldownMillis;
        fresh.interactionDistance = parsed.interactionDistance;
        fresh.disconnectDistance = parsed.disconnectDistance;
        fresh.enableSoundEffects = parsed.enableSoundEffects;
        return fresh;
    }

    public void validate() {
        configurationVersion = CURRENT_VERSION;
        maxPlayersPerChain = clamp(maxPlayersPerChain, 2, 64);
        maxPendingRequests = clamp(maxPendingRequests, 1, 20);
        requestTimeoutSeconds = clamp(requestTimeoutSeconds, 3, 120);
        requestCooldownMillis = clamp(requestCooldownMillis, 250, 10000);
        interactionDistance = clamp(interactionDistance, 1.0, 12.0);
        formationSideOffset = clamp(formationSideOffset, 0.75, 2.0);
        formationBehindOffset = clamp(formationBehindOffset, -0.75, 0.75);
        movingFormationBehindOffset = clamp(movingFormationBehindOffset, formationBehindOffset, 1.25);
        movementDetectionSpeed = clamp(movementDetectionSpeed, 0.005, 0.2);
        movementBlendSpeed = clamp(movementBlendSpeed, 0.02, 0.5);
        formationYawSpeed = clamp(formationYawSpeed, 2.0, 180.0);
        followSpringStrength = clamp(followSpringStrength, 0.05, 1.0);
        followDeadzone = clamp(followDeadzone, 0.01, 0.35);
        catchUpThreshold = clamp(catchUpThreshold, 0.1, 3.0);
        catchUpStrength = clamp(catchUpStrength, 0.0, 0.6);
        visualHandGap = clamp(visualHandGap, 0.02, 0.25);
        maxJumpArmLift = clamp(maxJumpArmLift, 0.0, 0.7);
        armSwayAmount = clamp(armSwayAmount, 0.0, 0.4);
        maxVisualPositionCorrection = clamp(maxVisualPositionCorrection, 0.25, 4.0);
        disconnectDistance = clamp(disconnectDistance, formationSideOffset + 1.0, 32.0);
        teleportDetectDistance = clamp(teleportDetectDistance, 2.0, 64.0);
    }

    private static TogetherConfig defaults() { return new TogetherConfig(); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private TogetherConfig() {}
}
