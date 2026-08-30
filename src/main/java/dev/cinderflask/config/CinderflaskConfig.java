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
 * <p>Both values change what a brew does, so the server pushes them to joining clients (see
 * {@code dev.cinderflask.net.ConfigSync}). What is here is the singleplayer and fallback source.
 */
public final class CinderflaskConfig {
    /** Shared lockout after a sip, across every flask. Carrying a second bottle is the answer. */
    public int sipCooldownTicks = 60;
    /** Ticks in one phase of the wheel. 12,000 is half a day, so a full turn is two days. */
    public int ticksPerPhase = 12_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CinderflaskConfig active = new CinderflaskConfig();

    // Snapshot of the on-disk values, so a server override can be undone without touching the file.
    private static int localSipCooldownTicks = new CinderflaskConfig().sipCooldownTicks;
    private static int localTicksPerPhase = new CinderflaskConfig().ticksPerPhase;

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
        localSipCooldownTicks = loaded.sipCooldownTicks;
        localTicksPerPhase = loaded.ticksPerPhase;
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
    public static void applyServerValues(int sipCooldownTicks, int ticksPerPhase) {
        CinderflaskConfig config = active;
        config.sipCooldownTicks = sipCooldownTicks;
        config.ticksPerPhase = ticksPerPhase;
    }

    /** Restores the on-disk values after leaving a server that overrode them. Client-side only. */
    public static void restoreLocalValues() {
        applyServerValues(localSipCooldownTicks, localTicksPerPhase);
    }
}
