package ru.yourname.custommeteorites.generator;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class TreasureLoot {

    public static void fillChest(Inventory inventory, Map<String, Object> lootTable) {
        Random random = new Random();

        for (Map.Entry<String, Object> entry : lootTable.entrySet()) {
            Map<String, Object> lootEntry = (Map<String, Object>) entry.getValue();
            if (!(Boolean) lootEntry.getOrDefault("enabled", true)) continue;
            if (random.nextDouble() * 100 >= (Double) lootEntry.get("chance")) {
                continue; // Не выпало
            }

            String materialName = (String) lootEntry.get("item-type");
            Material material = Material.getMaterial(materialName);
            if (material == null) continue;

            Object amountObj = lootEntry.get("amount");
            int amount;
            if (amountObj instanceof List) {
                List<Integer> amountRange = (List<Integer>) amountObj;
                amount = random.nextInt(amountRange.get(1) - amountRange.get(0) + 1) + amountRange.get(0);
            } else {
                amount = (Integer) amountObj;
            }

            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            // Применение энчаров
            Map<String, Object> enchants = (Map<String, Object>) lootEntry.get("enchants");
            if (enchants != null) {
                for (Map.Entry<String, Object> enchantEntry : enchants.entrySet()) {
                    Enchantment enchant = Enchantment.getByName(enchantEntry.getKey());
                    int level = (Integer) enchantEntry.getValue();
                    if (enchant != null) {
                        meta.addEnchant(enchant, level, true);
                    }
                }
            }

            // Применение unbreakable
            if ((Boolean) lootEntry.getOrDefault("unbreakable", false)) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }

            // Применение display name и lore
            String displayName = (String) lootEntry.get("display-name");
            if (displayName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            List<String> lore = (List<String>) lootEntry.get("lore");
            if (lore != null) {
                meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList());
            }

            // Custom Model Data
            Integer customModelData = (Integer) lootEntry.get("custom-model-data");
            if (customModelData != null && customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }

            // Damage (применяется к инструментам/оружию)
            Integer damage = (Integer) lootEntry.get("damage");
            if (damage != null && damage > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageableMeta) {
                damageableMeta.setDamage(damage);
            }

            item.setItemMeta(meta);
            inventory.addItem(item);
        }
    }
}
