package dev.cinderflask.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.cinderflask.Cinderflask;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON, so the only dependency stays Fabric API.
 *
 * <p>Everything here changes what a brew does, so the server pushes the whole of it to joining
 * clients (see {@code dev.cinderflask.net.ConfigSync}). What is on disk is the singleplayer and
 * fallback source.
 */
public final class CinderflaskConfig {
    /** Shared lockout after a sip, across every flask. Carrying a second bottle is the answer. */
    public int sipCooldownTicks = 60;

    /** Ticks in one phase of the wheel. 12,000 is half a day, so a full turn is two days. */
    public int ticksPerPhase = 12_000;

    /**
     * How many landmarks one dose may draw from.
     *
     * <p>The largest single lever on how the whole system feels. At 3 the space between two named
     * brews is continuous and a level brew gives you a spread of weak draughts; at 1 it becomes
     * hit the landmark or get nothing.
     */
    public int maxDraughtsPerDose = 3;

    /** Whether draughts resize blows between two players. A server may want brews out of duels. */
    public boolean draughtsAffectPvp = true;

    public Tuning draughts = new Tuning();

    /**
     * What the draughts are worth.
     *
     * <p>Only the numbers the combat hook reads live here. Ironroot's knockback resistance,
     * Quickstep's speed and Deepdelve's armour come from {@code addAttributeModifier}, which bakes
     * the value into the effect object when it is constructed, so those cannot be retuned without
     * rebuilding the registry and stay constants in {@code effect/Draughts.java}.
     */
    public static final class Tuning {
        /** One dial over every share below. Halve it to halve the whole system. */
        public float potency = 1.0f;

        /** How hard a lopsided brew rebounds afterwards. Zero removes the crash entirely. */
        public float comedownSeverity = 1.0f;

        public float berserkFromMissingHealth = 0.35f;
        public float berserkExposure = 0.10f;
        public float ironrootFlatReduction = 1.5f;
        public float sapswornLifestealShare = 0.15f;
        public float brambleReflectShare = 0.25f;
        public float unseenHandBonus = 0.5f;
        public float riposteAnswerBonus = 0.5f;
        public float quickstepSprintReduction = 0.3f;
        public float kelpwineWaterReduction = 0.15f;
        public float graveboundUndeadReduction = 0.2f;
        public float honeyedAllyHeal = 1.5f;
        public float graveboundKillHeal = 2.0f;
        public int emberbloodBurnSeconds = 2;

        /** The rebounds, which are the same shares read the other way round. */
        public float ashfallSoftening = 0.25f;
        public float brittleExposure = 0.25f;
        public float bloodlessDrain = 1.0f;
        public float plainSightExposure = 0.3f;

        /** Every share is read through here, so {@link #potency} is one dial over all of them. */
        public float dial(float share) {
            return share * potency;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CinderflaskConfig active = new CinderflaskConfig();

    // Snapshot of the on-disk values, so a server override can be undone without touching the file.
    private static CinderflaskConfig local = new CinderflaskConfig();

    public static CinderflaskConfig get() {
        return active;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Cinderflask.MOD_ID + ".json");
    }

    /** Loads {@code config/cinderflask.json}, writing defaults if it is absent and repairing it if it is not. */
    public static void load() {
        Path path = path();
        CinderflaskConfig loaded = new CinderflaskConfig();

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                CinderflaskConfig parsed = GSON.fromJson(json, CinderflaskConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonSyntaxException e) {
                Cinderflask.LOGGER.warn("Could not read {}, falling back to defaults.", path, e);
            }
        }

        loaded.sanitize();
        active = loaded;
        local = loaded.copy();
        save();
    }

