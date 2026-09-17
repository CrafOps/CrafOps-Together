package net.ppekkungz.crafops.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.ppekkungz.crafops.CrafOpsUtils;
import java.io.*;
import java.nio.file.*;

/** Server-owned, JSON-backed configuration. */
public final class TogetherConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile TogetherConfig INSTANCE = defaults();

    public int configurationVersion = 5;
    public int maxPlayersPerChain = 2;
    public boolean requireConsent = true;
    public int maxPendingRequests = 5;
    public int requestTimeoutSeconds = 15;
    public int requestCooldownMillis = 1200;
    public double interactionDistance = 4.5;
    public double disconnectDistance = 8.0;
    public double stepAssistVelocity = 0.18;
    /** Side-by-side spacing and a small visible gap between the two wrist centres. */
    public double formationSideOffset = 1.04;
    public double formationBehindOffset = 0.0;
    public double movingFormationBehindOffset = 0.28;
    public double movementDetectionSpeed = 0.025;
    public double movementBlendSpeed = 0.14;
    public double visualHandGap = 0.08;
    public double maxJumpArmLift = 0.55;
    public double maxVisualPositionCorrection = 2.0;
    public double formationTolerance = 0.04;
    public double formationSpringStrength = 0.42;
    public double formationAcceleration = 0.20;
    public double formationMaxCorrectionSpeed = 0.90;
    public double leaderVelocityMatch = 0.90;
    public boolean enableVisualConnection = false;
    public boolean enableSoundEffects = true;
    public double visualRenderDistance = 64.0;

    public static TogetherConfig get() { return INSTANCE; }

    public static synchronized boolean load() {
        TogetherConfig loaded = defaults();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("crafops_together.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    TogetherConfig parsed = GSON.fromJson(json, TogetherConfig.class);
                    if (parsed != null) {
                        loaded = parsed;
                        if (!json.has("configurationVersion") || loaded.configurationVersion < 5) {
                            loaded.configurationVersion = 5;
                            loaded.formationSideOffset = 1.04;
                            loaded.formationBehindOffset = 0.0;
                            loaded.movingFormationBehindOffset = 0.28;
                            loaded.movementDetectionSpeed = 0.025;
                            loaded.movementBlendSpeed = 0.14;
                            loaded.visualHandGap = 0.08;
                            loaded.maxJumpArmLift = 0.55;
                            loaded.maxVisualPositionCorrection = 2.0;
                            loaded.formationTolerance = 0.03;
                            loaded.formationSpringStrength = 0.48;
                            loaded.formationAcceleration = 0.24;
                            loaded.formationMaxCorrectionSpeed = 1.0;
                            loaded.leaderVelocityMatch = 0.95;
                            loaded.enableVisualConnection = false;
                        }
                    }
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

    public void validate() {
        maxPlayersPerChain = clamp(maxPlayersPerChain, 2, 64);
        configurationVersion = 5;
        maxPendingRequests = clamp(maxPendingRequests, 1, 20);
        requestTimeoutSeconds = clamp(requestTimeoutSeconds, 3, 120);
        requestCooldownMillis = clamp(requestCooldownMillis, 250, 10000);
        interactionDistance = clamp(interactionDistance, 1.0, 12.0);
        stepAssistVelocity = clamp(stepAssistVelocity, 0.0, 0.42);
        formationSideOffset = clamp(formationSideOffset, 0.75, 2.0);
        formationBehindOffset = clamp(formationBehindOffset, -0.75, 0.75);
        movingFormationBehindOffset = clamp(movingFormationBehindOffset, formationBehindOffset, 1.25);
        movementDetectionSpeed = clamp(movementDetectionSpeed, 0.005, 0.2);
        movementBlendSpeed = clamp(movementBlendSpeed, 0.02, 0.5);
        visualHandGap = clamp(visualHandGap, 0.02, 0.25);
        maxJumpArmLift = clamp(maxJumpArmLift, 0.0, 0.7);
        maxVisualPositionCorrection = clamp(maxVisualPositionCorrection, 0.25, 4.0);
        formationTolerance = clamp(formationTolerance, 0.01, 0.35);
        formationSpringStrength = clamp(formationSpringStrength, 0.05, 1.0);
        formationAcceleration = clamp(formationAcceleration, 0.03, 0.5);
        formationMaxCorrectionSpeed = clamp(formationMaxCorrectionSpeed, 0.2, 1.5);
        leaderVelocityMatch = clamp(leaderVelocityMatch, 0.0, 1.0);
        disconnectDistance = clamp(disconnectDistance, formationSideOffset + 1.0, 32.0);
        visualRenderDistance = clamp(visualRenderDistance, 8.0, 256.0);
    }

    private static TogetherConfig defaults() { return new TogetherConfig(); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private TogetherConfig() {}
}
