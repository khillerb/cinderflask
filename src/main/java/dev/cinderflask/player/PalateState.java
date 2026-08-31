package dev.cinderflask.player;

import dev.cinderflask.brew.Humours;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everyone's palate, saved with the world.
 *
 * <p>A {@code PersistentState} keyed by player id rather than a components library, so the mod's only
 * dependency stays Fabric API.
 */
public class PalateState extends PersistentState {
    private static final String KEY = "cinderflask_palate";

    private final Map<UUID, Palate> palates = new HashMap<>();

    public static PalateState get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(PalateState::fromNbt, PalateState::new, KEY);
    }

    public Palate of(UUID player) {
        return palates.getOrDefault(player, Palate.empty());
    }

    /** Credits a dose and returns the palate that resulted. */
    public Palate record(ServerPlayerEntity player, Humours brew) {
        Palate updated = of(player.getUuid()).tasting(brew);
        palates.put(player.getUuid(), updated);
        markDirty();
        return updated;
    }

    public static PalateState fromNbt(NbtCompound nbt) {
        PalateState state = new PalateState();

        for (String key : nbt.getKeys()) {
            NbtList values = nbt.getList(key, NbtElement.FLOAT_TYPE);
            if (values.size() != 5) {
                continue;
            }

            float[] tasted = new float[5];
            for (int i = 0; i < 5; i++) {
                tasted[i] = values.getFloat(i);
            }

            try {
                state.palates.put(UUID.fromString(key), new Palate(tasted));
            } catch (IllegalArgumentException ignored) {
                // Not a player id; leave it alone rather than losing the rest of the file.
            }
        }

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        palates.forEach((player, palate) -> {
            NbtList values = new NbtList();
            for (int i = 0; i < 5; i++) {
                values.add(NbtFloat.of(palate.tasted(i)));
            }
            nbt.put(player.toString(), values);
        });

        return nbt;
    }
}
