package com.drtshock.playervaults;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.drtshock.playervaults.commands.Commands;
import com.drtshock.playervaults.commands.SignSetInfo;
import com.drtshock.playervaults.commands.VaultViewInfo;
import com.drtshock.playervaults.util.Lang;
import com.drtshock.playervaults.util.Metrics;
import com.drtshock.playervaults.util.Updater;
import com.drtshock.playervaults.util.VaultManager;

public class PlayerVaults extends JavaPlugin {

    public static PlayerVaults PLUGIN;
    public static Logger log;
    public static boolean UPDATE = false;
    public static String NEWVERSION = "";
    public static String LINK = "";
    public static Commands commands;
    public static HashMap<String, SignSetInfo> SET_SIGN = new HashMap<String, SignSetInfo>();
    public static HashMap<String, VaultViewInfo> IN_VAULT = new HashMap<String, VaultViewInfo>();
    public static HashMap<String, Inventory> OPENINVENTORIES = new HashMap<String, Inventory>();
    public static Economy ECON = null;
    public static boolean DROP_ON_DEATH = false;
    public static int INVENTORIES_TO_DROP = 0;
    public static boolean USE_VAULT = false;
    public static YamlConfiguration LANG;
    public static File LANG_FILE;
    public static YamlConfiguration SIGNS;
    public static File SIGNS_FILE;
    public static String DIRECTORY = "plugins" + File.separator + "PlayerVaults" + File.separator + "vaults";
    public static VaultManager VM;
    public static Listeners listener;

    @Override
    public void onEnable() {
        loadLang();
        log = getServer().getLogger();
        getServer().getPluginManager().registerEvents(listener = new Listeners(this), this);
        loadConfig();
        loadSigns();
        startMetrics();
        checkUpdate();
        commands = new Commands();
        getCommand("pv").setExecutor(commands);
        getCommand("pvdel").setExecutor(commands);
        getCommand("pvsign").setExecutor(commands);
        getCommand("workbench").setExecutor(commands);
        setupEconomy();

        if (getConfig().getBoolean("drop-on-death.enabled")) {
            DROP_ON_DEATH = true;
            INVENTORIES_TO_DROP = getConfig().getInt("drop-on-death.inventories");
        }

        new File(DIRECTORY + File.separator + "backups").mkdirs();
        VM = new VaultManager(this);
    }

    @Override
    public void onDisable() {
        for(Player p:Bukkit.getOnlinePlayers()) {
            if (IN_VAULT.containsKey(p.getName())) {
                p.closeInventory();
            }
        }
    }

