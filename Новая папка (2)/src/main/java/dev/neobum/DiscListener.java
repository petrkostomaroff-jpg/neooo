package dev.neobum;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class DiscListener implements Listener {

    private final NeoBum plugin;

    public DiscListener(NeoBum plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() == null) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (e.useItemInHand() == Event.Result.DENY) return;

        ItemStack it = e.getItem();
        if (!plugin.items().isDisc(it)) return;

        Player p = e.getPlayer();
        plugin.debug(p.getName() + " right-clicked with a disc (" + a + ", " + e.getHand() + ")");
        if (!p.hasPermission("neobum.use")) return;

        // Клик по интерактивному блоку (сундук, дверь...) - обычное поведение, как у остальных предметов
        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) {
            return;
        }

        e.setCancelled(true);
        plugin.manager().throwDisc(p, e.getHand());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.manager().recall(e.getPlayer());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (plugin.items().isDisc(s)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
