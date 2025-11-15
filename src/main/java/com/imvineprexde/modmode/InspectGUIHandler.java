package com.imvineprexde.modmode;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class InspectGUIHandler implements Listener {
    private final ModeratorMode plugin;

    public InspectGUIHandler(ModeratorMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player moderator = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.startsWith("§cInspecting: ") ||
                title.contains("'s Inventory") ||
                title.contains("'s Ender Chest")) {

            event.setCancelled(true);

            if (title.startsWith("§cInspecting: ") &&
                    event.getSlot() == 10 &&
                    event.getClick().isRightClick()) {

                handleHeadRightClick(moderator, event.getCurrentItem());
            }

            if (title.startsWith("§cInspecting: ") && event.getClick().isLeftClick()) {
                handleMainMenuClick(moderator, event.getCurrentItem(), title);
            }
        }
    }

    private void handleHeadRightClick(Player moderator, ItemStack head) {
        if (head == null || head.getType() != Material.PLAYER_HEAD) return;

        if (!moderator.hasPermission("moderatormode.inspect.ip")) {
            moderator.sendMessage("§c❌ You don't have permission to copy IP addresses.");
            return;
        }

        if (!plugin.isIpInspectionEnabled()) {
            moderator.sendMessage("§c❌ IP inspection is disabled in config.");
            return;
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        String ip = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "player_ip"),
                PersistentDataType.STRING
        );

        if (ip == null) {
            moderator.sendMessage("§c❌ No IP data found for this player.");
            return;
        }

        // Send clickable message that copies IP when clicked
        Component message = Component.text("§a✓ Click to copy IP: ")
                .append(Component.text(ip).color(NamedTextColor.WHITE))
                .clickEvent(ClickEvent.copyToClipboard(ip));

        moderator.sendMessage(message);
        moderator.sendMessage("§7(IP copied to chat - click it to copy to clipboard)");
        moderator.closeInventory();
    }

    private void handleMainMenuClick(Player moderator, ItemStack item, String title) {
        if (item == null || item.getType().isAir()) return;

        String targetName = title.replace("§cInspecting: ", "");
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            moderator.sendMessage("§c❌ Target player is no longer online.");
            moderator.closeInventory();
            return;
        }

        switch (item.getType()) {
            case CHEST:
                plugin.showPlayerInventory(moderator, target);
                break;
            case ENDER_CHEST:
                plugin.showEnderChest(moderator, target);
                break;
            default:
                break;
        }
    }
}