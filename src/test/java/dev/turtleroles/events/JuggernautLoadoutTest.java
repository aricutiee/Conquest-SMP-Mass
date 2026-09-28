package dev.turtleroles.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JuggernautLoadoutTest {
    @Test void armorContainsSpecifiedEnchantsAndNoThorns() {
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            var enchants = JuggernautLoadout.armorSpecs(piece);
            assertEquals(piece.equals("helmet")||piece.equals("boots")?6:5, enchants.get("protection"));
            assertEquals(4, enchants.get("unbreaking"));
            assertEquals(1, enchants.get("mending"));
            assertFalse(enchants.containsKey("thorns"));
        }
        assertEquals(3, JuggernautLoadout.armorSpecs("helmet").get("respiration"));
        assertEquals(1, JuggernautLoadout.armorSpecs("helmet").get("aqua_affinity"));
        assertEquals(3, JuggernautLoadout.armorSpecs("leggings").get("swift_sneak"));
        assertEquals(4, JuggernautLoadout.armorSpecs("boots").get("feather_falling"));
        assertEquals(3, JuggernautLoadout.armorSpecs("boots").get("depth_strider"));
        assertEquals(3, JuggernautLoadout.armorSpecs("boots").get("soul_speed"));
    }

    @Test void toolsAndWeaponsMatchRequirements() {
        var sword = JuggernautLoadout.swordSpecs();
        assertEquals(5, sword.get("sharpness"));
        assertEquals(2, sword.get("fire_aspect"));
        assertEquals(3, sword.get("looting"));
        assertEquals(3, sword.get("sweeping_edge"));
        assertFalse(sword.containsKey("knockback"));
        assertEquals(5, JuggernautLoadout.axeSpecs().get("sharpness"));
        assertEquals(5, JuggernautLoadout.axeSpecs().get("efficiency"));
        assertEquals(3, JuggernautLoadout.axeSpecs().get("fortune"));
        assertEquals(5, JuggernautLoadout.pickSpecs().get("efficiency"));
        assertEquals(3, JuggernautLoadout.pickSpecs().get("fortune"));
        assertEquals(3, JuggernautLoadout.spearCoreSpecs().get("lunge"));
    }

    @Test void strengthPotionTargetsEightMinutesOnlyForStrengthTwo() {
        assertEquals(9600, StrengthPotions.DURATION_TICKS);
        assertTrue(StrengthPotions.needsEightMinutes(true, -1, -1));
        assertTrue(StrengthPotions.needsEightMinutes(false, 1, 1800));
        assertFalse(StrengthPotions.needsEightMinutes(false, 1, 9600));
        assertFalse(StrengthPotions.needsEightMinutes(false, 0, 1800));
    }
}
