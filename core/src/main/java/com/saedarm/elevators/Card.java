package com.saedarm.elevators;

import java.util.Random;

/** Every card in the mailbox deck. PASSIVE cards trigger on their own; the rest are played before you roll. */
public enum Card {
    // ---- normal: helps you ----
    SPARE_KEY(Tier.NORMAL, Use.PASSIVE, 3f, "Spare Key",
            "After you roll, you may move 1 more or 1 less."),
    DOORMAN_TIP(Tier.NORMAL, Use.SELF, 2f, "Doorman Tip",
            "Your next elevator ride goes one extra floor."),
    LAUNDRY_BAG(Tier.NORMAL, Use.PASSIVE, 2f, "Laundry Bag",
            "Your next chute only drops you halfway."),
    GRIPPY_SOCKS(Tier.NORMAL, Use.PASSIVE, .8f, "Grippy Socks",
            "The next chute you land on, you catch the edge and stay put."),
    TAKE_THE_FREIGHT(Tier.NORMAL, Use.SELF, 2f, "Take the Freight",
            "Instead of rolling, go straight up one floor (exactly 10 squares)."),
    FIRE_EXTINGUISHER(Tier.NORMAL, Use.PASSIVE, 2f, "Fire Extinguisher",
            "Blocks the next fire, bomb, piano, flood or blast that would hit you."),
    SUBLET(Tier.NORMAL, Use.TENANT, 1.5f, "Sublet",
            "Swap places with a tenant on your floor or one floor away."),
    NOISE_COMPLAINT(Tier.NORMAL, Use.TENANT, 1.5f, "Noise Complaint",
            "That tenant has to take the stairs on their next turn."),
    PACKAGE_THEFT(Tier.NORMAL, Use.TENANT, 1.5f, "Package Theft",
            "Steal a random card from another tenant."),

    // ---- chaos: hurts an area ----
    CHERRY_BOMB(Tier.CHAOS, Use.TENANT, 2f, "Cherry Bomb",
            "Blows a hole in their square for 3 rounds. They fall a floor, and so does anyone who lands on it."),
    GREASE_FIRE(Tier.CHAOS, Use.TENANT, 2f, "Grease Fire",
            "Sets their floor on fire for 2 rounds. Anyone who ends a turn there drops a floor."),
    PIANO_DELIVERY(Tier.CHAOS, Use.TENANT, 2f, "Piano Delivery",
            "Drops a piano down their column. Everyone stacked in it falls a floor. Including you."),
    SNAPPED_CABLE(Tier.CHAOS, Use.SELF, 1.5f, "Snapped Cable",
            "Elevators are broken for 2 rounds. Anyone standing at the top of a shaft drops to the bottom."),
    WATER_TOWER_BURST(Tier.CHAOS, Use.SELF, 1.5f, "Water Tower Burst",
            "The tank cracks. Everyone on floors 9 and 10 is washed down a floor."),
    MICROWAVE_INCIDENT(Tier.CHAOS, Use.SELF, 1.5f, "Microwave Incident",
            "Popcorn everywhere. Everyone else on your floor is knocked back 1 to 3 squares."),

    // ---- insane: rare and ridiculous ----
    RACCOON_INFESTATION(Tier.INSANE, Use.SELF, 1f, "Raccoon Infestation",
            "Raccoons move into the chute ahead of you for 2 rounds. Land at its bottom and they fling you up it."),
    ALIEN_ABDUCTION(Tier.INSANE, Use.SELF, 1f, "Alien Abduction",
            "Instead of rolling, get beamed to a random square on floors 8 to 10. It might be a chute."),
    EARTHQUAKE(Tier.INSANE, Use.SELF, 1f, "Earthquake",
            "Every tenant, you included, shakes 1 to 6 squares up or down."),
    TIME_WARP(Tier.INSANE, Use.SELF, 1f, "Time Warp",
            "Everyone goes back to where they were one round ago."),
    BAD_WIFI(Tier.INSANE, Use.SELF, .7f, "Bad Wi-Fi",
            "Everyone else loses their next turn resetting the router.");

    public enum Tier { NORMAL, CHAOS, INSANE }

    /** PASSIVE cards can't be played; SELF needs no target; TENANT needs one. */
    public enum Use { PASSIVE, SELF, TENANT }

    public final Tier tier;
    public final Use use;
    public final float weight;
    public final String title, text;

    Card(Tier tier, Use use, float weight, String title, String text) {
        this.tier = tier; this.use = use; this.weight = weight; this.title = title; this.text = text;
    }

    /** 55% normal, 33% chaos, 12% insane, then weighted within the tier. */
    public static Card draw(Random rng) {
        float roll = rng.nextFloat();
        Tier tier = roll < .55f ? Tier.NORMAL : roll < .88f ? Tier.CHAOS : Tier.INSANE;
        float total = 0;
        for (Card c : values()) if (c.tier == tier) total += c.weight;
        float pick = rng.nextFloat() * total;
        Card last = null;
        for (Card c : values()) {
            if (c.tier != tier) continue;
            last = c;
            pick -= c.weight;
            if (pick <= 0) return c;
        }
        return last;
    }
}
