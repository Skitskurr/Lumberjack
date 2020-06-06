package com.skitskurr.lumberjack.utils;

import org.bukkit.block.Block;

public class BlockDistanceQueue {
	
	private class Knot{
		private final Block block;
		private final double distance;
		private Knot next = null;
		
		private Knot(final Block block, final double distance) {
			this.block = block;
			this.distance = distance;
		}
	}
	
	private Knot current = null;
	
	public void add(final Block block, final double distance) {
		final Knot temp = new Knot(block, distance);
		if(this.current == null) {
			this.current = temp;
			return;
		}
		if(this.current.distance < distance) {
			temp.next = this.current;
			this.current = temp;
			return;
		}
		Knot loop = this.current;
		while(loop.next != null) {
			if(loop.next.distance < distance) {
				temp.next = loop.next;
				loop.next = temp;
				return;
			}
			loop = loop.next;
		}
		loop.next = temp;
	}
	
	public boolean isEmpty() {
		return this.current == null;
	}
	
	public boolean isLast() {
		return this.current == null || this.current.next == null;
	}
	
	public Block poll() {
		final Knot temp = this.current;
		this.current = this.current.next;
		return temp.block;
	}

}
