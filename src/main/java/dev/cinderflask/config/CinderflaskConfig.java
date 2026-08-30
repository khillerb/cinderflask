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
 * <p>{@link #ticksPerOperation} and {@link #maxEmbers} drive both burn behaviour and tooltip maths,
 * so the server pushes them to joining clients (see {@code dev.cinderflask.net.ConfigSync}). What is
 * here is the singleplayer and fallback source.
 */
public final class CinderflaskConfig {
    /** Burn ticks the flask spends per furnace ignition. 200 = exactly one vanilla smelt. */
    public int ticksPerOperation = 200;
    /** Maximum burn ticks the flask can hold. 10,000,000 ~= 6,250 coal ~= 97 stacks. */
    public int maxEmbers = 10_000_000;
    /** Whether sneak + right-click vacuums all valid fuel out of the player's inventory. */
    public boolean enableShiftFill = true;
    /** Whether sparking consumes the mob. Disable for a gentler, farm-friendly pack. */
    public boolean consumeSparkSource = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CinderflaskConfig active = new CinderflaskConfig();

    // Snapshot of the on-disk values, so a server override can be undone without touching the file.
    private static int localTicksPerOperation = new CinderflaskConfig().ticksPerOperation;
    private static int localMaxEmbers = new CinderflaskConfig().maxEmbers;

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
        localTicksPerOperation = loaded.ticksPerOperation;
        localMaxEmbers = loaded.maxEmbers;
        save();
    }

    private void sanitize() {
        if (ticksPerOperation < 100) {
            Cinderflask.LOGGER.warn("ticksPerOperation was {}, clamping to the minimum of 100.", ticksPerOperation);
            ticksPerOperation = 100;
        }
        if (maxEmbers < ticksPerOperation) {
            Cinderflask.LOGGER.warn("maxEmbers was {}, raising it to ticksPerOperation ({}).", maxEmbers, ticksPerOperation);
            maxEmbers = ticksPerOperation;
        }
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

    /** Applies the two server-authoritative numbers received on join. Client-side only. */
    public static void applyServerValues(int ticksPerOperation, int maxEmbers) {
        CinderflaskConfig config = active;
        config.ticksPerOperation = ticksPerOperation;
        config.maxEmbers = maxEmbers;
    }

    /** Restores the on-disk values after leaving a server that overrode them. Client-side only. */
    public static void restoreLocalValues() {
        applyServerValues(localTicksPerOperation, localMaxEmbers);
    }
}
