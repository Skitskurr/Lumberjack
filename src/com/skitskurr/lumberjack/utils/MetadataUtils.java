package com.skitskurr.lumberjack.utils;

import java.util.List;
import java.util.Optional;

import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

public class MetadataUtils {
	
	@SuppressWarnings("unchecked")
	public static <T> Optional<T> getMetadata(JavaPlugin plugin, Metadatable entity, String key, Class<T> type){
		List<MetadataValue> metadata = entity.getMetadata(key);
		for(MetadataValue value: metadata) {
			if(value.getOwningPlugin() == plugin && type.isInstance(value.value())) {
				return Optional.of((T) value.value());
			}
		}
		
		return Optional.empty();
	}

}
