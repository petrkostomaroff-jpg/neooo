package dev.neobum;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One flying disc: outbound flight, ricochets, boomerang return, catch. */
public final class ThrownDisc {

    private enum Phase { OUT, BACK }

    private final NeoBum plugin;
    private final DiscConfig cfg;
    private final DiscType type;
    private final UUID owner;
    private final ItemStack item;
    private final boolean offHand;
    private final int slot;

    private final ItemDisplay display;
    private final Location pos;
    private Vector dir;

    private Phase phase = Phase.OUT;
    private double rangeLeft;
    private double travelled = 0;
    private double speedMul = 1.0;
    private int ticks = 0;
    private int backTicks = 0;
    private int bounces = 0;
    private int hits = 0;
    private double spin = 0;
    private boolean done = false;
    private boolean burning = false;
    private int burnLeft = 0;
    private Location prev;
    private final Set<UUID> hitSet = new HashSet<>();

    public ThrownDisc(NeoBum plugin, Player owner, DiscType type, ItemStack item, boolean offHand, int slot,
                      Location start, Vector dir) {
        this.plugin = plugin;
        this.cfg = plugin.cfg();
        this.type = type;
        this.owner = owner.getUniqueId();
        this.item = item;
        this.offHand = offHand;
        this.slot = slot;
        this.pos = start.clone();
        this.dir = dir.clone().normalize();
        this.rangeLeft = Math.min(type.range, cfg.hardMaxDistance);

        World w = start.getWorld();
        ItemStack shown = item.clone();
        this.display = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            Compat.call(d, "setTeleportDuration", int.class, 1);
            d.setInterpolationDuration(1);
            d.setInterpolationDelay(0);
            d.setTransformation(transform());
        });
    }

    public UUID owner() {
        return owner;
    }

    public boolean isDone() {
        return done;
    }

    // ------------------------------------------------------------------ tick

    /** @return true when finished and should be removed */
    public boolean tick() {
        if (done) return true;
        Player p = plugin.getServer().getPlayer(owner);
        World w = pos.getWorld();
        ticks++;

        if (display == null || !display.isValid()) {
            dropAtDisc();
            return true;
        }
        if (p == null || !p.isOnline() || p.isDead() || !p.getWorld().equals(w)) {
            dropAtDisc();
            return true;
        }
        if (!w.isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
            if (phase == Phase.OUT) startReturn();
            else {
                dropAtDisc();
                return true;
            }
        }

        prev = pos.clone();
        boolean water = inWater();
        if (burning && water && cfg.extinguishInWater) extinguish(true);
        double factor = water ? 1.0 / cfg.waterSlowdown : 1.0;
        if (water && cfg.particles && ticks % 2 == 0) {
            w.spawnParticle(Particle.BUBBLE, pos, 2, 0.1, 0.1, 0.1, 0.0);
        }

        if (phase == Phase.OUT) tickOut(p, factor);
        else tickBack(p, factor);

        if (!done) {
            fireStep(p);
            render();
        }
        return done;
    }

    private void tickOut(Player p, double factor) {
        World w = pos.getWorld();
        double remaining = Math.min(type.speed * speedMul * factor, rangeLeft);
        int guard = 0;

        while (remaining > 1.0E-4 && guard++ < 10 && phase == Phase.OUT) {
            Vector d = dir.clone().normalize();
            RayTraceResult blockHit = w.rayTraceBlocks(pos, d, remaining, FluidCollisionMode.NEVER, true);
            RayTraceResult entHit = w.rayTraceEntities(pos, d, remaining, cfg.hitRadius, e -> canHit(e, p));

            double bd = blockHit != null ? blockHit.getHitPosition().distance(pos.toVector()) : Double.MAX_VALUE;
            double ed = entHit != null ? entHit.getHitPosition().distance(pos.toVector()) : Double.MAX_VALUE;

            if (entHit != null && ed <= bd) {
                advance(ed);
                remaining -= ed;
                hit((LivingEntity) entHit.getHitEntity(), p);
                if (!cfg.pierce) {
                    startReturn();
                    return;
                }
            } else if (blockHit != null) {
                advance(bd);
                remaining -= bd;
                bounce(blockHit);
                if (bounces > cfg.maxBounces) {
                    startReturn();
                    return;
                }
            } else {
                advance(remaining);
                remaining = 0;
            }
        }
        if (rangeLeft <= 1.0E-3) startReturn();
    }

    private void tickBack(Player p, double factor) {
        World w = pos.getWorld();
        backTicks++;
        Location target = p.getLocation().add(0, p.getHeight() * 0.6, 0);
        Vector to = target.toVector().subtract(pos.toVector());
        double dist = to.length();
        double step = type.returnSpeed * factor;

        if (dist <= Math.max(step, cfg.catchRadius) || backTicks > cfg.returnTimeout) {
            catchIt(p);
            return;
        }
        dir = to.multiply(1.0 / dist);

        double remaining = step;
        int guard = 0;
        while (remaining > 1.0E-4 && guard++ < 6) {
            RayTraceResult r = w.rayTraceEntities(pos, dir, remaining, cfg.hitRadius, e -> canHit(e, p));
            if (r == null) break;
            double ed = r.getHitPosition().distance(pos.toVector());
            pos.add(dir.clone().multiply(ed));
            remaining -= ed;
            hit((LivingEntity) r.getHitEntity(), p);
        }
        if (remaining > 0) pos.add(dir.clone().multiply(remaining));
        travelled += step;
    }

    // ------------------------------------------------------------- mechanics

    private void advance(double dist) {
        pos.add(dir.clone().normalize().multiply(dist));
        rangeLeft -= dist;
        travelled += dist;
    }

    private void bounce(RayTraceResult hitResult) {
        BlockFace face = hitResult.getHitBlockFace();
        Vector n = face != null ? face.getDirection() : dir.clone().multiply(-1).normalize();
        double dot = dir.dot(n);
        if (dot < 0) {
            dir = dir.clone().subtract(n.clone().multiply(2 * dot)).normalize();
        }
        pos.add(n.clone().multiply(0.06));
        bounces++;
        speedMul *= cfg.bounceSpeedMultiplier;
        double cap = Math.max(0, cfg.hardMaxDistance - travelled);
        rangeLeft = Math.min(rangeLeft + cfg.bounceExtraRange, cap);

        World w = pos.getWorld();
        play(cfg.sndRicochet);
        if (cfg.particles) w.spawnParticle(Particle.CRIT, pos, 5, 0.1, 0.1, 0.1, 0.1);

        if (burning && cfg.fireEnabled && cfg.igniteBlocks && face != null && hitResult.getHitBlock() != null) {
            Block hb = hitResult.getHitBlock();
            Block fb = hb.getRelative(face);
            Player p = plugin.getServer().getPlayer(owner);
            if (fb.getType().isAir() && hb.getType().isFlammable() && allowIgnite(fb, p)) {
                fb.setType(Material.FIRE);
            }
        }
    }

    private void startReturn() {
        if (phase == Phase.BACK) return;
        phase = Phase.BACK;
        backTicks = 0;
        if (cfg.hitAgainOnReturn) hitSet.clear();
    }

    private boolean canHit(Entity e, Player thrower) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.getUniqueId().equals(owner)) return false;
        if (e instanceof ArmorStand) return false;
        if (le.isDead() || le.isInvulnerable()) return false;
        if (hitSet.contains(e.getUniqueId())) return false;
        if (e instanceof Player t) {
            if (!cfg.hitPlayers || !t.getWorld().getPVP()) return false;
            GameMode gm = t.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
        }
        return true;
    }

    private void hit(LivingEntity target, Player thrower) {
        hitSet.add(target.getUniqueId());
        hits++;
        double dmg = type.damage(ticks, bounces);
        target.damage(dmg, thrower);
        if (cfg.fireEnabled) {
            if (burning && cfg.entityFireTicks > 0) {
                target.setFireTicks(Math.max(target.getFireTicks(), cfg.entityFireTicks));
            } else if (!burning && target.getFireTicks() > 0) {
                ignite();
            }
        }

        Vector push = dir.clone().setY(0);
        if (push.lengthSquared() > 1.0E-6 && cfg.knockback > 0) {
            push.normalize().multiply(cfg.knockback).setY(0.1);
            target.setVelocity(target.getVelocity().add(push));
        }
        play(cfg.sndHit);
        if (cfg.particles) {
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                    8, 0.2, 0.2, 0.2, 0.2);
        }
    }

    private boolean inWater() {
        Block b = pos.getBlock();
        if (b.getType() == Material.WATER || b.getType() == Material.BUBBLE_COLUMN) return true;
        return b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
    }

    private void play(DiscConfig.Snd s) {
        pos.getWorld().playSound(pos, s.key, SoundCategory.PLAYERS, s.volume, s.pitch);
    }

    // ------------------------------------------------------------------ fire

    private void ignite() {
        boolean was = burning;
        burning = true;
        burnLeft = cfg.burnTicks;
        if (!was) {
            pos.getWorld().playSound(pos, "minecraft:item.flintandsteel.use", SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
    }

    private void extinguish(boolean sound) {
        burning = false;
        burnLeft = 0;
        if (sound) {
            pos.getWorld().playSound(pos, "minecraft:block.fire.extinguish", SoundCategory.PLAYERS, 0.6f, 1.2f);
            if (cfg.particles) pos.getWorld().spawnParticle(Particle.SMOKE, pos, 6, 0.15, 0.15, 0.15, 0.02);
        }
    }

    private boolean isFireSource(Block b) {
        Material t = b.getType();
        if (!cfg.fireSources.contains(t)) return false;
        if (t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE) {
            return b.getBlockData() instanceof Lightable l && l.isLit();
        }
        return true;
    }

    private boolean allowIgnite(Block b, Player p) {
        BlockIgniteEvent ev = new BlockIgniteEvent(b, BlockIgniteEvent.IgniteCause.ARROW, p);
        plugin.getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** Called once per tick after movement: catching fire, igniting TNT / minecarts. */
    private void fireStep(Player p) {
        if (!cfg.fireEnabled) return;
        World w = pos.getWorld();
        Vector a = prev.toVector();
        Vector b = pos.toVector();
        double len = a.distance(b);
        int n = Math.max(1, (int) Math.ceil(len / 0.5));
        boolean waterNow = inWater();

        for (int i = 0; i <= n; i++) {
            Vector v = a.clone().add(b.clone().subtract(a).multiply(i / (double) n));
            Block c = w.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ());
            if (isFireSource(c) && !(waterNow && cfg.extinguishInWater)) ignite();
            if (burning && cfg.igniteTnt) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block t = c.getRelative(dx, dy, dz);
                            if (t.getType() == Material.TNT) lightTnt(t, p);
                        }
                    }
                }
            }
        }
        if (!burning) return;

        if (cfg.igniteTntMinecart) {
            Location mid = pos.clone().add(prev).multiply(0.5);
            double r = len / 2.0 + 1.0;
            for (Entity e : w.getNearbyEntities(mid, r, r, r)) {
                if (e.getType() == EntityType.TNT_MINECART) {
                    Compat.ignitePrimed(e);
                }
            }
        }
        if (cfg.particles) {
            w.spawnParticle(Particle.FLAME, pos, 2, 0.08, 0.08, 0.08, 0.01);
        }
        if (--burnLeft <= 0) extinguish(false);
    }

    private void lightTnt(Block t, Player p) {
        if (!allowIgnite(t, p)) return;
        Location center = t.getLocation().add(0.5, 0.0, 0.5);
        t.setType(Material.AIR);
        t.getWorld().spawn(center, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(80);
            tnt.setSource(p);
        });
        t.getWorld().playSound(center, "minecraft:entity.tnt.primed", SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    // ---------------------------------------------------------- end of flight

    private void catchIt(Player p) {
        int cost = hits * cfg.costPerHit + cfg.costPerThrow;
        boolean broke = cost > 0 && plugin.items().damage(item, cost);
        removeDisplay();
        done = true;
        if (broke) {
            p.getWorld().playSound(p.getLocation(), cfg.sndBreak.key, SoundCategory.PLAYERS,
                    cfg.sndBreak.volume, cfg.sndBreak.pitch);
            return;
        }
        giveBack(p);
        p.getWorld().playSound(p.getLocation(), cfg.sndCatch.key, SoundCategory.PLAYERS,
                cfg.sndCatch.volume, cfg.sndCatch.pitch);
        if (cfg.catchCooldownTicks > 0) p.setCooldown(cfg.baseMaterial, cfg.catchCooldownTicks);
    }

    /** Called on quit/shutdown: returns the item right away without effects. */
    public void recall(Player p) {
        if (done) return;
        int cost = hits * cfg.costPerHit + cfg.costPerThrow;
        boolean broke = cost > 0 && plugin.items().damage(item, cost);
        removeDisplay();
        done = true;
        if (!broke) giveBack(p);
    }

    private void giveBack(Player p) {
        PlayerInventory inv = p.getInventory();
        if (offHand) {
            ItemStack cur = inv.getItemInOffHand();
            if (cur == null || cur.getType().isAir()) {
                inv.setItemInOffHand(item);
                return;
            }
        } else {
            ItemStack cur = inv.getItem(slot);
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(slot, item);
                return;
            }
        }
        Map<Integer, ItemStack> left = inv.addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    public void dropAtDisc() {
        if (done) return;
        done = true;
        int cost = hits * cfg.costPerHit + cfg.costPerThrow;
        boolean broke = cost > 0 && plugin.items().damage(item, cost);
        World w = pos.getWorld();
        removeDisplay();
        if (!broke && w != null) w.dropItemNaturally(pos, item);
    }

    private void removeDisplay() {
        if (display != null && display.isValid()) display.remove();
    }

    // ---------------------------------------------------------------- render

    private Transformation transform() {
        float s = cfg.displayScale;
        Quaternionf q = new Quaternionf()
                .rotateY((float) Math.toRadians(spin))
                .rotateX((float) (-Math.PI / 2.0));
        return new Transformation(new Vector3f(), q, new Vector3f(s, s, s), new Quaternionf());
    }

    private void render() {
        spin += cfg.spinDegrees;
        display.teleport(pos);
        display.setTransformation(transform());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
    }
}
