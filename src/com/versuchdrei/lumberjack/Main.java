package com.versuchdrei.lumberjack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * the main class for the LumberJack plugin
 * 
 * @author VersuchDrei
 * @version 1.0
 */
public class Main extends JavaPlugin{
	
	@Override
	public void onEnable() {
		super.saveDefaultConfig();
		Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
	}

}
