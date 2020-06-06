package com.skitskurr.lumberjack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import com.skitskurr.lumberjack.utils.BlockDistanceQueue;
import com.skitskurr.lumberjack.utils.ItemUtils;
import com.skitskurr.lumberjack.utils.MetadataUtils;

import net.md_5.bungee.api.ChatColor;

public class EventListener implements Listener{
	
	private static final String CONFIG_KEY_ACTIVE_ON_JOIN = "activeOnJoin";
	private static final String CONFIG_KEY_USE_PERMISSIONS = "usePermissions";
	private static final String CONFIG_KEY_FAST_LEAF_DECAY = "fastLeafDecay";
	private static final String CONFIG_KEY_LEAF_DECAY_SOUND = "leafDecaySound";
	private static final String CONFIG_KEY_LEAF_DECAY_PARTICLES = "leafDecayParticles";
	
	private static final String METADATA_KEY_LUMBER_MODE = "lumberMode";
	private static final String METADATA_KEY_LUMBER_QUEUE = "lumberQueue";
	private static final String METADATA_KEY_LEAF_DECAY = "leafDecay";
	
	private static final String PERMISSION_LUMBERJACK = "skitskurr.lumberjack";
	
	private static final BlockFace[] NEIGHBORS = {BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST};
	private static final BlockFace[] LEAF_NEIGHBORS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
	private static final Material[] AXES = {Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE};
	
	private final Main plugin;
	
	private final boolean activeOnJoin;
	private final boolean usePermissions;
	private final boolean fastLeafDecay;
	private final boolean leafDecaySound;
	private final boolean leafDecayParticles;
	
	public EventListener(final Main plugin) {
		this.plugin = plugin;
		
		final FileConfiguration config = plugin.getConfig();
		this.activeOnJoin = config.getBoolean(EventListener.CONFIG_KEY_ACTIVE_ON_JOIN);
		this.usePermissions = config.getBoolean(EventListener.CONFIG_KEY_USE_PERMISSIONS);
		this.fastLeafDecay = config.getBoolean(EventListener.CONFIG_KEY_FAST_LEAF_DECAY);
		this.leafDecaySound = config.getBoolean(EventListener.CONFIG_KEY_LEAF_DECAY_SOUND);
		this.leafDecayParticles = config.getBoolean(EventListener.CONFIG_KEY_LEAF_DECAY_PARTICLES);
	}
	
