package com.skitskurr.lumberjack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin{
	
	@Override
	public void onEnable() {
		super.saveDefaultConfig();
		Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
	}

}
