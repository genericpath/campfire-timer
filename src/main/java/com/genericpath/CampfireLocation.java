package com.genericpath;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;

@AllArgsConstructor
@Getter
@Setter
public class CampfireLocation {
    private GameObject fire;
    private final WorldPoint worldPoint;
    private long ticksSinceFireLit;
    private long tickFireStarted;
    private long ticksAddedTotal; // Change to using just a single number which increments / decrements later
    private int logsAddedTotal; // Use to help learn about length of fires based on num logs?
    private int playersTendingCount;
    private int numTimesDespawned;
    private HashMap<Player, Double> playersTending;

    void incrementPlayersTendingCount(Player player, double valueToAdd) {
        if (!playersTending.containsKey(player)) {
            System.out.println("reached here");
            playersTending.put(player, valueToAdd);
            playersTendingCount++;
        } else {
            System.out.println("didnt reach there");
        }

    }
}
