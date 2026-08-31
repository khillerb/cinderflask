package dev.cinderflask.datagen;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Writes {@code data/cinderflask/brewing/*.json}.
 *
 * <p>Kept as generated data rather than hand-written files so the drift check in CI covers it, and so
 * the humour values for the whole game sit in one readable list instead of forty little files.
 */
public class BrewingProvider implements DataProvider {
    private final DataOutput.PathResolver resolver;

    public BrewingProvider(FabricDataOutput output) {
        this.resolver = output.getResolver(DataOutput.OutputType.DATA_PACK, "brewing");
    }

    @Override
    public String getName() {
        return "Brewing entries";
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        List<CompletableFuture<?>> written = new ArrayList<>();

        // Bases. A base opens a brew and nothing else will go in until one has. Honey is not a
        // special case: it writes sanguine and a little melancholic, and "more doses, ages slower,
        // drinks softer" is simply what those two do.
        written.add(item(writer, Items.WATER_BUCKET, base(1, 0, 0, 0, 0)));
        written.add(item(writer, Items.HONEY_BOTTLE, base(3, 0, 1, 2, 0)));

        // Body: how much of the vessel you fill. More body is more doses and a milder brew.
        written.add(item(writer, Items.HONEYCOMB, body(2)));
        written.add(item(writer, Items.NETHER_WART, body(4)));
        written.add(item(writer, Items.PITCHER_PLANT, body(6)));

        // Choleric — hot and quick. Glowstone is vanilla's amplifier reagent, and force is what
        // choleric is, so it lands here without anything having to be explained.
        written.add(item(writer, Items.BLAZE_POWDER, humour(3, 0, 0, 0)));
        written.add(item(writer, Items.MAGMA_CREAM, humour(2, 0, 1, 0)));
        written.add(item(writer, Items.GLOWSTONE_DUST, humour(3, 0, 0, 0)));

        // Melancholic — cold and patient. Redstone is vanilla's duration reagent, for the same reason.
        written.add(item(writer, Items.IRON_NUGGET, humour(0, 2, 0, 0)));
        written.add(item(writer, Items.AZALEA, humour(0, 3, 0, 0)));
        written.add(item(writer, Items.PRISMARINE_CRYSTALS, humour(0, 2, 0, 1)));
        written.add(item(writer, Items.REDSTONE, humour(0, 3, 0, 0)));

        // Sanguine — sweet and vital.
        written.add(item(writer, Items.GLOW_BERRIES, humour(0, 0, 3, 0)));
        written.add(item(writer, Items.SWEET_BERRIES, humour(0, 0, 2, 0)));
        written.add(item(writer, Items.SEA_PICKLE, humour(0, 0, 2, 1)));

        // Phlegmatic — dull and strange.
        written.add(item(writer, Items.FERMENTED_SPIDER_EYE, humour(0, 0, 0, 3)));
        written.add(item(writer, Items.INK_SAC, humour(0, 1, 0, 2)));
        written.add(item(writer, Items.SPORE_BLOSSOM, humour(0, 0, 1, 2)));

        // The signatures. One ingredient per landmark, sitting nearer to it than to anything else,
        // so the obvious thing to put in Kelpwine is kelp. Deliberately a little off the coordinate
        // rather than exactly on it: a signature should open the route, not finish it by itself.
        written.add(item(writer, Items.CACTUS, humour(2, 3, 0, 0)));            // Bramblewine
        written.add(item(writer, Items.GLOW_LICHEN, humour(0, 2, 3, 0)));       // Deepdelve
        written.add(item(writer, Items.KELP, humour(0, 0, 3, 2)));              // Kelpwine
        written.add(item(writer, Items.SUGAR, humour(3, 0, 0, 2)));             // Quickstep Draught

        // The four that reach. Short on quintessence on purpose, so the route still has to buy some.
        written.add(item(writer, Items.FIRE_CHARGE, reaching(3, 0, 0, 0, 1)));            // Emberflask
        written.add(item(writer, Items.PRISMARINE_SHARD, reaching(0, 3, 0, 0, 1)));       // Riposte Cordial
        written.add(item(writer, Items.GLISTERING_MELON_SLICE, reaching(0, 0, 3, 0, 1))); // Honeyed Restorative
        written.add(item(writer, Items.WITHER_SKELETON_SKULL, reaching(0, 0, 0, 3, 1)));  // Gravemead

        // Reach. The shard is what does the echoing.
        written.add(item(writer, Items.AMETHYST_SHARD, aether(1)));
        written.add(item(writer, Items.GHAST_TEAR, aether(2)));
        written.add(item(writer, Items.ECHO_SHARD, aether(5)));
        written.add(item(writer, Items.NETHER_STAR, aether(5)));
        written.add(item(writer, Items.CHORUS_FRUIT, reaching(0, 0, 0, 1, 3)));
        written.add(item(writer, Items.ENDER_PEARL, reaching(0, 0, 1, 0, 3)));

        // Filth, bought deliberately rather than waited for.
        written.add(item(writer, Items.ROTTEN_FLESH, corrupt(0, 0, 0, 1, 0.15f)));
        written.add(item(writer, Items.WITHER_ROSE, corrupt(0, 0, 0, 2, 0.30f)));
        written.add(item(writer, Items.SOUL_SOIL, corrupt(0, 1, 0, 1, 0.20f)));
        written.add(item(writer, Items.POISONOUS_POTATO, corrupt(0, 0, 0, 1, 0.20f)));
        written.add(item(writer, Items.PUFFERFISH, corrupt(0, 0, 0, 2, 0.15f)));

        // Vanilla effects, so a brewing stand is the shallow way into the deep system. Described
        // here rather than guessed, because vanilla effect colours are decorative — Strength is a
        // dark red that a colour heuristic would read as sanguine.
        written.add(effect(writer, StatusEffects.STRENGTH, humour(4, 0, 0, 0)));
        written.add(effect(writer, StatusEffects.SPEED, humour(3, 0, 0, 0)));
        written.add(effect(writer, StatusEffects.HASTE, humour(2, 0, 0, 0)));
        written.add(effect(writer, StatusEffects.JUMP_BOOST, humour(2, 0, 0, 0)));
        written.add(effect(writer, StatusEffects.DOLPHINS_GRACE, humour(2, 0, 1, 0)));
        written.add(effect(writer, StatusEffects.FIRE_RESISTANCE, humour(2, 1, 0, 0)));

        written.add(effect(writer, StatusEffects.RESISTANCE, humour(0, 3, 0, 0)));
        written.add(effect(writer, StatusEffects.SLOWNESS, humour(0, 2, 0, 0)));
        written.add(effect(writer, StatusEffects.HEALTH_BOOST, humour(0, 2, 2, 0)));
        written.add(effect(writer, StatusEffects.ABSORPTION, humour(0, 2, 1, 0)));

        written.add(effect(writer, StatusEffects.REGENERATION, humour(0, 0, 3, 0)));
        written.add(effect(writer, StatusEffects.INSTANT_HEALTH, humour(0, 0, 3, 0)));
        written.add(effect(writer, StatusEffects.SATURATION, humour(0, 0, 2, 0)));
        written.add(effect(writer, StatusEffects.HERO_OF_THE_VILLAGE, humour(0, 0, 2, 0)));

        written.add(effect(writer, StatusEffects.INVISIBILITY, humour(0, 0, 0, 3)));
        written.add(effect(writer, StatusEffects.NIGHT_VISION, humour(0, 0, 0, 2)));
        written.add(effect(writer, StatusEffects.WATER_BREATHING, humour(0, 0, 1, 2)));
        written.add(effect(writer, StatusEffects.SLOW_FALLING, humour(1, 0, 0, 1)));

        written.add(effect(writer, StatusEffects.GLOWING, aether(1)));
        written.add(effect(writer, StatusEffects.LEVITATION, new Values(0, 0, 0, 0, 2, 1, 0, false)));
        written.add(effect(writer, StatusEffects.CONDUIT_POWER, new Values(0, 0, 0, 1, 0, 2, 0, false)));
        written.add(effect(writer, StatusEffects.LUCK, new Values(0, 0, 0, 1, 0, 1, 0, false)));

        written.add(effect(writer, StatusEffects.POISON, corrupt(0, 0, 0, 2, 0.10f)));
        written.add(effect(writer, StatusEffects.WITHER, corrupt(0, 0, 0, 3, 0.20f)));
        written.add(effect(writer, StatusEffects.WEAKNESS, corrupt(0, 1, 0, 0, 0.10f)));
        written.add(effect(writer, StatusEffects.NAUSEA, corrupt(0, 0, 0, 2, 0.10f)));
        written.add(effect(writer, StatusEffects.BLINDNESS, corrupt(0, 0, 0, 2, 0.10f)));
        written.add(effect(writer, StatusEffects.DARKNESS, corrupt(0, 0, 0, 3, 0.15f)));
        written.add(effect(writer, StatusEffects.HUNGER, corrupt(0, 1, 0, 0, 0.10f)));
        written.add(effect(writer, StatusEffects.MINING_FATIGUE, corrupt(0, 2, 0, 0, 0.10f)));
        written.add(effect(writer, StatusEffects.BAD_OMEN, corrupt(0, 0, 0, 2, 0.20f)));
        written.add(effect(writer, StatusEffects.INSTANT_DAMAGE, corrupt(2, 0, 0, 0, 0.20f)));

        return CompletableFuture.allOf(written.toArray(CompletableFuture[]::new));
    }

    // -----------------------------------------------------------------------------------------
    // Shorthand
    // -----------------------------------------------------------------------------------------

    private record Values(float body, float choleric, float melancholic, float sanguine,
                          float phlegmatic, float quintessence, float corruption, boolean base) {
    }

    private static Values humour(float cho, float mel, float san, float phl) {
        return new Values(0, cho, mel, san, phl, 0, 0, false);
    }

    private static Values aether(float quintessence) {
        return new Values(0, 0, 0, 0, 0, quintessence, 0, false);
    }

    /** Humours and reach together, for anything that carries its effect to somebody else. */
    private static Values reaching(float cho, float mel, float san, float phl, float quintessence) {
        return new Values(0, cho, mel, san, phl, quintessence, 0, false);
    }

    private static Values body(float body) {
        return new Values(body, 0, 0, 0, 0, 0, 0, false);
    }

    private static Values base(float body, float cho, float mel, float san, float phl) {
        return new Values(body, cho, mel, san, phl, 0, 0, true);
    }

    private static Values corrupt(float cho, float mel, float san, float phl, float corruption) {
        return new Values(0, cho, mel, san, phl, 0, corruption, false);
    }

    private CompletableFuture<?> item(DataWriter writer, Item item, Values values) {
        Identifier id = Registries.ITEM.getId(item);
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", id.toString());

        JsonObject json = describe(values);
        json.add("ingredient", ingredient);

        return DataProvider.writeToPath(writer, json, resolver.resolveJson(local(id)));
    }

    private CompletableFuture<?> effect(DataWriter writer, StatusEffect effect, Values values) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect);

        JsonObject json = describe(values);
        json.addProperty("effect", id.toString());

        return DataProvider.writeToPath(writer, json,
                resolver.resolveJson(local(new Identifier(id.getNamespace(), "effect/" + id.getPath()))));
    }

    /** Entries live under this mod's namespace whatever they describe. */
    private static Identifier local(Identifier source) {
        return new Identifier("cinderflask", source.getPath());
    }

    private static JsonObject describe(Values values) {
        JsonObject json = new JsonObject();
        JsonObject humours = new JsonObject();

        if (values.choleric() != 0) humours.addProperty("choleric", values.choleric());
        if (values.melancholic() != 0) humours.addProperty("melancholic", values.melancholic());
        if (values.sanguine() != 0) humours.addProperty("sanguine", values.sanguine());
        if (values.phlegmatic() != 0) humours.addProperty("phlegmatic", values.phlegmatic());
        if (values.quintessence() != 0) humours.addProperty("quintessence", values.quintessence());

        if (humours.size() > 0) json.add("humours", humours);
        if (values.body() != 0) json.addProperty("body", values.body());
        if (values.corruption() != 0) json.addProperty("corruption", values.corruption());
        if (values.base()) json.addProperty("base", true);

        return json;
    }
}
