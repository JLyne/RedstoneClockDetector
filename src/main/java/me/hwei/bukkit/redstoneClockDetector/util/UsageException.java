package me.hwei.bukkit.redstoneClockDetector.util;

import java.io.Serial;

public class UsageException extends Exception {

	public UsageException(String usage, String message) {
		this.usage = usage;
		this.message = message;
	}
	
	public String getUsage() {
		return usage;
	}

	public String getMessage() {
		return message;
	}

	private final String usage;
	private final String message;
	
	@Serial
	private static final long serialVersionUID = 1L;

}
