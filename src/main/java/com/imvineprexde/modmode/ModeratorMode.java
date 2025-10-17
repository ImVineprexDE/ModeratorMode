package com.imvineprexde.modmode;

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
import java.lang.reflect.Modifier;
import java.util.*;

public class ModeratorMode extends JavaPlugin implements TabCompleter, Listener {

    // Enum to manage which vanish provider is active
    private enum VanishProvider {
        ESSENTIALS,
        BUKKIT_FALLBACK
    }

    private final Map<UUID, PlayerData> playerStates = new HashMap<>();
    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
            .setPrettyPrinting()
            .create();
    private File dataFolder;
    private List<ItemStack> moderatorHotbarItems;

    // EssentialsX integration variable
    private Essentials essentials = null;
    private VanishProvider activeVanishProvider;

    @Override
    public void onEnable() {
        getLogger().info("ModeratorMode v1.1-Vanish has been enabled!");
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
        getLogger().info("ModeratorMode v1.1-Vanish has been disabled!");
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
        Plugin essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin instanceof Essentials) {
            this.essentials = (Essentials) essentialsPlugin;
            this.activeVanishProvider = VanishProvider.ESSENTIALS;
            getLogger().info("Successfully hooked into EssentialsX for vanish support.");
        } else {
            this.activeVanishProvider = VanishProvider.BUKKIT_FALLBACK;
            getLogger().warning("EssentialsX not found. Falling back to basic player hiding.");
        }
    }

    private void setVanished(Player player, boolean vanished) {
        if (essentials != null) {
            essentials.getUser(player.getUniqueId()).setVanished(vanished);
        } else {
            // Fallback to the simple Bukkit method
            if (vanished) {
                getServer().getOnlinePlayers().forEach(onlinePlayer -> {
                    if (!onlinePlayer.equals(player)) onlinePlayer.hidePlayer(this, player);
                });
            } else {
                getServer().getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.showPlayer(this, player));
            }
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
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
        for (ItemStack item : moderatorHotbarItems) {
            player.getInventory().addItem(item.clone());
        }

        switch (activeVanishProvider) {
            case ESSENTIALS:
                player.sendMessage(ChatColor.GRAY + "Vanished using EssentialsX.");
                break;
            case BUKKIT_FALLBACK:
            default:
                player.sendMessage(ChatColor.GRAY + "Vanished using built-in hiding feature.");
                break;
        }
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
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        data.getStoredPotionEffects().forEach(player::addPotionEffect);

        if (playerFile.exists()) {
            playerFile.delete();
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
        player.sendMessage(ChatColor.GREEN + "ModeratorMode configuration successfully reloaded!");
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