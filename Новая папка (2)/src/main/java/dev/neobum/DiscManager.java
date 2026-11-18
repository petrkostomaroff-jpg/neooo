package dev.neobum;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DiscManager {

    private final NeoBum plugin;
    private final List<ThrownDisc> active = new ArrayList<>();

    public DiscManager(NeoBum plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        Iterator<ThrownDisc> it = active.iterator();
        while (it.hasNext()) {
            ThrownDisc d = it.next();
            boolean finished;
            try {
                finished = d.tick();
            } catch (Exception ex) {
                plugin.getLogger().warning("Disc tick error: " + ex);
                d.dropAtDisc();
                finished = true;
            }
            if (finished) it.remove();
        }
    }

    public void throwDisc(Player p, EquipmentSlot hand) {
        DiscConfig cfg = plugin.cfg();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.hasCooldown(cfg.baseMaterial)) return;

        PlayerInventory inv = p.getInventory();
        boolean off = hand == EquipmentSlot.OFF_HAND;
        ItemStack held = off ? inv.getItemInOffHand() : inv.getItemInMainHand();
        DiscType type = plugin.items().typeOf(held);
        if (type == null) return;

        ItemStack single = held.clone();
        single.setAmount(1);
        int slot = off ? 40 : inv.getHeldItemSlot();

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Location start = eye.clone().add(dir.clone().multiply(0.4)).subtract(0, 0.2, 0);

        // Сначала создаём полёт: если что-то сломается, предмет остаётся в руке.
        ThrownDisc disc;
        try {
            disc = new ThrownDisc(plugin, p, type, single, off, slot, start, dir);
        } catch (Throwable ex) {
            plugin.getLogger().severe("Could not start disc flight: " + ex);
            ex.printStackTrace();
            return;
        }

        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else if (off) {
            inv.setItemInOffHand(null);
        } else {
            inv.setItemInMainHand(null);
        }
        active.add(disc);
        plugin.debug("Thrown " + type.id + " by " + p.getName());

        if (off) p.swingOffHand();
        else p.swingMainHand();
        p.getWorld().playSound(p.getLocation(), cfg.sndThrow.key, SoundCategory.PLAYERS,
                cfg.sndThrow.volume, cfg.sndThrow.pitch);
        if (cfg.cooldownTicks > 0) p.setCooldown(cfg.baseMaterial, cfg.cooldownTicks);
    }

    /** Returns all discs of this player immediately (quit, etc). */
    public void recall(Player p) {
        Iterator<ThrownDisc> it = active.iterator();
        while (it.hasNext()) {
            ThrownDisc d = it.next();
            if (d.owner().equals(p.getUniqueId())) {
                d.recall(p);
                it.remove();
            }
        }
    }

    public void shutdown() {
        for (ThrownDisc d : active) {
            Player p = plugin.getServer().getPlayer(d.owner());
            if (p != null && p.isOnline()) d.recall(p);
            else d.dropAtDisc();
        }
        active.clear();
    }
}
