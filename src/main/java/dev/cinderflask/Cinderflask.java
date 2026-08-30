package dev.cinderflask;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.screen.CinderflaskScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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

    public static final ScreenHandlerType<CinderflaskScreenHandler> SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(CinderflaskScreenHandler::new);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CinderflaskConfig.load();

        Registry.register(Registries.ITEM, id("cinderflask"), CINDERFLASK);
        Registry.register(Registries.SCREEN_HANDLER, id("cinderflask"), SCREEN_HANDLER);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries ->
                entries.add(CINDERFLASK));

        ConfigSync.register();
    }
}