    /**
     * Start metrics
     */
    public void startMetrics() {
        try {
            Metrics metrics = new Metrics(this);
            metrics.start();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks for available updates.
     */
    public void checkUpdate() {
        new BukkitRunnable() {

            public void run() {
                Updater u = new Updater();
                if (getConfig().getBoolean("check-update")) {
                    try {
                        if (u.getUpdate(getDescription().getVersion())) {
                            UPDATE = true;
                        }
                    } catch(IOException e) {
                        log.log(Level.WARNING, "PlayerVaults: Failed to check for updates.");
                        log.log(Level.WARNING, "PlayerVaults: Report this stack trace to drtshock and gomeow.");
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskAsynchronously(this);
    }

    /**
     * Setup economy
     * 
     * @return Whether or not economy exists.
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        ECON = rsp.getProvider();
        USE_VAULT = true;
        return ECON != null;
    }

    /**
     * Load the config.yml file.
     */
    public void loadConfig() {
        File config = new File(getDataFolder() + File.separator + "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        } else {
            updateConfig();
        }
    }

    /**
     * Load the signs.yml file.
     */
    public void loadSigns() {
        File signs = new File(getDataFolder(), "signs.yml");
        if (!signs.exists()) {
            try {
                signs.createNewFile();
            } catch(IOException e) {
                log.severe("PlayerVaults has encountered a fatal error trying to load the signs file.");
                log.severe("Please report this error to drtshock and gomeow.");
                e.printStackTrace();
            }
        }
        PlayerVaults.SIGNS_FILE = signs;
        PlayerVaults.SIGNS = YamlConfiguration.loadConfiguration(signs);
    }

    /**
     * Get the signs.yml config.
     * 
     * @return The signs.yml config.
     */
    public YamlConfiguration getSigns() {
        return PlayerVaults.SIGNS;
    }

    /**
     * Save the signs.yml file.
     */
    public void saveSigns() {
        try {
            PlayerVaults.SIGNS.save(PlayerVaults.SIGNS_FILE);
        } catch(IOException e) {
            log.severe("PlayerVaults has encountered an error trying to save the signs file.");
            log.severe("Please report this error to drtshock and gomeow.");
            e.printStackTrace();
        }
    }

    /**
     * Update the config.yml file.
     */
    public void updateConfig() {
        boolean checkUpdate = getConfig().getBoolean("check-update", true);
        boolean ecoEnabled = getConfig().getBoolean("economy.enabled", false);
        int ecoCreate = getConfig().getInt("economy.cost-to-create", 100);
        int ecoOpen = getConfig().getInt("economy.cost-to-open", 10);
        int ecoDelete = getConfig().getInt("economy.refund-on-delete", 50);
        boolean dropEnabled = getConfig().getBoolean("drop-on-death.enabled", false);
        int dropInvs = getConfig().getInt("drop-on-death.inventories", 50);
        File configFile = new File(getDataFolder(), "config.yml");
        configFile.delete();
        YamlConfiguration conf = YamlConfiguration.loadConfiguration(getResource("config.yml"));
        setInConfig("check-update", checkUpdate, conf);
        setInConfig("economy.enabled", ecoEnabled, conf);
        setInConfig("economy.cost-to-create", ecoCreate, conf);
        setInConfig("economy.cost-to-open", ecoOpen, conf);
        setInConfig("economy.refund-on-delete", ecoDelete, conf);
        setInConfig("drop-on-death.enabled", dropEnabled, conf);
        setInConfig("drop-on-death.inventories", dropInvs, conf);
        try {
            conf.save(configFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set an object in the config.yml
     * @param path The path in the config.
     * @param object What to be saved.
     * @param conf Where to save the object.
     */
    public <T> void setInConfig(String path, T object, YamlConfiguration conf) {
        conf.set(path, object);
    }

    /**
     * Load the lang.yml file.
     * @return The lang.yml config.
     */
    public YamlConfiguration loadLang() {
        File lang = new File(getDataFolder(), "lang.yml");
        if (!lang.exists()) {
            try {
                getDataFolder().mkdir();
                lang.createNewFile();
                InputStream defConfigStream = this.getResource("lang.yml");
                if (defConfigStream != null) {
                    YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
                    defConfig.save(lang);
                    Lang.setFile(defConfig);
                    return defConfig;
                }
            } catch(IOException e) {
                e.printStackTrace(); // So they notice
                log.severe("[PlayerVaults] Couldn't create language file.");
                log.severe("[PlayerVaults] This is a fatal error. Now disabling");
                this.setEnabled(false); // Without it loaded, we can't send them messages
            }
        }
        YamlConfiguration conf = YamlConfiguration.loadConfiguration(lang);
        for(Lang item:Lang.values()) {
            if (conf.getString(item.getPath()) == null) {
                conf.set(item.getPath(), item.getDefault());
            }
        }
        Lang.setFile(conf);
        PlayerVaults.LANG = conf;
        PlayerVaults.LANG_FILE = lang;
        try {
            conf.save(getLangFile());
        } catch(IOException e) {
            log.log(Level.WARNING, "PlayerVaults: Failed to save lang.yml.");
            log.log(Level.WARNING, "PlayerVaults: Report this stack trace to drtshock and gomeow.");
            e.printStackTrace();
        }
        return conf;
    }

    /**
     * Gets the lang.yml config.
     * @return The lang.yml config.
     */
    public YamlConfiguration getLang() {
        return LANG;
    }

    /**
     * Get the lang.yml file.
     * @return The lang.yml file.
     */
    public File getLangFile() {
        return LANG_FILE;
    }
}
