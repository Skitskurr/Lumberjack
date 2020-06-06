package com.skitskurr.lumberjack.utils;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemUtils {
	
	private static Random random = new Random();
	
	public static void reduceDurability(final ItemStack item) {
		final ItemMeta meta = item.getItemMeta();
		if(!(meta instanceof Damageable)) {
			return;
		}
		
		// simulate unbreaking enchantment
		if(random.nextInt(meta.getEnchantLevel(Enchantment.DURABILITY) + 1) != 0) {
			return;
		}
		
		final Damageable damageable = (Damageable) meta;
		final int newDamage = damageable.getDamage() + 1;
		
		if(newDamage == item.getType().getMaxDurability()) {
			item.setType(Material.AIR);
		} else {
			damageable.setDamage(newDamage);
			item.setItemMeta(meta);
		}
	}

}
