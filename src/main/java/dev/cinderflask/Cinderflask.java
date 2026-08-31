package dev.cinderflask;

import dev.cinderflask.brew.BrewingRecipes;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.item.SumpItem;
import dev.cinderflask.recipe.CorkRecipe;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.player.PalateSync;
import dev.cinderflask.screen.CinderflaskScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.resource.ResourceType;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Cinderflask implements ModInitializer {
    public static final String MOD_ID = "cinderflask";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cinderflask");

    /** Tier one. Phases 4 adds the Bound, Witch-iron and Aetherglass vessels above it. */
    public static final CinderflaskItem CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1), 8);

    /** What a brew becomes when it is left too long, and the way back into the corrupt half. */
    public static final SumpItem SUMP = new SumpItem(new FabricItemSettings());

    public static final ScreenHandlerType<CinderflaskScreenHandler> SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(CinderflaskScreenHandler::new);

    public static final RecipeSerializer<CorkRecipe> CORK_RECIPE =
            new SpecialRecipeSerializer<>(CorkRecipe::new);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CinderflaskConfig.load();

        Registry.register(Registries.ITEM, id("cinderflask"), CINDERFLASK);
        Registry.register(Registries.ITEM, id("sump"), SUMP);
        Registry.register(Registries.SCREEN_HANDLER, id("cinderflask"), SCREEN_HANDLER);
        Registry.register(Registries.RECIPE_SERIALIZER, id("cork"), CORK_RECIPE);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries ->
        {
            entries.add(CINDERFLASK);
            entries.add(SUMP);
        });

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BrewingRecipes());

        ConfigSync.register();
        PalateSync.register();
    }
}
