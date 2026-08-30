package dev.cinderflask.datagen;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.tag.CinderflaskTags;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Emits everything under {@code src/main/generated}. Regenerate with {@code ./gradlew runDatagen}.
 *
 * <p>Item models are hand-written instead: they need {@code predicate} overrides for the ember fill
 * states, which the vanilla model generators do not express.
 */
public class CinderflaskDataGenerator implements DataGeneratorEntrypoint {
    /** Thematically where a fuel item belongs: the advancement you get for smelting iron. */
    private static final Identifier SMELT_IRON = new Identifier("minecraft", "story/smelt_iron");

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(Recipes::new);
        pack.addProvider(SparkSourceTags::new);
        pack.addProvider(EmberDenyTags::new);
        pack.addProvider(EnglishLanguage::new);
        pack.addProvider(Advancements::new);
    }

    private static class Recipes extends FabricRecipeProvider {
        Recipes(FabricDataOutput output) {
            super(output);
        }

        @Override
        public void generate(Consumer<RecipeJsonProvider> exporter) {
            ShapedRecipeJsonBuilder.create(RecipeCategory.TOOLS, Cinderflask.EMPTY_CINDERFLASK)
                    .pattern(" G ")
                    .pattern("G G")
                    .pattern("BBB")
                    .input('G', Items.GOLD_INGOT)
                    .input('B', Items.GLASS_BOTTLE)
                    .criterion(hasItem(Items.GLASS_BOTTLE), conditionsFromItem(Items.GLASS_BOTTLE))
                    .criterion(hasItem(Items.GOLD_INGOT), conditionsFromItem(Items.GOLD_INGOT))
                    .offerTo(exporter);
        }
    }

    private static class SparkSourceTags extends FabricTagProvider.EntityTypeTagProvider {
        SparkSourceTags(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
            super(output, registries);
        }

        @Override
        protected void configure(RegistryWrapper.WrapperLookup lookup) {
            getOrCreateTagBuilder(CinderflaskTags.SPARK_SOURCE)
                    // Optional: Naturalist need not be installed for the tag to load.
                    .addOptional(new Identifier("naturalist", "firefly"))
                    .add(EntityType.BLAZE)
                    .add(EntityType.MAGMA_CUBE);
        }
    }

    private static class EmberDenyTags extends FabricTagProvider.ItemTagProvider {
        EmberDenyTags(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
            super(output, registries);
        }

        @Override
        protected void configure(RegistryWrapper.WrapperLookup lookup) {
            getOrCreateTagBuilder(CinderflaskTags.EMBER_DENY)
                    .add(Items.LAVA_BUCKET);
        }
    }

    private static class EnglishLanguage extends FabricLanguageProvider {
        EnglishLanguage(FabricDataOutput output) {
            super(output, "en_us");
        }

        @Override
        public void generateTranslations(TranslationBuilder builder) {
            builder.add(Cinderflask.EMPTY_CINDERFLASK, "Empty Cinderflask");
            builder.add(Cinderflask.CINDERFLASK, "Cinderflask");

            builder.add("cinderflask.tooltip.embers", "Embers: %s smelts");
            builder.add("cinderflask.tooltip.embers_ticks", "Embers: %s ticks");
            builder.add("cinderflask.tooltip.lore.lit", "The mote inside keeps the embers alive long after a fire would have gone out.");
            builder.add("cinderflask.tooltip.lore.unlit", "The mote has nothing to feed on. Fill the flask with anything that burns.");
            builder.add("cinderflask.tooltip.lore.cold", "Gold and blackened steel around cold glass. It holds nothing until something living lends it a spark.");
            builder.add("cinderflask.tooltip.spark_sources", "Sparked by:");
            builder.add("cinderflask.tooltip.spark_sources.none", "Nothing here can spark it.");
            builder.add("cinderflask.tooltip.worth", "Worth: %s smelts");
            builder.add("cinderflask.tooltip.worth_stack", "Stack: %s smelts");
            builder.add("cinderflask.tooltip.worth_ticks", "Worth: %s ticks");
            builder.add("cinderflask.tooltip.worth_ticks_stack", "Stack: %s ticks");

            builder.add("cinderflask.gui.embers", "Embers: %s smelts");
            builder.add("cinderflask.gui.embers_ticks", "%s ticks");
            builder.add("cinderflask.message.nothing_to_burn", "Nothing you are carrying will burn.");

            // EMI derives its category key as emi.category.<namespace>.<path>.
            builder.add("emi.category.cinderflask.sparking", "Sparking");
            builder.add("cinderflask.emi.sparking.hint",
                    "Right-click a living %s with the Empty Cinderflask.");

            builder.add("advancements.cinderflask.cold_and_hollow.title", "Cold and Hollow");
            builder.add("advancements.cinderflask.cold_and_hollow.description",
                    "Craft an Empty Cinderflask. The gold and glass are the easy part.");
            builder.add("advancements.cinderflask.everburn.title", "Everburn");
            builder.add("advancements.cinderflask.everburn.description",
                    "Seal a living spark inside a Cinderflask, and stop burning a whole coal for a single smelt.");
        }
    }

    private static class Advancements extends FabricAdvancementProvider {
        Advancements(FabricDataOutput output) {
            super(output);
        }

        @Override
        public void generateAdvancement(Consumer<Advancement> consumer) {
            // Advancement.Builder#build refuses a parent id it cannot resolve, so vanilla parents
            // have to be handed in as an object. This stub is never emitted; only its id is written.
            Advancement smeltIron = Advancement.Builder.create().build(SMELT_IRON);

            Advancement forged = Advancement.Builder.create()
                    .parent(smeltIron)
                    .display(
                            Cinderflask.EMPTY_CINDERFLASK,
                            Text.translatable("advancements.cinderflask.cold_and_hollow.title"),
                            Text.translatable("advancements.cinderflask.cold_and_hollow.description"),
                            null,
                            AdvancementFrame.TASK,
                            true, true, false)
                    .criterion("has_empty_cinderflask",
                            InventoryChangedCriterion.Conditions.items(Cinderflask.EMPTY_CINDERFLASK))
                    .build(consumer, "cinderflask:cold_and_hollow");

            Advancement.Builder.create()
                    .parent(forged)
                    .display(
                            Cinderflask.CINDERFLASK,
                            Text.translatable("advancements.cinderflask.everburn.title"),
                            Text.translatable("advancements.cinderflask.everburn.description"),
                            null,
                            AdvancementFrame.GOAL,
                            true, true, false)
                    .criterion("has_cinderflask",
                            InventoryChangedCriterion.Conditions.items(Cinderflask.CINDERFLASK))
                    .build(consumer, "cinderflask:everburn");
        }
    }
}
