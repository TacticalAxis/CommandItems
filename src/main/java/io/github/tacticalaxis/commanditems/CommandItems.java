package io.github.tacticalaxis.commanditems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class CommandItems extends JavaPlugin implements Listener {

    private static CommandItems instance;

    public static CommandItems getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getCommand("commanditems").setExecutor(this);
        ConfigurationManager.getInstance().setupConfiguration();
        writeMaterialValues();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        giveItems(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigurationManager.getInstance().reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "CustomItems configuration reloaded!");
        return true;
    }

    private static FileConfiguration config() {
        return ConfigurationManager.getInstance().getConfiguration(ConfigurationManager.CONFIG_MAIN);
    }

    private void writeMaterialValues() {
        try {
            if (!getDataFolder().exists()) {
                boolean success = getDataFolder().mkdirs();
                if (!success) {
                    System.out.println("Configuration files could not be created!");
                    Bukkit.shutdown();
                }
            }
            File file = new File(getDataFolder(), "materials.yml");
            if (!file.exists()) {
                getLogger().info("materials.yml" + " not found, creating!");
                saveResource("materials.yml", true);
            }
            FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            ArrayList<String> mats = new ArrayList<>();
            for (Material m : Material.values()) {
                mats.add(m.name());
            }
            configuration.set("materials", mats);
            configuration.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void click(PlayerInteractEvent event) {
        if (event.getPlayer().getInventory().getItemInMainHand() != null) {
            ItemStack i = event.getPlayer().getInventory().getItemInMainHand();
            if (i.hasItemMeta()) {
                ItemMeta im = i.getItemMeta();
                if (im.hasLore()) {
                    HashMap<String, HashMap<ItemStack, Integer>> items = getItems();
                    for (String name : items.keySet()) {
                        for (ItemStack item : items.get(name).keySet()) {
                            if (i.isSimilar(item)) {
                                List<String> commands = config()
                                        .getConfigurationSection("items")
                                        .getConfigurationSection(name)
                                        .getStringList("commands");
                                for (String cmd : commands) {
                                    Bukkit.dispatchCommand(event.getPlayer(), cmd);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void inventory(InventoryClickEvent event) {
        if (event.getCurrentItem() != null) {
            ItemStack i = event.getCurrentItem();
            if (i.hasItemMeta()) {
                ItemMeta im = i.getItemMeta();
                if (im.hasLore()) {
                    HashMap<String, HashMap<ItemStack, Integer>> items = getItems();
                    for (String name : items.keySet()) {
                        for (ItemStack item : items.get(name).keySet()) {
                            if (i.isSimilar(item)) {
                                event.setCancelled(true);
                                List<String> commands = config()
                                        .getConfigurationSection("items")
                                        .getConfigurationSection(name)
                                        .getStringList("commands");
                                for (String cmd : commands) {
                                    Bukkit.dispatchCommand(event.getWhoClicked(), cmd);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void giveItems(Player player) {
        HashMap<String, HashMap<ItemStack, Integer>> items = getItems();
        for (String name : items.keySet()) {
            for (ItemStack item : items.get(name).keySet()) {
                player.getInventory().setItem(items.get(name).get(item), item);
            }
        }
    }



    private static HashMap<String, HashMap<ItemStack, Integer>> getItems() {
        HashMap<String, HashMap<ItemStack, Integer>> everything = new HashMap<>();
        ConfigurationSection cfg = config().getConfigurationSection("items");
        for (String itemName : cfg.getKeys(false)) {
            HashMap<ItemStack, Integer> all = new HashMap<>();
            ConfigurationSection cfgItem = cfg.getConfigurationSection(itemName);
            try {
                Material material = Material.getMaterial(cfgItem.getString("type").toUpperCase());
                int slot = cfgItem.getInt("slot", 9) - 1;
                if (material == null) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Material " + ChatColor.GOLD + cfgItem.getString("type").toUpperCase() + ChatColor.RED + " does not exist! Reverting to §6DIAMOND_AXE");
                    material = Material.DIAMOND_AXE;
                }

                ItemStack item = new ItemStack(material, 1);

                ItemMeta im = item.getItemMeta();

                ConfigurationSection meta = cfgItem.getConfigurationSection("metadata");

                if (meta != null) {
                    if (meta.getString("display-name") != null) {
                        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', meta.getString("display-name")));
                    }

                    List<String> lore = new ArrayList<>();
                    if (meta.getStringList("lore") != null) {
                        for (String loreComponent : meta.getStringList("lore")) {
                            String s = ChatColor.translateAlternateColorCodes('&', loreComponent);
                            lore.add(s);
                        }

                    }
                    im.setLore(lore);

                    if (meta.getBoolean("glow", true)) {
                        im.addEnchant(Enchantment.MENDING, 1, true);
                    }

                    im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

                    item.setItemMeta(im);
                }
                all.put(item, slot);
                everything.put(itemName, new HashMap<>(all));
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Error with item: " + itemName);
            }
        }
        return everything;
    }
}