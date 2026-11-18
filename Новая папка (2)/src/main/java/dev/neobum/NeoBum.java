package dev.neobum;

import org.bukkit.plugin.java.JavaPlugin;

public final class NeoBum extends JavaPlugin {

    private final DiscConfig cfg = new DiscConfig();
    private DiscItems items;
    private DiscManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg.load(getConfig());
        items = new DiscItems(this);
        manager = new DiscManager(this);

        getServer().getPluginManager().registerEvents(new DiscListener(this), this);
        DiscCommand command = new DiscCommand(this);
        getCommand("neobum").setExecutor(command);
        getCommand("neobum").setTabCompleter(command);

        getServer().getScheduler().runTaskTimer(this, manager::tick, 1L, 1L);
        getLogger().info("NeoBum enabled: " + cfg.all().size() + " discs loaded.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        cfg.load(getConfig());
    }

    public void debug(String msg) {
        if (cfg.debug) getLogger().info("[debug] " + msg);
    }

    public DiscConfig cfg() {
        return cfg;
    }

    public DiscItems items() {
        return items;
    }

    public DiscManager manager() {
        return manager;
    }
}
