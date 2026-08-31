package dev.cinderflask.datagen;

import dev.cinderflask.Cinderflask;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ComplexRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;

import java.util.function.Consumer;

/**
 * Emits everything under {@code src/main/generated}. Regenerate with {@code ./gradlew runDatagen}.
 *
 * <p>Item models are hand-written instead: they need {@code predicate} overrides for the fill states
 * and a separate tinted liquid layer, which the vanilla model generators do not express.
 */
public class CinderflaskDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(Recipes::new);
        pack.addProvider(BrewingProvider::new);
        pack.addProvider(EnglishLanguage::new);
    }

    private static class Recipes extends FabricRecipeProvider {
        Recipes(FabricDataOutput output) {
            super(output);
        }

        @Override
        public void generate(Consumer<RecipeJsonProvider> exporter) {
            ShapedRecipeJsonBuilder.create(RecipeCategory.BREWING, Cinderflask.CINDERFLASK)
                    .pattern(" G ")
                    .pattern("G G")
                    .pattern("BBB")
                    .input('G', Items.GOLD_INGOT)
                    .input('B', Items.GLASS_BOTTLE)
                    .criterion(hasItem(Items.GLASS_BOTTLE), conditionsFromItem(Items.GLASS_BOTTLE))
                    .criterion(hasItem(Items.GOLD_INGOT), conditionsFromItem(Items.GOLD_INGOT))
                    .offerTo(exporter);

            ComplexRecipeJsonBuilder.create(Cinderflask.CORK_RECIPE).offerTo(exporter, "cinderflask:cork");
        }
    }

    private static class EnglishLanguage extends FabricLanguageProvider {
        EnglishLanguage(FabricDataOutput output) {
            super(output, "en_us");
        }

        @Override
        public void generateTranslations(TranslationBuilder builder) {
            builder.add(Cinderflask.CINDERFLASK, "Cinderflask");
            builder.add(Cinderflask.SUMP, "Sump");

            builder.add("cinderflask.tooltip.sump", "Thick, and it moves when you are not looking.");
            builder.add("cinderflask.message.uncorked", "It is not corked. Nothing is finished.");
            builder.add("cinderflask.message.poured", "You pour it out.");
            builder.add("cinderflask.message.washed", "You rinse the flask clean.");

            builder.add("cinderflask.humour.choleric", "choleric");
            builder.add("cinderflask.humour.melancholic", "melancholic");
            builder.add("cinderflask.humour.sanguine", "sanguine");
            builder.add("cinderflask.humour.phlegmatic", "phlegmatic");

            builder.add("cinderflask.strength.faint", "a trace of");
            builder.add("cinderflask.strength.some", "some");
            builder.add("cinderflask.strength.strong", "strongly");
            builder.add("cinderflask.strength.overwhelming", "overwhelmingly");
            builder.add("cinderflask.readout.part", "%s %s");
            builder.add("cinderflask.readout.rough", "%s in the mouth.");

            builder.add("cinderflask.body.thin", "Thin");
            builder.add("cinderflask.body.even", "Even");
            builder.add("cinderflask.body.thick", "Thick");

            builder.add("cinderflask.impression.choleric", "It smells sharp, and it is not patient.");
            builder.add("cinderflask.impression.melancholic", "Cold, and it sits heavy in the glass.");
            builder.add("cinderflask.impression.sanguine", "Sweet. Something in it wants you well.");
            builder.add("cinderflask.impression.phlegmatic", "Sour, and it does not smell like much at all.");

            builder.add("cinderflask.state.empty", "Empty");
            builder.add("cinderflask.state.working", "Working");
            builder.add("cinderflask.state.sealed", "Sealed");
            builder.add("cinderflask.state.ruined", "Ruined");

            builder.add("cinderflask.tooltip.empty", "Empty. Something ought to go in it.");
            builder.add("cinderflask.tooltip.ruined", "Something has died in here.");
            builder.add("cinderflask.tooltip.doses", "%s doses");
            builder.add("cinderflask.tooltip.strength", "Strength %s for %ss");
            builder.add("cinderflask.tooltip.humours", "Cho %s  Mel %s  San %s  Phl %s");
            builder.add("cinderflask.tooltip.reach", "Reach %s");
            builder.add("cinderflask.tooltip.age", "phase %s, corruption %s");

            builder.add("cinderflask.message.empty", "The flask is empty.");

            builder.add("cinderflask.tooltip.untempered", "Untempered");
            builder.add("cinderflask.tooltip.no_mote", "No mote. Offer it something living.");
            builder.add("cinderflask.tooltip.mote", "A mote of %s  ·  %s brews");
            builder.add("cinderflask.tooltip.body", "Body +%s");
            builder.add("cinderflask.tooltip.writes", "Writes %s");

            builder.add("cinderflask.gui.doses", "%s doses");
            builder.add("cinderflask.gui.strength", "Strength %s  ·  %ss");
            builder.add("cinderflask.gui.nothing", "Nothing in it yet.");

            builder.add("cinderflask.temper.untempered", "Untempered");
            builder.add("cinderflask.temper.herbal", "Herbal temper");
            builder.add("cinderflask.temper.ember", "Ember temper");
            builder.add("cinderflask.temper.rime", "Rime temper");
            builder.add("cinderflask.temper.grave", "Grave temper");
            builder.add("cinderflask.temper.resonant", "Resonant temper");
            builder.add("cinderflask.temper.echo", "Echo temper");
        }
    }
}
