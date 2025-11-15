package com.imvineprexde.modmode;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ModLogger {
    private final ModeratorMode plugin;
    private final File logDirectory;

    public ModLogger(ModeratorMode plugin) {
        this.plugin = plugin;
        this.logDirectory = new File(plugin.getDataFolder(), "logs");
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
    }

    public void logModeEnter(Player moderator) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "MODE_ENTER");
        entry.addProperty("moderator_uuid", moderator.getUniqueId().toString());
        entry.addProperty("moderator_name", moderator.getName());
        entry.addProperty("timestamp", System.currentTimeMillis());
        entry.addProperty("world", moderator.getLocation().getWorld().getName());
        entry.addProperty("location", formatLocation(moderator.getLocation()));

        writeLog(entry);
    }

    public void logModeExit(Player moderator, long durationSeconds) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "MODE_EXIT");
        entry.addProperty("moderator_uuid", moderator.getUniqueId().toString());
        entry.addProperty("moderator_name", moderator.getName());
        entry.addProperty("timestamp", System.currentTimeMillis());
        entry.addProperty("duration_seconds", durationSeconds);

        writeLog(entry);
    }

    public void logInspection(Player moderator, Player target) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "PLAYER_INSPECT");
        entry.addProperty("moderator_name", moderator.getName());
        entry.addProperty("moderator_uuid", moderator.getUniqueId().toString());
        entry.addProperty("target_name", target.getName());
        entry.addProperty("target_uuid", target.getUniqueId().toString());
        entry.addProperty("timestamp", System.currentTimeMillis());

        writeLog(entry);
    }

    public void logHotbarChange(Player admin, String action, String itemName) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "HOTBAR_" + action.toUpperCase());
        entry.addProperty("admin_name", admin.getName());
        entry.addProperty("admin_uuid", admin.getUniqueId().toString());
        entry.addProperty("item", itemName);
        entry.addProperty("timestamp", System.currentTimeMillis());

        writeLog(entry);
    }

    private void writeLog(JsonObject data) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        File logFile = new File(logDirectory, "mod-actions-" + date + ".log");

        // Async logging to prevent lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(data.toString() + "\n");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write log: " + e.getMessage());
            }
        });
    }

    private String formatLocation(Location loc) {
        return String.format("%s %.2f,%.2f,%.2f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
}