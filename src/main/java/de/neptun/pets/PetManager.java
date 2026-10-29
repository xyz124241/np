package de.neptun.pets;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Spawnt Pets und steuert ihr Verhalten (Folgen, Teleport, Angriff, OP-Check, Schutz). */
final class PetManager implements Listener {

    private final NeptunPets plugin;
    private final Set<UUID> pets = new LinkedHashSet<>();
    private int tickCounter;

    PetManager(NeptunPets plugin) {
        this.plugin = plugin;
    }

    void start() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (PetData.isPet(e)) {
                    pets.add(e.getUniqueId());
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    // ------------------------------------------------------------------ Spawnen

    Entity spawn(Player p, Draft d) {
        Class<?> raw = d.type.getEntityClass();
        if (raw == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Entity> cls = (Class<? extends Entity>) raw;

        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize().multiply(2);
        Location loc = p.getLocation().add(dir);

        final UUID owner = p.getUniqueId();
        Consumer<Entity> init = ent -> {
            PetData.write(ent, owner, d.flags);
            for (Map.Entry<String, Object> v : d.variants.entrySet()) {
                Variants.apply(ent, v.getKey(), v.getValue());
            }
            ent.setPersistent(true);
            if (ent instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                le.setCanPickupItems(false);
            }
            if (ent instanceof Ageable a) {
                if (d.babyTicks < 0) {
                    a.setAge(d.babyTicks);
                } else {
                    a.setAdult();
                }
                if ((d.flags & PetData.GROW) == 0) {
                    a.setAgeLock(true); // Alter eingefroren: wird nicht älter
                }
            }
            if (d.name != null) {
                ent.customName(d.name);
                ent.setCustomNameVisible(true);
            }
        };

        Entity e;
        try {
            e = loc.getWorld().spawn(loc, cls, init);
        } catch (Throwable t) {
            plugin.getLogger().warning("Pet konnte nicht gespawnt werden: " + t);
            return null;
        }
        if (e == null || !e.isValid()) {
            return null;
        }
        applyFlags(e);
        if (e instanceof LivingEntity le) {
            for (Map.Entry<org.bukkit.potion.PotionEffectType, int[]> fx : d.effects.entrySet()) {
                int[] v = fx.getValue();
                int ticks = v[1] < 0 ? PotionEffect.INFINITE_DURATION : v[1] * 20;
                le.addPotionEffect(new PotionEffect(fx.getKey(), ticks, v[0], false, true, true));
            }
        }
        pets.add(e.getUniqueId());
        return e;
    }

    /** Setzt Kollision und Bewegungsgeschwindigkeit passend zu den Einstellungen. */
    void applyFlags(Entity e) {
        if (!(e instanceof LivingEntity le)) {
            return;
        }
        le.setCollidable(PetData.has(e, PetData.PUSH));
        for (String name : new String[]{"movement_speed", "flying_speed"}) {
            Attribute attr = attribute(name);
            if (attr == null) {
                continue;
            }
            AttributeInstance ai = le.getAttribute(attr);
            if (ai == null) {
                continue;
            }
            NamespacedKey k = new NamespacedKey(plugin, "orig_" + name);
            Double orig = e.getPersistentDataContainer().get(k, PersistentDataType.DOUBLE);
            if (orig == null) {
                orig = ai.getBaseValue();
                e.getPersistentDataContainer().set(k, PersistentDataType.DOUBLE, orig);
            }
            ai.setBaseValue(PetData.has(e, PetData.MOVE) ? orig : 0.0D);
        }
    }

    private static Attribute attribute(String name) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        if (a == null) {
            a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
        }
        return a;
    }

    void kill(Entity e) {
        pets.remove(e.getUniqueId());
        if (e instanceof LivingEntity le) {
            le.setHealth(0.0D);
        }
        if (e.isValid()) {
            e.remove();
        }
    }

    // ------------------------------------------------------------------ Tick

    private void tick() {
        tickCounter++;
        boolean opCheck = tickCounter % 2 == 0; // jede Sekunde
        for (UUID id : new ArrayList<>(pets)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                pets.remove(id);
                continue;
            }
            UUID ownerId = PetData.owner(e);
            if (ownerId == null) {
                pets.remove(id);
                continue;
            }
            if (opCheck) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(ownerId);
                if (!op.isOp()) {
                    kill(e);
                    continue;
                }
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !(e instanceof Mob m)) {
                continue;
            }
            behave(m, owner);
        }
    }

    private void behave(Mob m, Player owner) {
        Location ol = owner.getLocation();
        Location pl = m.getLocation();
        boolean sameWorld = ol.getWorld() != null && ol.getWorld().equals(pl.getWorld());
        double d2 = sameWorld ? ol.distanceSquared(pl) : Double.MAX_VALUE;

        if (PetData.has(m, PetData.TELEPORT) && (!sameWorld || d2 > 24 * 24)) {
            m.teleport(ol);
            return;
        }
        if (PetData.has(m, PetData.FOLLOW) && PetData.has(m, PetData.MOVE) && sameWorld && d2 > 3 * 3) {
            m.getPathfinder().moveTo(ol, 1.2D);
        }

        if (PetData.has(m, PetData.AGGRESSIVE)) {
            LivingEntity t = m.getTarget();
            if (t == null || !t.isValid() || owner.getUniqueId().equals(t.getUniqueId())) {
                m.setTarget(nearestOther(m, owner));
            }
        } else if (m.getTarget() instanceof Player) {
            m.setTarget(null);
        }
    }

    private Player nearestOther(Mob m, Player owner) {
        Player best = null;
        double bestD = 16 * 16;
        for (Player p : m.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            double d = p.getLocation().distanceSquared(m.getLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!PetData.isPet(en)) {
                continue;
            }
            UUID owner = PetData.owner(en);
            if (owner == null || !Bukkit.getOfflinePlayer(owner).isOp()) {
                // Besitzer ist kein OP mehr -> das Pet stirbt
                Bukkit.getScheduler().runTask(plugin, () -> kill(en));
            } else {
                pets.add(en.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) {
            pets.remove(en.getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (PetData.isPet(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            pets.remove(e.getEntity().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!PetData.isPet(victim)) {
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p
                && !p.getUniqueId().equals(PetData.owner(victim))
                && !PetData.has(victim, PetData.HIT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!PetData.isPet(e.getEntity()) || !(e.getTarget() instanceof Player p)) {
            return;
        }
        if (p.getUniqueId().equals(PetData.owner(e.getEntity())) || !PetData.has(e.getEntity(), PetData.AGGRESSIVE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent e) {
        if (PetData.isPet(e.getEntity()) && !PetData.has(e.getEntity(), PetData.LEASH)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        if (!PetData.isPet(target)) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItem(e.getHand());
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        boolean isOwner = p.getUniqueId().equals(PetData.owner(target));

        if (hand.getType() == Material.LEAD) {
            if (!PetData.has(target, PetData.LEASH)) {
                e.setCancelled(true);
                return;
            }
            if (target instanceof Mob m && !m.isLeashed()) {
                // Auch Mobs, die vanilla nicht anleinbar sind (z. B. Monster), werden angeleint
                e.setCancelled(true);
                m.setLeashHolder(p);
                if (p.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
            }
            return;
        }
        if (!isOwner && !PetData.has(target, PetData.FEED)) {
            e.setCancelled(true);
        }
    }
}
