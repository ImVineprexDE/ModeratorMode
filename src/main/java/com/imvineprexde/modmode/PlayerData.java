package com.imvineprexde.modmode;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlayerData {
    private final double health;
    private final int foodLevel;
    private final float exp;
    private final int level;
    private final String gameMode;
    private final Map<String, Object> location;
    private final List<Map<String, Object>> inventoryContents;
    private final List<Map<String, Object>> armorContents;
    private final List<Map<String, Object>> potionEffects;

    public PlayerData(Location location, GameMode gameMode, double health, int foodLevel, float exp, int level,
                      ItemStack[] inventoryContents, ItemStack[] armorContents, Collection<PotionEffect> storedPotionEffects) {
        this.location = location.serialize();
        this.gameMode = gameMode.name();
        this.health = health;
        this.foodLevel = foodLevel;
        this.exp = exp;
        this.level = level;
        this.inventoryContents = new ArrayList<>();
        for (ItemStack item : inventoryContents) {
            this.inventoryContents.add(item != null ? item.serialize() : null);
        }
        this.armorContents = new ArrayList<>();
        for (ItemStack item : armorContents) {
            this.armorContents.add(item != null ? item.serialize() : null);
        }
        this.potionEffects = storedPotionEffects.stream()
                .map(PotionEffect::serialize)
                .collect(Collectors.toList());
    }

    public Location getLocation() {
        return Location.deserialize(this.location);
    }
    public GameMode getGameMode() {
        return GameMode.valueOf(this.gameMode);
    }
    public ItemStack[] getInventoryContents() {
        ItemStack[] items = new ItemStack[this.inventoryContents.size()];
        for (int i = 0; i < this.inventoryContents.size(); i++) {
            Map<String, Object> map = this.inventoryContents.get(i);
            items[i] = (map != null) ? ItemStack.deserialize(map) : null;
        }
        return items;
    }
    public ItemStack[] getArmorContents() {
        ItemStack[] items = new ItemStack[this.armorContents.size()];
        for (int i = 0; i < this.armorContents.size(); i++) {
            Map<String, Object> map = this.armorContents.get(i);
            items[i] = (map != null) ? ItemStack.deserialize(map) : null;
        }
        return items;
    }
    public Collection<PotionEffect> getStoredPotionEffects() {
        return this.potionEffects.stream()
                .map(PotionEffect::new)
                .collect(Collectors.toList());
    }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public float getExp() { return exp; }
    public int getLevel() { return level; }
}