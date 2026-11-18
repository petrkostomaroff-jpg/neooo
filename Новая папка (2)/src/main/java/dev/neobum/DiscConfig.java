package dev.neobum;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscConfig {

    public static final class Snd {
        public final String key;
        public final float volume;
        public final float pitch;

        Snd(String key, float volume, float pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public boolean debug;
    public Material baseMaterial;
    public int cooldownTicks;
    public int catchCooldownTicks;
    public boolean indicatorEnabled;
    public double indicatorSeconds;
    public double waterSlowdown;
    public double hardMaxDistance;
    public int maxBounces;
    public double bounceSpeedMultiplier;
    public double bounceExtraRange;
    public double hitRadius;
    public boolean pierce;
    public boolean hitAgainOnReturn;
    public boolean hitPlayers;
    public double knockback;
    public double catchRadius;
    public int returnTimeout;
    public double spinDegrees;
    public float displayScale;
    public int costPerThrow;
    public int costPerHit;
    public List<String> lore = new ArrayList<>();
    public boolean particles;

    // fire
    public boolean fireEnabled;
    public int burnTicks;
    public int entityFireTicks;
    public boolean igniteTnt;
    public boolean igniteTntMinecart;
    public boolean igniteBlocks;
    public boolean extinguishInWater;
    public final Set<Material> fireSources = EnumSet.noneOf(Material.class);
    public Snd sndThrow, sndHit, sndRicochet, sndCatch, sndBreak;

    private final Map<String, DiscType> types = new LinkedHashMap<>();

    public void load(FileConfiguration c) {
        ConfigurationSection g = c.getConfigurationSection("general");
        if (g == null) g = c.createSection("general");

        debug = g.getBoolean("debug", false);
        Material m = Material.matchMaterial(g.getString("base-material", "ECHO_SHARD"));
        baseMaterial = (m == null || m.isAir()) ? Material.ECHO_SHARD : m;
        cooldownTicks = Math.max(0, g.getInt("cooldown-ticks", 20));
        catchCooldownTicks = Math.max(0, g.getInt("catch-cooldown-ticks", 20));
        indicatorEnabled = g.getBoolean("attack-indicator.enabled", true);
        indicatorSeconds = Math.max(0.05, g.getDouble("attack-indicator.seconds", 1.0));
        waterSlowdown = Math.max(1.0, g.getDouble("water-slowdown", 1.25));
        hardMaxDistance = Math.max(1.0, g.getDouble("hard-max-distance", 30.0));
        maxBounces = Math.max(0, g.getInt("max-bounces", 8));
        bounceSpeedMultiplier = Math.max(0.1, g.getDouble("bounce-speed-multiplier", 1.08));
        bounceExtraRange = Math.max(0.0, g.getDouble("bounce-extra-range", 3.0));
        hitRadius = Math.max(0.05, g.getDouble("hit-radius", 0.3));
        pierce = g.getBoolean("pierce-entities", true);
        hitAgainOnReturn = g.getBoolean("hit-again-on-return", true);
        hitPlayers = g.getBoolean("hit-players", true);
        knockback = g.getDouble("knockback", 0.35);
        catchRadius = Math.max(0.3, g.getDouble("catch-radius", 1.2));
        returnTimeout = Math.max(20, g.getInt("return-timeout-ticks", 200));
        spinDegrees = g.getDouble("spin-degrees-per-tick", 45.0);
        displayScale = (float) g.getDouble("display-scale", 1.0);
        costPerThrow = Math.max(0, g.getInt("durability.cost-per-throw", 0));
        costPerHit = Math.max(0, g.getInt("durability.cost-per-hit", 1));
        lore = new ArrayList<>(g.getStringList("lore"));
        particles = g.getBoolean("particles.enabled", true);

        fireEnabled = g.getBoolean("fire.enabled", true);
        burnTicks = Math.max(1, g.getInt("fire.burn-ticks", 100));
        entityFireTicks = Math.max(0, g.getInt("fire.entity-fire-ticks", 80));
        igniteTnt = g.getBoolean("fire.ignite-tnt", true);
        igniteTntMinecart = g.getBoolean("fire.ignite-tnt-minecart", true);
        igniteBlocks = g.getBoolean("fire.ignite-flammable-blocks", false);
        extinguishInWater = g.getBoolean("fire.extinguish-in-water", true);
        fireSources.clear();
        List<String> srcs = g.isList("fire.ignite-sources") ? g.getStringList("fire.ignite-sources")
                : List.of("FIRE", "SOUL_FIRE", "LAVA", "CAMPFIRE", "SOUL_CAMPFIRE");
        for (String n : srcs) {
            Material fm = Material.matchMaterial(n);
            if (fm != null) fireSources.add(fm);
        }

        sndThrow = snd(g, "throw", "minecraft:item.trident.throw", 1f, 1.3f);
        sndHit = snd(g, "hit", "minecraft:entity.player.attack.strong", 1f, 1f);
        sndRicochet = snd(g, "ricochet", "minecraft:item.trident.hit_ground", 1f, 1.2f);
        sndCatch = snd(g, "catch", "minecraft:item.trident.return", 0.8f, 1.4f);
        sndBreak = snd(g, "break", "minecraft:entity.item.break", 1f, 1f);

        types.clear();
        ConfigurationSection d = c.getConfigurationSection("discs");
        if (d != null) {
            for (String id : d.getKeys(false)) {
                ConfigurationSection s = d.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) continue;
                String key = id.toLowerCase();
                types.put(key, new DiscType(
                        key,
                        s.getString("display-name", key + " throwing disc"),
                        Math.max(1, s.getInt("durability", 250)),
                        s.getDouble("damage.base", 6.0),
                        s.getDouble("damage.per-second", 0.7),
                        s.getDouble("damage.per-bounce", 1.0),
                        Math.max(0.0, s.getDouble("damage.max-bonus", 4.0)),
                        Math.max(0.1, s.getDouble("speed", 1.5)),
                        Math.max(1.0, s.getDouble("range", 28.0)),
                        Math.max(0.1, s.getDouble("return-speed", 1.6))));
            }
        }
    }

    private Snd snd(ConfigurationSection g, String name, String key, float vol, float pitch) {
        String p = "sounds." + name + ".";
        return new Snd(g.getString(p + "key", key),
                (float) g.getDouble(p + "volume", vol),
                (float) g.getDouble(p + "pitch", pitch));
    }

    public DiscType get(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    public Map<String, DiscType> all() {
        return types;
    }
}
