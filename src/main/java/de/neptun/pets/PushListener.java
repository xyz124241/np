package de.neptun.pets;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Eigene Klasse, damit ein Fehlen des Events (andere Server-Version) nichts anderes lahmlegt. */
final class PushListener implements Listener {

    @EventHandler
    public void onPush(EntityPushedByEntityAttackEvent e) {
        if (PetData.isPet(e.getEntity()) && !PetData.has(e.getEntity(), PetData.PUSH)) {
            e.setCancelled(true);
        }
    }
}
