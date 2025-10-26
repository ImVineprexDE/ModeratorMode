package com.imvineprexde.modmode;

// SuperVanish Import (über Maven verfügbar)
import de.myzelyam.api.vanish.VanishAPI;

// EssentialsX Import
import com.earth2me.essentials.Essentials;

// Standard Bukkit/Java Imports
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class ModeratorMode extends JavaPlugin implements TabCompleter, Listener {

    // Enum to manage which vanish provider is active
    private enum VanishProvider {
        SUPERVANISH,
        ESSENTIALS,
        CMI,
        BUKKIT_FALLBACK
    }

    private final Map<UUID, PlayerData> playerStates = new HashMap<>();
    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
            .setPrettyPrinting()
            .create();
    private File dataFolder;
    private List<ItemStack> moderatorHotbarItems;

    // Vanish integration variables
    private boolean hasSuperVanish = false;
    private Essentials essentials = null;
    private Plugin cmiPlugin = null;
    private VanishProvider activeVanishProvider;

    // CMI Reflection cache
    private Class<?> cmiClass = null;
    private Method cmiGetInstanceMethod = null;
    private Method cmiGetPlayerManagerMethod = null;
    private Method cmiGetUserMethod = null;
    private Method cmiSetVanishedMethod = null;

    @Override
    public void onEnable() {
        getLogger().info("ModeratorMode v1.2.1 has been enabled!"); // Version hier auch angepasst
        this.saveDefaultConfig();
        loadConfigValues();
        setupVanishHook();
        this.dataFolder = new File(getDataFolder(), "playerdata");
        if (!this.dataFolder.exists()) this.dataFolder.mkdirs();

        this.getCommand("modmode").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("ModeratorMode v1.2.1 has been disabled!"); // Version hier auch angepasst
        for (UUID uuid : new ArrayList<>(playerStates.keySet())) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "You were removed from moderator mode because the plugin was disabled.");
                restorePlayerState(player);
            }
        }
        playerStates.clear();
    }

    private void setupVanishHook() {
        // Priority: SuperVanish > EssentialsX > CMI > Bukkit Fallback

        // Check for SuperVanish (highest priority)
        Plugin superVanishPlugin = getServer().getPluginManager().getPlugin("SuperVanish");
        if (superVanishPlugin == null) {
            superVanishPlugin = getServer().getPluginManager().getPlugin("PremiumVanish");
        }

        if (superVanishPlugin != null && superVanishPlugin.isEnabled()) {
            try {
                // Test if VanishAPI is accessible
                Class.forName("de.myzelyam.api.vanish.VanishAPI");
                this.hasSuperVanish = true;
                this.activeVanishProvider = VanishProvider.SUPERVANISH;
                getLogger().info("Successfully hooked into SuperVanish/PremiumVanish for vanish support (Priority 1).");
                return;
            } catch (ClassNotFoundException e) {
                getLogger().warning("SuperVanish detected but API not accessible. Trying next provider...");
            }
        }

        // Check for EssentialsX (second priority)
        Plugin essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin instanceof Essentials) {
            this.essentials = (Essentials) essentialsPlugin;
            this.activeVanishProvider = VanishProvider.ESSENTIALS;
            getLogger().info("Successfully hooked into EssentialsX for vanish support (Priority 2).");
            return;
        }

        // Check for CMI (third priority) - Pure Reflection
        Plugin cmiPlug = getServer().getPluginManager().getPlugin("CMI");
        if (cmiPlug != null && cmiPlug.isEnabled()) {
            this.cmiPlugin = cmiPlug;
            if (setupCMIReflection()) {
                this.activeVanishProvider = VanishProvider.CMI;
                getLogger().info("Successfully hooked into CMI for vanish support via Reflection (Priority 3).");
                return;
            } else {
                getLogger().warning("CMI detected but reflection setup failed. Falling back to Bukkit method.");
            }
        }

        // Fallback to Bukkit
        this.activeVanishProvider = VanishProvider.BUKKIT_FALLBACK;
        getLogger().warning("No vanish plugin found. Using basic player hiding (Priority 4).");
    }

    /**
     * Setup CMI integration using pure reflection (no dependency required)
     */
    private boolean setupCMIReflection() {
        try {
            // com.Zrips.CMI.CMI.getInstance()
            cmiClass = Class.forName("com.Zrips.CMI.CMI");
            cmiGetInstanceMethod = cmiClass.getDeclaredMethod("getInstance");

            Object cmiInstance = cmiGetInstanceMethod.invoke(null);
            if (cmiInstance == null) {
                getLogger().warning("CMI instance is null");
                return false;
            }

            // cmiInstance.getPlayerManager()
            cmiGetPlayerManagerMethod = cmiClass.getDeclaredMethod("getPlayerManager");
            Object playerManager = cmiGetPlayerManagerMethod.invoke(cmiInstance);

            if (playerManager == null) {
                getLogger().warning("CMI PlayerManager is null");
                return false;
            }

            // playerManager.getUser(Player)
            Class<?> playerManagerClass = playerManager.getClass();
            cmiGetUserMethod = playerManagerClass.getDeclaredMethod("getUser", Player.class);

            // CMIUser.setVanished(boolean)
            Class<?> cmiUserClass = Class.forName("com.Zrips.CMI.Containers.CMIUser");
            cmiSetVanishedMethod = cmiUserClass.getDeclaredMethod("setVanished", boolean.class);

            getLogger().info("CMI reflection setup successful!");
            return true;

        } catch (ClassNotFoundException e) {
            getLogger().warning("CMI classes not found: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            getLogger().warning("CMI method not found: " + e.getMessage());
            return false;
        } catch (Exception e) {
            getLogger().warning("CMI reflection setup failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Set player vanish state using the configured provider
     */
    private void setVanished(Player player, boolean vanished) {
        try {
            switch (activeVanishProvider) {
                case SUPERVANISH:
                    if (hasSuperVanish) {
                        if (vanished) {
                            VanishAPI.hidePlayer(player);
                        } else {
                            VanishAPI.showPlayer(player);
                        }
                    }
                    break;

                case ESSENTIALS:
                    if (essentials != null) {
                        essentials.getUser(player.getUniqueId()).setVanished(vanished);
                    }
                    break;

                case CMI:
                    if (cmiPlugin != null && cmiGetInstanceMethod != null) {
                        Object cmiInstance = cmiGetInstanceMethod.invoke(null);
                        Object playerManager = cmiGetPlayerManagerMethod.invoke(cmiInstance);
                        Object cmiUser = cmiGetUserMethod.invoke(playerManager, player);

                        if (cmiUser != null) {
                            cmiSetVanishedMethod.invoke(cmiUser, vanished);
                        } else {
                            getLogger().warning("CMIUser is null for player: " + player.getName());
                            fallbackVanish(player, vanished);
                        }
                    }
                    break;

                case BUKKIT_FALLBACK:
                default:
                    fallbackVanish(player, vanished);
                    break;
            }
        } catch (Exception e) {
            getLogger().severe("Error setting vanish state for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            // Fallback to Bukkit method on error
            fallbackVanish(player, vanished);
        }
    }

    /**
     * Fallback vanish method using Bukkit's player hide/show
     */
    private void fallbackVanish(Player player, boolean vanished) {
        if (vanished) {
            getServer().getOnlinePlayers().forEach(onlinePlayer -> {
                if (!onlinePlayer.equals(player) && !onlinePlayer.hasPermission("moderatormode.seevanished")) {
                    onlinePlayer.hidePlayer(this, player);
                }
            });
        } else {
            getServer().getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.showPlayer(this, player));
        }
    }

    private void loadConfigValues() {
        moderatorHotbarItems = new ArrayList<>();
        List<?> rawList = getConfig().getList("moderator-hotbar-items");
        if (rawList == null) return;

        for (Object obj : rawList) {
            if (obj instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    moderatorHotbarItems.add(ItemStack.deserialize(map));
                } catch (Exception e) {
                    getLogger().warning("Could not load an item from config.yml: " + e.getMessage());
                }
            }
        }
        getLogger().info("Loaded " + moderatorHotbarItems.size() + " hotbar item(s).");
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (playerStates.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop items while in moderator mode.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            if (!player.hasPermission("moderatormode.use")) return noPerms(player);
            toggleModeratorMode(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help":
                if (!player.hasPermission("moderatormode.use")) return noPerms(player);
                sendHelpMessage(player);
                break;
            case "add":
                if (!player.hasPermission("moderatormode.admin")) return noAdminPerms(player);
                handleAddItemCommand(player);
                break;
            case "remove":
                if (!player.hasPermission("moderatormode.admin")) return noAdminPerms(player);
                handleRemoveItemCommand(player, args);
                break;
            case "list":
                if (!player.hasPermission("moderatormode.admin")) return noAdminPerms(player);
                handleListItemsCommand(player);
                break;
            case "reload":
                if (!player.hasPermission("moderatormode.admin")) return noAdminPerms(player);
                handleReloadCommand(player);
                break;
            default:
                if (!player.hasPermission("moderatormode.use")) return noPerms(player);
                player.sendMessage(ChatColor.RED + "Unknown command. Use /modmode help for assistance.");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("modmode")) {
            if (args.length == 1) {
                List<String> subCommands = new ArrayList<>();
                if (sender.hasPermission("moderatormode.use")) subCommands.add("help");
                if (sender.hasPermission("moderatormode.admin")) {
                    subCommands.addAll(Arrays.asList("add", "remove", "list", "reload"));
                }
                StringUtil.copyPartialMatches(args[0], subCommands, completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                if (sender.hasPermission("moderatormode.admin")) {
                    List<String> numbers = new ArrayList<>();
                    for (int i = 1; i <= moderatorHotbarItems.size(); i++) {
                        numbers.add(String.valueOf(i));
                    }
                    StringUtil.copyPartialMatches(args[1], numbers, completions);
                }
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void toggleModeratorMode(Player player) {
        if (playerStates.containsKey(player.getUniqueId()) || new File(dataFolder, player.getUniqueId() + ".json").exists()) {
            player.sendMessage(ChatColor.GREEN + "You have left moderator mode!");
            restorePlayerState(player);
        } else {
            player.sendMessage(ChatColor.GREEN + "You have entered moderator mode!");
            savePlayerState(player);
            applyModeratorMode(player);
        }
    }

    private void applyModeratorMode(Player player) {
        setVanished(player, true);
        player.setGameMode(GameMode.CREATIVE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setExp(0);
        player.setLevel(0);
        player.getInventory().clear();
        // KORREKTUR HIER: Iteriere über eine Kopie der Liste, um ConcurrentModificationException zu vermeiden
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
        for (ItemStack item : moderatorHotbarItems) {
            player.getInventory().addItem(item.clone());
        }

        // Send vanish provider info
        String vanishMessage = switch (activeVanishProvider) {
            case SUPERVANISH -> ChatColor.GRAY + "Vanished using SuperVanish/PremiumVanish.";
            case ESSENTIALS -> ChatColor.GRAY + "Vanished using EssentialsX.";
            case CMI -> ChatColor.GRAY + "Vanished using CMI (Reflection).";
            case BUKKIT_FALLBACK -> ChatColor.GRAY + "Vanished using built-in hiding feature.";
        };
        player.sendMessage(vanishMessage);
    }

    private void savePlayerState(Player player) {
        PlayerData data = new PlayerData(
                player.getLocation(), player.getGameMode(), player.getHealth(), player.getFoodLevel(),
                player.getExp(), player.getLevel(), player.getInventory().getContents(),
                player.getInventory().getArmorContents(), player.getActivePotionEffects()
        );
        playerStates.put(player.getUniqueId(), data);
        File playerFile = new File(dataFolder, player.getUniqueId() + ".json");
        try (FileWriter writer = new FileWriter(playerFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            getLogger().severe("Could not save player data for " + player.getName() + "!");
            e.printStackTrace();
        }
    }

    private void restorePlayerState(Player player) {
        setVanished(player, false);
        PlayerData data = playerStates.remove(player.getUniqueId());
        File playerFile = new File(dataFolder, player.getUniqueId() + ".json");
        if (data == null) {
            if (playerFile.exists()) {
                try (FileReader reader = new FileReader(playerFile)) {
                    data = gson.fromJson(reader, PlayerData.class);
                } catch (IOException e) {
                    getLogger().severe("Could not load player data for " + player.getName() + "!");
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Error: Your saved data could not be loaded.");
                    return;
                }
            } else {
                player.sendMessage(ChatColor.RED + "Error: No saved data was found.");
                return;
            }
        }

        player.teleport(data.getLocation());
        player.setGameMode(data.getGameMode());
        player.setHealth(data.getHealth());
        player.setFoodLevel(data.getFoodLevel());
        player.setExp(data.getExp());
        player.setLevel(data.getLevel());
        player.getInventory().clear();
        player.getInventory().setContents(data.getInventoryContents());
        player.getInventory().setArmorContents(data.getArmorContents());
        // KORREKTUR HIER: Iteriere über eine Kopie der Liste, um ConcurrentModificationException zu vermeiden
        new ArrayList<>(player.getActivePotionEffects()).forEach(effect -> player.removePotionEffect(effect.getType()));
        data.getStoredPotionEffects().forEach(player::addPotionEffect);

        if (playerFile.exists()) {
            if (!playerFile.delete()) {
                getLogger().warning("Failed to delete player data file for " + player.getName());
            }
        }

        player.sendMessage(ChatColor.GRAY + "You are no longer vanished.");
    }

    private void handleAddItemCommand(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Error: You are not holding an item in your hand!");
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack item : moderatorHotbarItems) {
            items.add(item.serialize());
        }
        items.add(heldItem.serialize());
        getConfig().set("moderator-hotbar-items", items);
        saveConfig();
        loadConfigValues();
        Component itemName = heldItem.hasItemMeta() && heldItem.getItemMeta().hasDisplayName()
                ? heldItem.getItemMeta().displayName()
                : Component.text(heldItem.getType().name());
        player.sendMessage(Component.text("The item ").color(NamedTextColor.GREEN)
                .append(itemName)
                .append(Component.text(" has been added to the hotbar list!")));
    }

    private void handleRemoveItemCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /modmode remove <number>");
            player.sendMessage(ChatColor.GRAY + "Use /modmode list to see the item numbers.");
            return;
        }
        try {
            int index = Integer.parseInt(args[1]) - 1;
            if (index < 0 || index >= moderatorHotbarItems.size()) {
                player.sendMessage(ChatColor.RED + "Error: Invalid number.");
                return;
            }
            ItemStack removedItem = moderatorHotbarItems.get(index);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack item : moderatorHotbarItems) {
                items.add(item.serialize());
            }
            items.remove(index);
            getConfig().set("moderator-hotbar-items", items);
            saveConfig();
            loadConfigValues();
            Component itemName = removedItem.hasItemMeta() && removedItem.getItemMeta().hasDisplayName()
                    ? removedItem.getItemMeta().displayName()
                    : Component.text(removedItem.getType().name());
            player.sendMessage(Component.text("The item ").color(NamedTextColor.GREEN)
                    .append(itemName)
                    .append(Component.text(" has been removed from the list!")));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Error: '" + args[1] + "' is not a valid number.");
        }
    }

    private void handleListItemsCommand(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Configured Hotbar Items ---");
        if (moderatorHotbarItems.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No items are configured.");
        } else {
            for (int i = 0; i < moderatorHotbarItems.size(); i++) {
                ItemStack item = moderatorHotbarItems.get(i);
                Component displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().displayName()
                        : Component.text(item.getType().name()).color(NamedTextColor.YELLOW);
                player.sendMessage(Component.text((i + 1) + ". ").color(NamedTextColor.GOLD).append(displayName));
            }
        }
        player.sendMessage(ChatColor.GOLD + "-----------------------------");
    }

    private void handleReloadCommand(Player player) {
        this.reloadConfig();
        loadConfigValues();
        // Re-check vanish hooks after reload
        setupVanishHook();
        player.sendMessage(ChatColor.GREEN + "ModeratorMode configuration successfully reloaded!");
        player.sendMessage(ChatColor.GRAY + "Active vanish provider: " + activeVanishProvider.name());
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- ModeratorMode Help ---");
        player.sendMessage(ChatColor.YELLOW + "/mm" + ChatColor.WHITE + " - Toggles moderator mode.");
        player.sendMessage(ChatColor.YELLOW + "/mm help" + ChatColor.WHITE + " - Shows this help message.");
        if (player.hasPermission("moderatormode.admin")) {
            player.sendMessage(ChatColor.AQUA + "/mm list" + ChatColor.WHITE + " - Lists the configured hotbar items.");
            player.sendMessage(ChatColor.AQUA + "/mm add" + ChatColor.WHITE + " - Adds the item in your hand to the list.");
            player.sendMessage(ChatColor.AQUA + "/mm remove <number>" + ChatColor.WHITE + " - Removes an item from the list.");
            player.sendMessage(ChatColor.AQUA + "/mm reload" + ChatColor.WHITE + " - Reloads the configuration file.");
        }
        player.sendMessage(ChatColor.GOLD + "------------------------");
    }

    private boolean noPerms(Player player) {
        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        return true;
    }

    private boolean noAdminPerms(Player player) {
        player.sendMessage(ChatColor.RED + "You do not have admin permission for this command.");
        return true;
    }
}