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
            ComplexRecipeJsonBuilder.create(Cinderflask.SOLERA_RECIPE).offerTo(exporter, "cinderflask:solera");
            ComplexRecipeJsonBuilder.create(Cinderflask.SINTER_RECIPE).offerTo(exporter, "cinderflask:sinter");
            ComplexRecipeJsonBuilder.create(Cinderflask.BIND_RECIPE).offerTo(exporter, "cinderflask:bind");
            ComplexRecipeJsonBuilder.create(Cinderflask.WITCH_IRON_RECIPE).offerTo(exporter, "cinderflask:witch_iron");
            ComplexRecipeJsonBuilder.create(Cinderflask.AETHERGLASS_RECIPE).offerTo(exporter, "cinderflask:aetherglass");
            ComplexRecipeJsonBuilder.create(Cinderflask.ALMANAC_RECIPE).offerTo(exporter, "cinderflask:almanac");

        }
    }

    private static class EnglishLanguage extends FabricLanguageProvider {
        EnglishLanguage(FabricDataOutput output) {
            super(output, "en_us");
        }

        @Override
        public void generateTranslations(TranslationBuilder builder) {
            builder.add(Cinderflask.CINDERFLASK, "Cinderflask");
            builder.add(Cinderflask.BOUND_CINDERFLASK, "Bound Cinderflask");
            builder.add(Cinderflask.WITCH_IRON_CINDERFLASK, "Witch-iron Cinderflask");
            builder.add(Cinderflask.AETHERGLASS_CINDERFLASK, "Aetherglass Cinderflask");
            builder.add(Cinderflask.SUMP, "Sump");
            builder.add(Cinderflask.DREGS, "Dregs");
            builder.add(Cinderflask.SINTER, "Sintered Flask");

            builder.add("cinderflask.tooltip.dregs", "The last of something. It remembers a little.");
            builder.add("cinderflask.tooltip.dregs_age", "Carries %s phases forward.");
            builder.add("cinderflask.tooltip.sinter", "Packed in sand. Fire it to mend it.");
            builder.add("cinderflask.tooltip.sinter_holds", "Holds %s");
            builder.add("cinderflask.tooltip.cracked", "Cracked. It is leaking.");
            builder.add("cinderflask.message.cracked", "The flask cracks.");

            builder.add("emi.category.cinderflask.brewing", "Brewing");
            builder.add("emi.category.cinderflask.tempering", "Tempering");
            builder.add("emi.category.cinderflask.landmarks", "Known Brews");
            builder.add("emi.category.cinderflask.vessel", "The Vessel");

            builder.add("cinderflask.emi.base", "Opens a brew");
            builder.add("cinderflask.emi.writes", "Writes %s %s");
            builder.add("cinderflask.emi.reach", "Reach +%s");
            builder.add("cinderflask.emi.body", "Body +%s");
            builder.add("cinderflask.emi.corruption", "Corruption +%s");
            builder.add("cinderflask.emi.clock", "Ages at %sx");
            builder.add("cinderflask.emi.aims", "Aims at %s");
            builder.add("cinderflask.emi.gives", "%s  ·  %s");

            builder.add("cinderflask.vessel.cork", "Seals a working brew. This is what starts its clock.");
            builder.add("cinderflask.vessel.upgrade", "Re-houses the brew in a wider vessel.");
            builder.add("cinderflask.vessel.solera", "Pours a working brew into a sealed one. Strength and age both blend by dose.");
            builder.add("cinderflask.vessel.sinter", "Packs a cracked flask in sand. Fire it in a furnace to mend it.");
            builder.add("cinderflask.vessel.almanac", "Writes a book against a flask. The flask is handed straight back.");
            builder.add("cinderflask.vessel.carries", "The mote, the seasoning, the temper and the name all survive.");

            builder.add("cinderflask.info.dregs", "What settles in a flask you drank dry. Open the next brew on it and that brew starts part-aged, remembering some of what came before.");
            builder.add("cinderflask.info.sump", "What a brew becomes when it is left far too long, or crammed past what the vessel can hold. It is not a drink. It is the cheap way into the corrupt half of the wheel.");
            builder.add("cinderflask.info.cracked", "A heavy hit while you are carrying something volatile cracks the flask. A crack breathes off the hot humours first, so a cracked flask drifts colder and slower as it empties — long weak doses of something quite unlike what you brewed. Some people keep one on purpose.");
            builder.add("cinderflask.info.ageing", "A sealed brew turns one step round the wheel every half day, and it does it in a chest as readily as in your hand. Choleric settles into melancholic, melancholic mellows into sanguine, sanguine sours into phlegmatic, and phlegmatic comes back round to choleric. Four steps is a full turn, and a full turn costs the brew some of its body.");

            builder.add("cinderflask.landmark.emberflask", "Emberflask");
            builder.add("cinderflask.landmark.deadmans_draught", "Deadman's Draught");
            builder.add("cinderflask.landmark.quickstep_draught", "Quickstep Draught");
            builder.add("cinderflask.landmark.ironroot_tonic", "Ironroot Tonic");
            builder.add("cinderflask.landmark.bramblewine", "Bramblewine");
            builder.add("cinderflask.landmark.deepdelve", "Deepdelve");
            builder.add("cinderflask.landmark.riposte_cordial", "Riposte Cordial");
            builder.add("cinderflask.landmark.honeyed_restorative", "Honeyed Restorative");
            builder.add("cinderflask.landmark.sap_sworn_mead", "Sap-Sworn Mead");
            builder.add("cinderflask.landmark.kelpwine", "Kelpwine");
            builder.add("cinderflask.landmark.nightcap", "Nightcap");
            builder.add("cinderflask.landmark.gravemead", "Gravemead");
            // The draught each landmark produces. Named apart from the brew, because "a Nightcap"
            // and "the Unseen Hand it leaves you with" are not the same noun.
            builder.add("effect.cinderflask.deadmans_draught", "Deadman's Vigour");
            builder.add("effect.cinderflask.ironroot_tonic", "Ironroot");
            builder.add("effect.cinderflask.sap_sworn_mead", "Sapsworn");
            builder.add("effect.cinderflask.nightcap", "Unseen Hand");
            builder.add("effect.cinderflask.bramblewine", "Bramble");
            builder.add("effect.cinderflask.deepdelve", "Deepdelve");
            builder.add("effect.cinderflask.kelpwine", "Kelpsworn");
            builder.add("effect.cinderflask.quickstep_draught", "Quickstep");
            builder.add("effect.cinderflask.emberflask", "Emberblood");
            builder.add("effect.cinderflask.riposte_cordial", "Riposte");
            builder.add("effect.cinderflask.honeyed_restorative", "Honeyed");
            builder.add("effect.cinderflask.gravemead", "Gravebound");

            // The four rebounds: one to a humour, each the crash from what that humour lent you.
            builder.add("effect.cinderflask.ashfall", "Ashfall");
            builder.add("effect.cinderflask.brittle", "Brittle");
            builder.add("effect.cinderflask.bloodless", "Bloodless");
            builder.add("effect.cinderflask.plain_sight", "Plain Sight");

            builder.add("cinderflask.role.alchemist", "Alchemist");
            builder.add("cinderflask.role.berserker", "Berserker");
            builder.add("cinderflask.role.skirmisher", "Skirmisher");
            builder.add("cinderflask.role.bulwark", "Bulwark");
            builder.add("cinderflask.role.retaliator", "Retaliator");
            builder.add("cinderflask.role.miner", "Miner");
            builder.add("cinderflask.role.duelist", "Duelist");
            builder.add("cinderflask.role.healer", "Healer");
            builder.add("cinderflask.role.reaver", "Reaver");
            builder.add("cinderflask.role.diver", "Diver");
            builder.add("cinderflask.role.assassin", "Assassin");
            builder.add("cinderflask.role.necromancer", "Necromancer");


            // The Almanac. One title and one body per node on the map; a test fails if a
            // node is added without both.
            builder.add(Cinderflask.ALMANAC, "Cinderflask Almanac");
            builder.add("cinderflask.tooltip.almanac", "A map of everything the glass can do.");
            builder.add("cinderflask.almanac.locked", "Drink more %s.");
            builder.add("cinderflask.almanac.hint", "Drag to pan, scroll to zoom, space to recentre.");
            builder.add("cinderflask.almanac.more", "Click to read the rest.");
            builder.add("cinderflask.almanac.region.vessel", "The Vessel");
            builder.add("cinderflask.almanac.region.brewing", "Brewing");
            builder.add("cinderflask.almanac.region.endings", "How a Brew Ends");
            builder.add("cinderflask.almanac.region.wheel", "The Wheel");
            builder.add("cinderflask.almanac.region.numbers", "What the Numbers Mean");
            builder.add("cinderflask.almanac.flask.title", "The Cinderflask");
            builder.add("cinderflask.almanac.flask.body", "Gold and three glass bottles. It holds one brew at a time and gives you one dose a sip. Everything else on this map is something you can do to it.");
            builder.add("cinderflask.almanac.mote.title", "Catching a Mote");
            builder.add("cinderflask.almanac.mote.body", "Right-click a living thing with an empty flask and a little of it stays in the glass. The mote is the small light in the bottom, and it takes that creature's own colour.");
            builder.add("cinderflask.almanac.motes_unique.title", "Rarer Motes");
            builder.add("cinderflask.almanac.motes_unique.body", "Every creature gives a different colour, drawn from the same palette the game uses for its spawn eggs. A flask that caught something unusual looks like nothing else on the server, and it keeps that light through every upgrade and every mending.");
            builder.add("cinderflask.almanac.temper.title", "Tempering");
            builder.add("cinderflask.almanac.temper.body", "Hold a flask against the right block and the glass takes a temper. A temper changes only how fast what is inside turns \u2014 never what it turns into.");
            builder.add("cinderflask.almanac.bound_cinderflask.title", "Bound Cinderflask");
            builder.add("cinderflask.almanac.bound_cinderflask.body", "Iron and honeycomb widen the glass. More room means more doses out of one brew, and the upgrade re-houses the vessel rather than replacing it: mote, seasoning, temper and any name it has earned all move across.");
            builder.add("cinderflask.almanac.witch_iron_cinderflask.title", "Witch-iron Cinderflask");
            builder.add("cinderflask.almanac.witch_iron_cinderflask.body", "An iron block and a wither rose. Wider again, and the first vessel that will hold a brew concentrated enough to be genuinely dangerous.");
            builder.add("cinderflask.almanac.aetherglass_cinderflask.title", "Aetherglass Cinderflask");
            builder.add("cinderflask.almanac.aetherglass_cinderflask.body", "Amethyst and an echo shard. The widest vessel, and the only one that lends reach on its own \u2014 which is what makes support brewing a late unlock rather than an early one.");
            builder.add("cinderflask.almanac.base.title", "Opening a Brew");
            builder.add("cinderflask.almanac.base.body", "Water, or a honey bottle. Nothing else will go into an empty flask until a base has, and until then there is nothing to write into.");
            builder.add("cinderflask.almanac.body.title", "Body");
            builder.add("cinderflask.almanac.body.body", "Some ingredients add bulk rather than character. Body is how much of the vessel you fill, and filling more of it buys you more doses of something milder.");
            builder.add("cinderflask.almanac.ingredients.title", "Writing the Brew");
            builder.add("cinderflask.almanac.ingredients.body", "Everything else you drop in writes into five numbers. Those numbers are the brew. There is no list of recipes to learn, only where you have landed.");
            builder.add("cinderflask.almanac.cork.title", "Corking");
            builder.add("cinderflask.almanac.cork.body", "A flask and a plank on a bench. Corking is what starts the clock, so until you do it you can take as long as you like composing one.");
            builder.add("cinderflask.almanac.sip.title", "Drinking");
            builder.add("cinderflask.almanac.sip.body", "One dose a sip, quick enough to do mid-fight. What you get depends on where the brew sits, not on what went into it.");
            builder.add("cinderflask.almanac.choleric.title", "Choleric");
            builder.add("cinderflask.almanac.choleric.body", "Hot and quick. Choleric is force: it drives how hard a brew hits, and it does not last.");
            builder.add("cinderflask.almanac.melancholic.title", "Melancholic");
            builder.add("cinderflask.almanac.melancholic.body", "Cold and patient. Melancholic is endurance: it drives how long a brew holds, and it is what keeps a volatile one from breaking the glass.");
            builder.add("cinderflask.almanac.sanguine.title", "Sanguine");
            builder.add("cinderflask.almanac.sanguine.body", "Sweet and vital. Sanguine mends, and it is strong without being sharp.");
            builder.add("cinderflask.almanac.phlegmatic.title", "Phlegmatic");
            builder.add("cinderflask.almanac.phlegmatic.body", "Dull and strange. Phlegmatic is the sour one \u2014 weak, long, and it does things none of the other three can.");
            builder.add("cinderflask.almanac.reach.title", "Reach");
            builder.add("cinderflask.almanac.reach.body", "Quintessence sits off the wheel and never rotates. It decides whether a brew happens to you or to the people around you, which is why the four brews that carry it are the four that help somebody else.");
            builder.add("cinderflask.almanac.corruption.title", "Corruption");
            builder.add("cinderflask.almanac.corruption.body", "Filth, bought with reagents or simply waited for. Every full turn of the wheel adds a little, and it is the cheap road into the half of the space nobody aims at on purpose.");
            builder.add("cinderflask.almanac.wheel.title", "The Wheel");
            builder.add("cinderflask.almanac.wheel.body", "A sealed brew turns one step every half day. Choleric settles into melancholic, melancholic mellows into sanguine, sanguine sours into phlegmatic, and phlegmatic comes back round to choleric. Four steps is a full turn. It is worked out from the moment you corked it, so a flask ages just as well forgotten in a chest.");
            builder.add("cinderflask.almanac.deadmans_draught.title", "Deadman's Draught");
            builder.add("cinderflask.almanac.deadmans_draught.body", "Pure choleric, and the berserker's drink. Deadman's Vigour: the emptier you are the harder you swing, and everything hits you harder while it lasts.");
            builder.add("cinderflask.almanac.ironroot_tonic.title", "Ironroot Tonic");
            builder.add("cinderflask.almanac.ironroot_tonic.body", "Pure melancholic, and the bulwark's. Ironroot takes a flat amount off every blow, which beats a swarm of small hits where a percentage never would.");
            builder.add("cinderflask.almanac.sap_sworn_mead.title", "Sap-Sworn Mead");
            builder.add("cinderflask.almanac.sap_sworn_mead.body", "Pure sanguine, and the reaver's. Sapsworn puts a share of whatever you take out of somebody back into you.");
            builder.add("cinderflask.almanac.nightcap.title", "Nightcap");
            builder.add("cinderflask.almanac.nightcap.body", "Pure phlegmatic, and the assassin's. The Unseen Hand pays out when you strike somebody who is not looking at you.");
            builder.add("cinderflask.almanac.bramblewine.title", "Bramblewine");
            builder.add("cinderflask.almanac.bramblewine.body", "Choleric leaning into melancholic: a wall that bites. Bramble returns a share of what you take to whoever was close enough to land it, though it cannot answer an arrow.");
            builder.add("cinderflask.almanac.deepdelve.title", "Deepdelve");
            builder.add("cinderflask.almanac.deepdelve.body", "Melancholic leaning into sanguine. Deepdelve makes the ground harmless \u2014 falls, suffocation, and anything that drops on you.");
            builder.add("cinderflask.almanac.kelpwine.title", "Kelpwine");
            builder.add("cinderflask.almanac.kelpwine.body", "Sanguine leaning into phlegmatic. Kelpsworn stops you needing the surface at all, and softens whatever hits you while you are in water.");
            builder.add("cinderflask.almanac.quickstep_draught.title", "Quickstep Draught");
            builder.add("cinderflask.almanac.quickstep_draught.body", "Phlegmatic leaning into choleric. Quickstep is speed and attack speed, and it only shelters you while you are actually running.");
            builder.add("cinderflask.almanac.emberflask.title", "Emberflask");
            builder.add("cinderflask.almanac.emberflask.body", "Choleric carried outward. Emberblood sets alight whatever you hit, and the fire is not yours to fear.");
            builder.add("cinderflask.almanac.riposte_cordial.title", "Riposte Cordial");
            builder.add("cinderflask.almanac.riposte_cordial.body", "Melancholic carried outward. Riposte staggers whoever hit you, however far off they were standing, and your answer inside the window lands heavier.");
            builder.add("cinderflask.almanac.honeyed_restorative.title", "Honeyed Restorative");
            builder.add("cinderflask.almanac.honeyed_restorative.body", "Sanguine carried outward. Honeyed mends you and everyone near you; without the reach it would only be a slower Regeneration.");
            builder.add("cinderflask.almanac.gravemead.title", "Gravemead");
            builder.add("cinderflask.almanac.gravemead.body", "Phlegmatic carried outward. Gravebound blunts the undead and knits you back together with every kill.");
            builder.add("cinderflask.almanac.amplifier.title", "Strength");
            builder.add("cinderflask.almanac.amplifier.body", "Driven by how concentrated the hot humours are, so it falls away as a brew settles.");
            builder.add("cinderflask.almanac.duration.title", "Duration");
            builder.add("cinderflask.almanac.duration.body", "Driven by the share of the brew that is patient rather than the amount of it, which is why duration climbs as strength drops instead of the two rising together.");
            builder.add("cinderflask.almanac.concentration.title", "Concentration");
            builder.add("cinderflask.almanac.concentration.body", "Essence per unit of vessel, and the central trade. Cram a lot into a small flask for a few ferocious doses, or spread it through a large one for many mild ones. Past a point the glass will not hold it at all.");
            builder.add("cinderflask.almanac.balance.title", "Balance");
            builder.add("cinderflask.almanac.balance.body", "How evenly the four are spread. A level brew hands you a spread of weak draughts and no crash at all; a lopsided one hands you a single strong draught and a bill.");
            builder.add("cinderflask.almanac.rebound.title", "The Rebound");
            builder.add("cinderflask.almanac.rebound.body", "The crash is the brew running backwards: it takes away exactly what its leading humour lent you. Choleric softens your blows, melancholic takes your wall, sanguine bleeds you, and phlegmatic makes being caught from behind hurt.");
            builder.add("cinderflask.almanac.ageing.title", "Ageing");
            builder.add("cinderflask.almanac.ageing.body", "Time turns the wheel and thins the body. A full turn costs some of what is in the flask, and enough turns leave nothing worth drinking.");
            builder.add("cinderflask.almanac.cracking.title", "Cracking");
            builder.add("cinderflask.almanac.cracking.body", "A heavy hit while you are carrying something volatile breaks the glass. A crack breathes off the hot humours first, so a cracked flask drifts colder and slower as it empties, giving long weak doses of something quite unlike what you brewed. Some people keep one on purpose.");
            builder.add("cinderflask.almanac.sinter.title", "Sintering");
            builder.add("cinderflask.almanac.sinter.body", "Pack a cracked flask in sand and fire it in a furnace. What comes out is the same vessel, with its mote, its seasoning, its temper and its name all intact \u2014 repairing an heirloom should not cost you the heirloom.");
            builder.add("cinderflask.almanac.dregs.title", "Dregs");
            builder.add("cinderflask.almanac.dregs.body", "What settles in a flask you drank dry. Open the next brew on them and it starts part-aged, remembering a little of what came before, and coloured by it.");
            builder.add("cinderflask.almanac.solera.title", "Solera");
            builder.add("cinderflask.almanac.solera.body", "Pour a working brew into a sealed one and both the vector and the age blend by dose. Decay outruns anything brewed fresh, so a deep phase at full strength is only reachable by keeping one running.");
            builder.add("cinderflask.almanac.sump.title", "Sump");
            builder.add("cinderflask.almanac.sump.body", "What a brew becomes when it is left far too long, or crammed past what the glass can hold. It is not a drink. It remembers what it used to be, which is what makes it a way into the corrupt half rather than simply rubbish.");
            builder.add("cinderflask.almanac.names.title", "Earned Names");
            builder.add("cinderflask.almanac.names.body", "A flask that has held enough brews takes a name from whatever it kept holding. The name follows the vessel through every upgrade and through the fire.");

            builder.add("cinderflask.name.format", "%s %s");
            builder.add("cinderflask.name.adjective.ember", "Old");
            builder.add("cinderflask.name.adjective.patient", "Patient");
            builder.add("cinderflask.name.adjective.sweet", "Sweet");
            builder.add("cinderflask.name.adjective.sour", "Bitter");
            builder.add("cinderflask.name.noun.cup", "Cup");
            builder.add("cinderflask.name.noun.flask", "Vessel");
            builder.add("cinderflask.name.noun.widow", "Widow");
            builder.add("cinderflask.name.noun.mother", "Mother");

            builder.add("cinderflask.tooltip.sump", "Thick, and it moves when you are not looking.");
            builder.add("cinderflask.message.uncorked", "It is not corked. Nothing is finished.");
            builder.add("cinderflask.message.poured", "You pour it out.");
            builder.add("cinderflask.message.washed", "You rinse the flask clean.");

            builder.add("cinderflask.humour.choleric", "choleric");
            builder.add("cinderflask.humour.melancholic", "melancholic");
            builder.add("cinderflask.humour.sanguine", "sanguine");
            builder.add("cinderflask.humour.phlegmatic", "phlegmatic");
            builder.add("cinderflask.humour.quintessence", "quintessence");

            builder.add("cinderflask.strength.faint", "a trace of");
            builder.add("cinderflask.strength.some", "some");
            builder.add("cinderflask.strength.strong", "strongly");
            builder.add("cinderflask.strength.overwhelming", "overwhelmingly");
            builder.add("cinderflask.readout.part", "%s %s");
            builder.add("cinderflask.readout.rough", "%s in the mouth.");
            builder.add("cinderflask.readout.draughts", "Gives %s");

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
