package dev.neobum;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class DiscItems {

    private final NeoBum plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey speedKey;
    private final DecimalFormat fmt = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));

    public DiscItems(NeoBum plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "disc_type");
        this.speedKey = new NamespacedKey(plugin, "disc_attack_speed");
    }

    public ItemStack create(DiscType t) {
        DiscConfig cfg = plugin.cfg();
        ItemStack stack = new ItemStack(cfg.baseMaterial);
        ItemMeta meta = stack.getItemMeta();

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, t.id);
        meta.itemName(Component.translatable(t.translationKey(), t.displayName));
        meta.setItemModel(new NamespacedKey("neobum", t.modelName()));
        meta.setMaxStackSize(1);

        if (meta instanceof Damageable d) {
            d.setMaxDamage(t.durability);
            d.setDamage(0);
        }

        if (cfg.indicatorEnabled) {
            // Скорость атаки игрока по умолчанию 4.0. Нужная = 1 / seconds.
            double wanted = 1.0 / cfg.indicatorSeconds;
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    speedKey, wanted - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        for (String line : cfg.lore) {
            lore.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line.replace("%damage%", fmt.format(t.baseDamage)))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    public DiscType typeOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return plugin.cfg().get(id);
    }

    public boolean isDisc(ItemStack stack) {
        return typeOf(stack) != null;
    }

    /** Applies durability damage (respects Unbreaking). Returns true if the item broke. */
    public boolean damage(ItemStack stack, int amount) {
        if (amount <= 0) return false;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int unbreaking = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        int real = 0;
        for (int i = 0; i < amount; i++) {
            if (unbreaking <= 0 || ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) real++;
        }
        d.setDamage(d.getDamage() + real);
        stack.setItemMeta(d);
        int max = d.hasMaxDamage() ? d.getMaxDamage() : Integer.MAX_VALUE;
        return d.getDamage() >= max;
    }
}
