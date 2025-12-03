package ru.yourname.custommeteorites.generator;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class TreasureLoot {

    public static void fillChest(Inventory inventory, org.bukkit.configuration.ConfigurationSection lootTable) {
        Random random = new Random();

        for (String key : lootTable.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection lootEntry = lootTable.getConfigurationSection(key);
            if (lootEntry == null || !lootEntry.getBoolean("enabled", true)) continue;
            if (random.nextDouble() * 100 >= lootEntry.getDouble("chance")) {
                continue;
            }

            String materialName = lootEntry.getString("item-type");
            Material material = Material.getMaterial(materialName);
            if (material == null) continue;

            int amount = lootEntry.getInt("amount", 1);
            // Если amount - список, обработайте его здесь

            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            org.bukkit.configuration.ConfigurationSection enchants = lootEntry.getConfigurationSection("enchants");
            if (enchants != null) {
                for (String enchantKey : enchants.getKeys(false)) {
                    Enchantment enchant = Enchantment.getByName(enchantKey);
                    int level = enchants.getInt(enchantKey);
                    if (enchant != null) {
                        meta.addEnchant(enchant, level, true);
                    }
                }
            }

            if (lootEntry.getBoolean("unbreakable", false)) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }

            String displayName = lootEntry.getString("display-name");
            if (displayName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            List<String> lore = lootEntry.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList());
            }

            item.setItemMeta(meta);
            inventory.addItem(item);
        }
    }
}
