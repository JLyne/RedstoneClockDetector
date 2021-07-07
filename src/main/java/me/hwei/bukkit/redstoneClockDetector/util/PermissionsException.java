package me.hwei.bukkit.redstoneClockDetector.util;

import java.io.Serial;

public class PermissionsException extends Exception {

	public PermissionsException(String perms) {
		this.perms = perms;
	}
	
	public String getPerms() {
		return perms;
	}

	private final String perms;
	
	@Serial
	private static final long serialVersionUID = 1L;
}
