package dev.cinderflask;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.item.EmptyCinderflaskItem;
import dev.cinderflask.item.FuelTimes;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.screen.CinderflaskScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FuelRegistry;
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

    public static final EmptyCinderflaskItem EMPTY_CINDERFLASK =
            new EmptyCinderflaskItem(new FabricItemSettings().maxCount(16));

    public static final CinderflaskItem CINDERFLASK =
            new CinderflaskItem(new FabricItemSettings().maxCount(1));

    public static final ScreenHandlerType<CinderflaskScreenHandler> SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(CinderflaskScreenHandler::new);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CinderflaskConfig.load();

        Registry.register(Registries.ITEM, id("empty_cinderflask"), EMPTY_CINDERFLASK);
        Registry.register(Registries.ITEM, id("cinderflask"), CINDERFLASK);
        Registry.register(Registries.SCREEN_HANDLER, id("cinderflask"), SCREEN_HANDLER);

        // This is what makes a fuel slot accept the flask at all. Whether it burns is decided per
        // stack in the furnace mixin, so an empty flask can sit in the slot without lighting it.
        FuelRegistry.INSTANCE.add(CINDERFLASK, CinderflaskConfig.get().ticksPerOperation);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(EMPTY_CINDERFLASK);
            entries.add(CINDERFLASK);
        });

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> FuelTimes.invalidate());

        ConfigSync.register();
    }
}
