package com.skitskurr.lumberjack.utils;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

public class MaterialTag implements Tag<Material>{
	
	private final NamespacedKey key;
	private final Set<Material> values = new HashSet<>();

	public MaterialTag(final NamespacedKey key, final Material[] types) {
		this.key = key;
		for(final Material type: types) {
			values.add(type);
		}
	}
	
	@Override
	public NamespacedKey getKey() {
		return this.key;
	}

	@Override
	public Set<Material> getValues() {
		return this.values;
	}

	@Override
	public boolean isTagged(final Material type) {
		return this.values.contains(type);
	}

}
