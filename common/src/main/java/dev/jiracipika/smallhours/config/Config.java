package dev.jiracipika.smallhours.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;

/**
 * Loader-neutral JSON config. Each loader points us at its config dir once at
 * init; everything else lives here. Parsed once at startup — restart to apply.
 * Missing keys fall back to defaults, and the file is always rewritten after a
 * load so upgrades introduce new keys automatically.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "smallhours.json";

    // Master switch: false = the mod does nothing on its own (no accrual, no
    // ambience, no spawns). Summoned entities still behave.
    public static volatile boolean enabled = true;
    // Per-entity natural spawn gates (spawn eggs always work).
    public static volatile boolean omenEnabled = true;
    public static volatile boolean understudyEnabled = true;
    public static volatile boolean patientEnabled = true;
    // The director skips the very first night of a world by default so spawn
    // areas stay gentle while players find their feet.
    public static volatile boolean skipFirstNight = true;
    // Dread floor below which the Patient never hunts you.
    public static volatile int patientMinDread = Tuning.PATIENT_MIN_DREAD;
    // Understudy prefers sounds the target player actually triggered recently.
    public static volatile boolean mimicryEnabled = true;
    // Waking up near a tamed cat lifts the mark.
    public static volatile boolean catRitualEnabled = true;
    // Patient phases.
    public static volatile boolean patientLurk = true;
    public static volatile boolean patientMidnightRamp = true;
    public static volatile boolean patientClosingSteps = true;
    public static volatile boolean patientEyesOpen = true;
    // Shared sightings earn the witness half credit.
    public static volatile boolean witnessRule = true;

    private static Path dir;

    private Config() {
    }

    public static void init(Path configDir) {
        dir = configDir;
        load();
    }

    private static void load() {
        Path file = dir.resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                enabled = getBool(json, "enabled", enabled);
                omenEnabled = getBool(json, "omen", omenEnabled);
                understudyEnabled = getBool(json, "understudy", understudyEnabled);
                patientEnabled = getBool(json, "patient", patientEnabled);
                skipFirstNight = getBool(json, "skip_first_night", skipFirstNight);
                patientMinDread = getInt(json, "patient_min_dread", patientMinDread);
                mimicryEnabled = getBool(json, "mimicry", mimicryEnabled);
                catRitualEnabled = getBool(json, "cat_ritual", catRitualEnabled);
                patientLurk = getBool(json, "patient_lurk", patientLurk);
                patientMidnightRamp = getBool(json, "patient_midnight_ramp", patientMidnightRamp);
                patientClosingSteps = getBool(json, "patient_closing_steps", patientClosingSteps);
                patientEyesOpen = getBool(json, "patient_eyes", patientEyesOpen);
                witnessRule = getBool(json, "witness_rule", witnessRule);
                SmallHours.LOG.info("Small Hours config loaded from {}", file);
            } else {
                SmallHours.LOG.info("Small Hours config not found; writing defaults to {}", file);
            }
        } catch (Exception e) {
            SmallHours.LOG.warn("Small Hours config unreadable ({}); using defaults", e.toString());
        }
        save();
    }

    private static void save() {
        if (dir == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("_comment", "Small Hours. Restart the server after editing.");
        json.addProperty("enabled", enabled);
        json.addProperty("omen", omenEnabled);
        json.addProperty("understudy", understudyEnabled);
        json.addProperty("patient", patientEnabled);
        json.addProperty("skip_first_night", skipFirstNight);
        json.addProperty("patient_min_dread", patientMinDread);
        json.addProperty("mimicry", mimicryEnabled);
        json.addProperty("cat_ritual", catRitualEnabled);
        json.addProperty("patient_lurk", patientLurk);
        json.addProperty("patient_midnight_ramp", patientMidnightRamp);
        json.addProperty("patient_closing_steps", patientClosingSteps);
        json.addProperty("patient_eyes", patientEyesOpen);
        json.addProperty("witness_rule", witnessRule);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE_NAME), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SmallHours.LOG.warn("Small Hours config could not be written: {}", e.toString());
        }
    }

    private static boolean getBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }
}
