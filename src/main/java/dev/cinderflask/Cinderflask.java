package dev.cinderflask;

import dev.cinderflask.brew.BrewingRecipes;
import dev.cinderflask.brew.Cracking;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.effect.Draughts;
import dev.cinderflask.effect.Rebounds;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.item.DregsItem;
import dev.cinderflask.item.SinterItem;
import dev.cinderflask.item.SumpItem;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.player.PalateSync;
import dev.cinderflask.recipe.CorkRecipe;
import dev.cinderflask.recipe.MendRecipe;
import dev.cinderflask.recipe.VesselRecipes;
import dev.cinderflask.screen.CinderflaskScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.recipe.CookingRecipeSerializer;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Cinderflask implements ModInitializer {
    public static final String MOD_ID = "cinderflask";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cinderflask");

    // The vessel ladder. Capacity is a ceiling that body ingredients fill, so a wider flask is more
    // doses rather than a stronger brew — the trade between the two is concentration, and that stays
    // yours to make at every tier.
    public static final CinderflaskItem CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1), 8, 0);
    public static final CinderflaskItem BOUND_CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1), 12, 0);
    public static final CinderflaskItem WITCH_IRON_CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1), 17, 0);

    /** The only vessel that lends reach on its own, which is what makes support play a late unlock. */
    public static final CinderflaskItem AETHERGLASS_CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1), 22, 1);

    /** What a brew becomes when it is left too long, and the way back into the corrupt half. */
    public static final SumpItem SUMP = new SumpItem(new FabricItemSettings());

    /** What settles in a flask you drank dry. Opens the next brew part-aged. */
    public static final DregsItem DREGS = new DregsItem(new FabricItemSettings());

    /** A cracked flask packed in sand, waiting for the fire. */
    public static final SinterItem SINTER = new SinterItem(new FabricItemSettings().maxCount(1));

    public static final ScreenHandlerType<CinderflaskScreenHandler> SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(CinderflaskScreenHandler::new);

    public static final RecipeSerializer<CorkRecipe> CORK_RECIPE =
            new SpecialRecipeSerializer<>(CorkRecipe::new);
    public static final RecipeSerializer<VesselRecipes.Solera> SOLERA_RECIPE =
            new SpecialRecipeSerializer<>(VesselRecipes.Solera::new);
    public static final RecipeSerializer<VesselRecipes.Sinter> SINTER_RECIPE =
            new SpecialRecipeSerializer<>(VesselRecipes.Sinter::new);
    public static final RecipeSerializer<MendRecipe> MEND_RECIPE =
            new CookingRecipeSerializer<>(MendRecipe::new, 200);

    public static final RecipeSerializer<VesselRecipes.Upgrade> BIND_RECIPE =
            new SpecialRecipeSerializer<>((id, category) -> new VesselRecipes.Upgrade(
                    id, category, CINDERFLASK, BOUND_CINDERFLASK,
                    VesselRecipes.is(Items.IRON_INGOT), VesselRecipes.is(Items.HONEYCOMB),
                    Cinderflask.BIND_RECIPE));

    public static final RecipeSerializer<VesselRecipes.Upgrade> WITCH_IRON_RECIPE =
            new SpecialRecipeSerializer<>((id, category) -> new VesselRecipes.Upgrade(
                    id, category, BOUND_CINDERFLASK, WITCH_IRON_CINDERFLASK,
                    VesselRecipes.is(Items.IRON_BLOCK), VesselRecipes.is(Items.WITHER_ROSE),
                    Cinderflask.WITCH_IRON_RECIPE));

    public static final RecipeSerializer<VesselRecipes.Upgrade> AETHERGLASS_RECIPE =
            new SpecialRecipeSerializer<>((id, category) -> new VesselRecipes.Upgrade(
                    id, category, WITCH_IRON_CINDERFLASK, AETHERGLASS_CINDERFLASK,
                    VesselRecipes.is(Items.AMETHYST_SHARD), VesselRecipes.is(Items.ECHO_SHARD),
                    Cinderflask.AETHERGLASS_RECIPE));

    /** Every vessel, narrowest first. */
    public static CinderflaskItem[] vessels() {
        return new CinderflaskItem[]{
                CINDERFLASK, BOUND_CINDERFLASK, WITCH_IRON_CINDERFLASK, AETHERGLASS_CINDERFLASK};
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CinderflaskConfig.load();

        Registry.register(Registries.ITEM, id("cinderflask"), CINDERFLASK);
        Registry.register(Registries.ITEM, id("bound_cinderflask"), BOUND_CINDERFLASK);
        Registry.register(Registries.ITEM, id("witch_iron_cinderflask"), WITCH_IRON_CINDERFLASK);
        Registry.register(Registries.ITEM, id("aetherglass_cinderflask"), AETHERGLASS_CINDERFLASK);
        Registry.register(Registries.ITEM, id("sump"), SUMP);
        Registry.register(Registries.ITEM, id("dregs"), DREGS);
        Registry.register(Registries.ITEM, id("sinter"), SINTER);

        Registry.register(Registries.SCREEN_HANDLER, id("cinderflask"), SCREEN_HANDLER);

        Registry.register(Registries.RECIPE_SERIALIZER, id("cork"), CORK_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("solera"), SOLERA_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("sinter"), SINTER_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("mend"), MEND_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("bind"), BIND_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("witch_iron"), WITCH_IRON_RECIPE);
        Registry.register(Registries.RECIPE_SERIALIZER, id("aetherglass"), AETHERGLASS_RECIPE);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            for (CinderflaskItem vessel : vessels()) {
                entries.add(vessel);
            }
            entries.add(SUMP);
            entries.add(DREGS);
            entries.add(SINTER);
        });

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BrewingRecipes());

        Draughts.register();
        Rebounds.register();
        Cracking.register();
        ConfigSync.register();
        PalateSync.register();
    }
}