	/**
	 * sets the players lumberjack mode to the configs default setting on join
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		// permissions are on and player does not have it? -> abort
		if(!permissionCheck(player)) {
			return;
		}
		player.setMetadata(EventListener.METADATA_KEY_LUMBER_MODE, new FixedMetadataValue(this.plugin, this.activeOnJoin));
	}
	
	/**
	 * toggles the players lumberjack mode when right clicking air with an axe
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onInteract(final PlayerInteractEvent event) {
		// player clicked a block -> abort
		if(event.getAction() != Action.RIGHT_CLICK_AIR) {
			return;
		}
		
		// when the off hand is empty this event is only called for the main hand
		// when holding an item in the off hand it is called for the off and main hand
		// so in case of something else than the main hand we abort here
		if(event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		
		final Player player = event.getPlayer();
		// player is not wielding an axe? -> abort
		if(!Arrays.stream(EventListener.AXES).anyMatch(player.getInventory().getItemInMainHand().getType()::equals)) {
			return;
		}
		
		// permissions are on and player does not have it? -> abort
		if(!permissionCheck(player)) {
			player.sendMessage(ChatColor.GRAY + "You don't have permissions to use the lumberjack mode.");
			return;
		}
		
		final Optional<Boolean> optionalLumberMode = MetadataUtils.getMetadata(plugin, player, EventListener.METADATA_KEY_LUMBER_MODE, Boolean.class);
		// no metadata? -> shouldn't happen, so safety abort
		if(!optionalLumberMode.isPresent()) {
			return;
		}
		
		final boolean newValue = !optionalLumberMode.get();
		player.setMetadata(EventListener.METADATA_KEY_LUMBER_MODE, new FixedMetadataValue(this.plugin, newValue));
		player.sendMessage(ChatColor.GRAY + "Lumberjack mode is now " + ChatColor.DARK_GRAY + (newValue? "ON" : "OFF") + ChatColor.GRAY + ".");
	}
	
	/**
	 * checks if either a leaf was broken and tries to decay surrounding leaves,
	 *  or if a log was broken with an axe by a player who has lumberjack enabled and if so applies the lumberjack funtionality
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBreak(final BlockBreakEvent event) {
		final Block block = event.getBlock();
		final Material mat = block.getType();
		
		if(Tag.LOGS.isTagged(mat) || Tag.LEAVES.isTagged(mat)) {
			decaySurroundingLeaves(block);
		}
		
		// if no log was broken we can stop here
		if(!Tag.LOGS.isTagged(mat)) {
			return;
		}
		
		final Player player = event.getPlayer();
		final ItemStack mainHand = player.getInventory().getItemInMainHand();
		// lumberjack only works with axes
		if(!Arrays.stream(EventListener.AXES).anyMatch(mainHand.getType()::equals)) {
			return;
		}
		
		// permissions are on and player does not have it? -> abort
		if(!permissionCheck(player)) {
			return;
		}
		
		final Optional<Boolean> optionalLumberMode = MetadataUtils.getMetadata(plugin, player, EventListener.METADATA_KEY_LUMBER_MODE, Boolean.class);
		// no metadata? -> shouldn't happen, so safety abort
		if(!optionalLumberMode.isPresent()) {
			return;
		}
		
		// player has lumberjack disabled? -> check for regular leaf decay and abort
		if(!optionalLumberMode.get()) {
			return;
		}
		
		final Optional<BlockDistanceQueue> optionalQueue = MetadataUtils.getMetadata(plugin, block, EventListener.METADATA_KEY_LUMBER_QUEUE, BlockDistanceQueue.class);
		final BlockDistanceQueue queue = optionalQueue.orElseGet(() -> loadLumberQueue(block));
		
		// if the block is the last one the queue has to be removed again, 
		// otherwise it will interfere with a new tree growing at the same location
		if(queue.isLast()) {
			block.removeMetadata(EventListener.METADATA_KEY_LUMBER_QUEUE, this.plugin);
			return;
		}
		
		Block furthest;
		do {
			furthest = queue.poll();
			// queue cannot be empty here, because we have at least the broken log left
		} while(!Tag.LOGS.isTagged(furthest.getType()));

		final Location loc = event.getPlayer().getLocation();
		loc.getWorld().dropItemNaturally(loc, new ItemStack(furthest.getType()));
		furthest.setType(Material.AIR);
		furthest.removeMetadata(EventListener.METADATA_KEY_LUMBER_QUEUE, plugin);
		decaySurroundingLeaves(furthest);
		event.setCancelled(true);
		ItemUtils.reduceDurability(mainHand);
		player.getInventory().setItemInMainHand(mainHand);
	}
	
	/**
	 * checks if there are more leaves around a decaying leaf and if so decays them, too
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDecay(final LeavesDecayEvent event) {
		decaySurroundingLeaves(event.getBlock());
	}
	
	/**
	 * checks if either permissions are disabled or the given player has permissions to use the lumberjack mode
	 * @param player the player to check for permissions
	 * @return true if permissions are disabled or the player has lumberjack permissions, otherwise false
	 */
	private boolean permissionCheck(final Player player) {
		return !this.usePermissions || player.hasPermission(EventListener.PERMISSION_LUMBERJACK);
	}
	
	/**
	 * generates the LumberQueue for the broken block
	 * @param block the block that was broken
	 * @return a queue of connected logs
	 */
	private BlockDistanceQueue loadLumberQueue(final Block block) {
		final BlockDistanceQueue queue = new BlockDistanceQueue();
		final Set<Block> checkedBlocks = new HashSet<>();
		checkBlock(block, block, queue, checkedBlocks);
		block.setMetadata(EventListener.METADATA_KEY_LUMBER_QUEUE, new FixedMetadataValue(this.plugin, queue));
		return queue;
	}
	
