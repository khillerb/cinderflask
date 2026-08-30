package dev.cinderflask.tag;

import dev.cinderflask.Cinderflask;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class CinderflaskTags {
    /** Mobs an Empty Cinderflask can take a spark from. Datapack-tunable. */
    public static final TagKey<EntityType<?>> SPARK_SOURCE =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Cinderflask.id("spark_source"));

    /** Fuels the flask refuses to swallow, even though a furnace would burn them. */
    public static final TagKey<Item> EMBER_DENY =
            TagKey.of(RegistryKeys.ITEM, Cinderflask.id("ember_deny"));

    private CinderflaskTags() {
    }
}
