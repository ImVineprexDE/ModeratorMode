package com.imvineprexde.modmode;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InspectListener implements Listener {
    private final ModeratorMode plugin;

    public InspectListener(ModeratorMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player moderator = event.getPlayer();
        Player target = (Player) event.getRightClicked();

        if (!plugin.isInModeratorMode(moderator)) return;
        if (!moderator.hasPermission("moderatormode.inspect")) {
            moderator.sendMessage("§c❌ You don't have permission to inspect players.");
            return;
        }

        openInspectionGUI(moderator, target);
        plugin.getModLogger().logInspection(moderator, target);
    }

    private void openInspectionGUI(Player moderator, Player target) {
        String title = "§cInspecting: " + target.getName();
        Inventory gui = Bukkit.createInventory(null, 27, title);

        gui.setItem(10, createPlayerSkull(target, moderator));

        gui.setItem(12, createMenuItem(
                Material.CHEST,
                "§e📦 View Inventory",
                "§7Click to see player's inventory",
                "§7Slots: " + target.getInventory().getSize()
        ));

        gui.setItem(13, createMenuItem(
                Material.ENDER_CHEST,
                "§5📦 View Ender Chest",
                "§7Click to see ender chest contents"
        ));

        gui.setItem(14, createMenuItem(
                Material.PAPER,
                "§a📊 Statistics",
                "§7Health: §c" + Math.round(target.getHealth()) + "/20",
                "§7Food: §e" + target.getFoodLevel() + "/20",
                "§7Gamemode: §6" + target.getGameMode().name(),
                "§7XP Level: §b" + target.getLevel()
        ));

        gui.setItem(16, createMenuItem(
                Material.POTION,
                "§d🧪 Active Effects",
                "§7Click to see potion effects",
                "§7Count: " + target.getActivePotionEffects().size()
        ));

        moderator.openInventory(gui);
    }

    public void showPlayerInventory(Player moderator, Player target) {
        String title = "§c" + target.getName() + "'s Inventory";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null) {
                inv.setItem(i, contents[i].clone());
            }
        }

        inv.setItem(36, target.getInventory().getHelmet());
        inv.setItem(37, target.getInventory().getChestplate());
        inv.setItem(38, target.getInventory().getLeggings());
        inv.setItem(39, target.getInventory().getBoots());
        inv.setItem(40, target.getInventory().getItemInOffHand());

        moderator.openInventory(inv);
    }

    public void showEnderChest(Player moderator, Player target) {
        String title = "§5" + target.getName() + "'s Ender Chest";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                inv.setItem(i, contents[i].clone());
            }
        }

        moderator.openInventory(inv);
    }

    // ✅ FIXED: Completely hides IP - only shows hint when enabled
    private ItemStack createPlayerSkull(Player target, Player moderator) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName("§6" + target.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7UUID: " + target.getUniqueId());

        // Only store IP and show hint if BOTH config true AND has permission
        boolean canCopyIp = plugin.isIpInspectionEnabled() && moderator.hasPermission("moderatormode.inspect.ip");

        if (canCopyIp) {
            String ip = target.getAddress().getAddress().getHostAddress();
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "player_ip"),
                    PersistentDataType.STRING,
                    ip
            );
            lore.add("§8§oRight-click to view Player-IP");
        }

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}