	/**
	 * checks if a given block is a log and if so adds it to the given queue and recursively checks neighbored blocks in a 3 block dice around it
	 * @param source the source block to calculate the distance from
	 * @param check the block to check
	 * @param queue the queue to add the block to, if applicable
	 * @param checkedBlocks a set of blocks already checked, to prevent circulation
	 */
	private void checkBlock(final Block source, final Block check, final BlockDistanceQueue queue, final Set<Block> checkedBlocks) {
		if(checkedBlocks.contains(check)) {
			// if the block was already checked we abort to prevent circulation
			return;
		}
		
		// if the block is not a log its not added to the queue
		if(!Tag.LOGS.isTagged(check.getType())) {
			return;
		}
		
		// we only add the block to checkedBlocks if it was a log
		// this way a recursive call can determine if or not to check the blocks around its upper and lower block depending on it being in the checked list
		checkedBlocks.add(check);
		final double x = Math.pow(source.getX() - check.getX(), 2);
		final double y = Math.pow(source.getY() - check.getY(), 2);
		final double z = Math.pow(source.getZ() - check.getZ(), 2);
		// we skip getting the squareroot here, because its performance heavy and doesn't change the order
		final double distance = x + y + z;
		queue.add(check, distance);
		
		final Block upper = check.getRelative(BlockFace.UP);
		checkBlock(source, upper, queue, checkedBlocks);
		if(!checkedBlocks.contains(upper)) {
			for(final BlockFace direction: EventListener.NEIGHBORS) {
				final Block neighbor = upper.getRelative(direction);
				checkBlock(source, neighbor, queue, checkedBlocks);
			}
		}
		
		for(final BlockFace direction: EventListener.NEIGHBORS) {
			final Block neighbor = check.getRelative(direction);
			checkBlock(source, neighbor, queue, checkedBlocks);
		}
		
		final Block lower = check.getRelative(BlockFace.DOWN);
		checkBlock(source, lower, queue, checkedBlocks);
		if(!checkedBlocks.contains(lower)) {
			for(final BlockFace diretion: EventListener.NEIGHBORS) {
				final Block neighbor = lower.getRelative(diretion);
				checkBlock(source, neighbor, queue, checkedBlocks);
			}
		}
	}
	
	/**
	 * fast decays all decayable leaves around the given block
	 * @param block
	 */
	private void decaySurroundingLeaves(final Block block) {
		// disabled in config -> abort
		if(!this.fastLeafDecay) {
			return;
		}
		
		// this method is called during break events, the block however is broken after the event
		// so we have to wait for the block to actually break before checking the leaves for decaying
		// for some reason we need to wait at least six ticks, otherwise the leaves might be considered persistent by the server
		new BukkitRunnable() {
			@Override
			public void run() {
				for(final BlockFace direction: EventListener.LEAF_NEIGHBORS) {
					decayLeaf(block.getRelative(direction));
				}
			}
		}.runTaskLater(plugin, 6);
	}
	
	/**
	 * checks if the given block is a leaf that would decay at some point and if so makes it decay within the next half second, 
	 * this function also calls a LeavesDecayEvent, which will then result in recursion until the whole treetop is gone
	 * @param block the block to decay
	 */
	private void decayLeaf(final Block block) {
		// block is not a leaf -> abort
		if(!Tag.LEAVES.isTagged(block.getType())) {
			return;
		}
		
		final Optional<Boolean> optionalDecay = MetadataUtils.getMetadata(plugin, block, EventListener.METADATA_KEY_LEAF_DECAY, Boolean.class);
		// block already is set to decay -> abort
		if(optionalDecay.isPresent() && optionalDecay.get()) {
			return;
		}

		final Leaves leaves = (Leaves) block.getBlockData();
		// leaf was set by a player -> abort
		if(leaves.isPersistent()) {
			return;
		}
		
		// leaf is near a log -> abort
		if(leaves.getDistance() < 7) {
			return;
		}
		
		block.setMetadata(EventListener.METADATA_KEY_LEAF_DECAY, new FixedMetadataValue(plugin, true));
		
		new BukkitRunnable() {
			@Override
			public void run() {
				// chunk got unloaded during the delay -> abort
				if(!block.getChunk().isLoaded()) {
					block.removeMetadata(EventListener.METADATA_KEY_LEAF_DECAY, plugin);
					return;
				}
				
				// block was replaced -> abort
				if(!Tag.LEAVES.isTagged(block.getType())) {
					block.removeMetadata(EventListener.METADATA_KEY_LEAF_DECAY, plugin);
					return;
				}
				
				final LeavesDecayEvent event = new LeavesDecayEvent(block);
				plugin.getServer().getPluginManager().callEvent(event);
				
				// something cancelled the decay event -> abort
				if(event.isCancelled()) {
					block.removeMetadata(EventListener.METADATA_KEY_LEAF_DECAY, plugin);
					return;
				}
				
				final World world = block.getWorld();
				final Location location = block.getLocation();
				if(EventListener.this.leafDecaySound){
					world.playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.05f, 1.2f);
				}
				if(EventListener.this.leafDecayParticles) {
					world.spawnParticle(Particle.BLOCK_DUST, location.add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0, block.getType().createBlockData());
				}
				
				block.breakNaturally();
				block.removeMetadata(EventListener.METADATA_KEY_LEAF_DECAY, plugin);
			}
		}.runTaskLater(plugin, 3 + new Random().nextInt(7));
	}

}
