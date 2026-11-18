package dev.neobum;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DiscCommand implements TabExecutor {

    private final NeoBum plugin;

    public DiscCommand(NeoBum plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/" + label + " <give|reload|list>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("neobum.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "NeoBum config reloaded.");
            }
            case "list" -> sender.sendMessage(ChatColor.GRAY + "Discs: " + String.join(", ", plugin.cfg().all().keySet()));
            case "give" -> {
                if (!sender.hasPermission("neobum.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "/" + label + " give <player> <disc> [amount]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                DiscType t = plugin.cfg().get(args[2]);
                if (t == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown disc. Use /" + label + " list");
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(36, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                        return true;
                    }
                }
                for (int i = 0; i < amount; i++) {
                    ItemStack s = plugin.items().create(t);
                    for (ItemStack left : target.getInventory().addItem(s).values()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), left);
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + t.id + " to " + target.getName());
            }
            default -> sender.sendMessage(ChatColor.GRAY + "/" + label + " <give|reload|list>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "reload", "list")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String id : plugin.cfg().all().keySet()) {
                if (id.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(id);
            }
        }
        return out;
    }
}
