package dev.neobum;

public final class DiscType {
    public final String id;
    public final String displayName;
    public final int durability;
    public final double baseDamage;
    public final double perSecond;
    public final double perBounce;
    public final double maxBonus;
    public final double speed;
    public final double range;
    public final double returnSpeed;

    public DiscType(String id, String displayName, int durability, double baseDamage, double perSecond,
                    double perBounce, double maxBonus, double speed, double range, double returnSpeed) {
        this.id = id;
        this.displayName = displayName;
        this.durability = durability;
        this.baseDamage = baseDamage;
        this.perSecond = perSecond;
        this.perBounce = perBounce;
        this.maxBonus = maxBonus;
        this.speed = speed;
        this.range = range;
        this.returnSpeed = returnSpeed;
    }

    /** Current damage depending on flight time (ticks) and bounce count. */
    public double damage(int flightTicks, int bounces) {
        double bonus = perSecond * (flightTicks / 20.0) + perBounce * bounces;
        return baseDamage + Math.min(bonus, maxBonus);
    }

    public String translationKey() {
        return "item.neobum." + id + "_throwing_disc";
    }

    public String modelName() {
        return id + "_throwing_disc";
    }
}