    private void sanitize() {
        if (sipCooldownTicks < 0) {
            Cinderflask.LOGGER.warn("sipCooldownTicks was {}, clamping to 0.", sipCooldownTicks);
            sipCooldownTicks = 0;
        }
        if (ticksPerPhase < 200) {
            Cinderflask.LOGGER.warn("ticksPerPhase was {}, clamping to the minimum of 200.", ticksPerPhase);
            ticksPerPhase = 200;
        }
        if (maxDraughtsPerDose < 1 || maxDraughtsPerDose > 12) {
            Cinderflask.LOGGER.warn("maxDraughtsPerDose was {}, clamping into 1..12.", maxDraughtsPerDose);
            maxDraughtsPerDose = Math.min(12, Math.max(1, maxDraughtsPerDose));
        }

        // An older file, or one somebody deleted the block out of.
        if (draughts == null) {
            draughts = new Tuning();
        }

        draughts.potency = clamp("potency", draughts.potency);
        draughts.comedownSeverity = clamp("comedownSeverity", draughts.comedownSeverity);
        draughts.berserkFromMissingHealth = clamp("berserkFromMissingHealth", draughts.berserkFromMissingHealth);
        draughts.berserkExposure = clamp("berserkExposure", draughts.berserkExposure);
        draughts.ironrootFlatReduction = clamp("ironrootFlatReduction", draughts.ironrootFlatReduction);
        draughts.sapswornLifestealShare = clamp("sapswornLifestealShare", draughts.sapswornLifestealShare);
        draughts.brambleReflectShare = clamp("brambleReflectShare", draughts.brambleReflectShare);
        draughts.unseenHandBonus = clamp("unseenHandBonus", draughts.unseenHandBonus);
        draughts.riposteAnswerBonus = clamp("riposteAnswerBonus", draughts.riposteAnswerBonus);
        draughts.quickstepSprintReduction = clamp("quickstepSprintReduction", draughts.quickstepSprintReduction);
        draughts.kelpwineWaterReduction = clamp("kelpwineWaterReduction", draughts.kelpwineWaterReduction);
        draughts.graveboundUndeadReduction = clamp("graveboundUndeadReduction", draughts.graveboundUndeadReduction);
        draughts.honeyedAllyHeal = clamp("honeyedAllyHeal", draughts.honeyedAllyHeal);
        draughts.graveboundKillHeal = clamp("graveboundKillHeal", draughts.graveboundKillHeal);
        draughts.ashfallSoftening = clamp("ashfallSoftening", draughts.ashfallSoftening);
        draughts.brittleExposure = clamp("brittleExposure", draughts.brittleExposure);
        draughts.bloodlessDrain = clamp("bloodlessDrain", draughts.bloodlessDrain);
        draughts.plainSightExposure = clamp("plainSightExposure", draughts.plainSightExposure);

        if (draughts.emberbloodBurnSeconds < 0 || draughts.emberbloodBurnSeconds > 60) {
            Cinderflask.LOGGER.warn("emberbloodBurnSeconds was {}, clamping into 0..60.",
                    draughts.emberbloodBurnSeconds);
            draughts.emberbloodBurnSeconds = Math.min(60, Math.max(0, draughts.emberbloodBurnSeconds));
        }
    }

    /** Shares are all fractions or small flat amounts; nothing sane is negative or past ten. */
    private static float clamp(String name, float value) {
        if (Float.isNaN(value) || value < 0 || value > 10) {
            float fixed = Float.isNaN(value) ? 0 : Math.min(10, Math.max(0, value));
            Cinderflask.LOGGER.warn("draughts.{} was {}, clamping to {}.", name, value, fixed);
            return fixed;
        }
        return value;
    }

    private CinderflaskConfig copy() {
        return GSON.fromJson(GSON.toJson(this), CinderflaskConfig.class);
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(active), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Cinderflask.LOGGER.warn("Could not write {}.", path, e);
        }
    }

    /**
     * Applies the server's values, received on join. Client-side only.
     *
     * <p>Takes a whole config rather than an argument per field, so adding a knob is a change to
     * {@code ConfigSync} and nothing else.
     */
    public static void applyServerValues(CinderflaskConfig from) {
        from.sanitize();
        active = from;
    }

    /** Restores the on-disk values after leaving a server that overrode them. Client-side only. */
    public static void restoreLocalValues() {
        active = local.copy();
    }
}
