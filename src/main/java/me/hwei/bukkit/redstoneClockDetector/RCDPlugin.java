package me.hwei.bukkit.redstoneClockDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import me.hwei.bukkit.redstoneClockDetector.commands.BreakCommand;
import me.hwei.bukkit.redstoneClockDetector.commands.ListCommand;
import me.hwei.bukkit.redstoneClockDetector.commands.StartCommand;
import me.hwei.bukkit.redstoneClockDetector.commands.StatusCommand;
import me.hwei.bukkit.redstoneClockDetector.commands.StopCommand;
import me.hwei.bukkit.redstoneClockDetector.commands.TeleportCommand;
import me.hwei.bukkit.redstoneClockDetector.util.AbstractCommand;
import me.hwei.bukkit.redstoneClockDetector.util.IOutput;
import me.hwei.bukkit.redstoneClockDetector.util.OutputManager;
import me.hwei.bukkit.redstoneClockDetector.util.PermissionsException;
import me.hwei.bukkit.redstoneClockDetector.util.UsageException;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RCDPlugin extends JavaPlugin implements CommandExecutor, Listener {

	@Override
	public void onDisable() {
		this.stop();
		this.redstoneActivityTable = null;
		this.redstoneActivityList = null;
		this.toConsole.output("Disabled.");
	}

	@Override
	public void onEnable() {
		IOutput toConsole = message -> getServer().getConsoleSender().sendMessage(message);
		IOutput toAll = message -> getServer().broadcast(Component.text(message));
		OutputManager.IPlayerGetter playerGetter = name -> getServer().getPlayer(name);
		String pluginName = this.getDescription().getName();
		OutputManager.Setup(
				"[" + ChatColor.YELLOW + pluginName + ChatColor.WHITE + "] ",
				toConsole, toAll, playerGetter);
		this.toConsole = OutputManager.GetInstance().prefix(toConsole);
		this.toConsole.output("Enabled.");
		
	
		this.redstoneActivityTable = new HashMap<>();
		this.redstoneActivityList = new ArrayList<>();
		this.stop();
		
		this.setupCommands();
		
		this.getServer().getPluginManager().registerEvents(this, this);
	}
	
	protected void setupCommands() {
		try {
			ListCommand listCommand = new ListCommand(
					"list [page]  List locations of redstone activities.",
					"redstoneclockdetector.list",
					null, this);
			AbstractCommand[] childCommands = new AbstractCommand[] {
					new StartCommand(
							"<sec>  Start scan for <sec> seconds.",
							"redstoneclockdetector.start",
							null, this, listCommand),
					new StopCommand(
							"stop  Stop scan.",
							"redstoneclockdetector.stop",
							null, this),
					listCommand,
					new TeleportCommand(
							"tp [player] [num]  Teleport player [player] to place of number [num] in list.",
							"redstoneclockdetector.tp",
							null, this),
					new BreakCommand(
							"break <num>  Break the block at place of number <num> in list.",
							"redstoneclockdetector.break",
							null, this),
			};
			
			this.topCommand = new StatusCommand(
					"  Status of plugin.",
					"redstoneclockdetector",
					childCommands, this);
			
		} catch (Exception e) {
			this.toConsole.output("Can not setup commands!");
			e.printStackTrace();
		}
	}
	
	public List<Entry<Location, Integer>> getRedstoneActivityList() {
		return this.redstoneActivityList;
	}
	public CommandSender getUser() {
		return this.sender;
	}
	public int getSecondsRemain() {
		if(this.taskId == Integer.MIN_VALUE)
			return -1;
		return this.worker.getSecondsRemain();
	}
	public interface IProgressReporter {
		void onProgress(int secondsRemain);
	}
	protected class Worker implements Runnable {
		public Worker(int seconds, IProgressReporter progressReporter) {
			this.progressReporter = progressReporter;
			this.secondsRemain = seconds;
		}
		@Override
		public void run() {
			
			
			
			if(this.secondsRemain <= 0)
			{
				if(RCDPlugin.this.stop() && this.progressReporter != null)
					this.progressReporter.onProgress(secondsRemain);
			} else {
				if(this.progressReporter != null)
					this.progressReporter.onProgress(secondsRemain);
				this.secondsRemain--;
			}

		}
		public int getSecondsRemain() {
			return this.secondsRemain;
		}
		protected IProgressReporter progressReporter;
		protected int secondsRemain;
	}
	public void start(CommandSender sender, int seconds, IProgressReporter progressReporter ) {
		if(this.taskId != Integer.MIN_VALUE)
			return;
		this.sender = sender;
		this.worker = new Worker(seconds, progressReporter);
		this.taskId = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, this.worker, 0L, 20L);
	}
	public boolean stop() {
		if(this.taskId != Integer.MIN_VALUE) {
			this.getServer().getScheduler().cancelTask(this.taskId);
			this.taskId = Integer.MIN_VALUE;
			this.sender = null;
			this.worker = null;
			this.sortList();
			this.redstoneActivityTable.clear();
			return true;
		} else
			return false;
	}
	
	protected void sortList() {
		class ValueComparator implements Comparator<Location> {
			final Map<Location, Integer> base;
			public ValueComparator(Map<Location, Integer> base) {
				this.base = base;
			}
			public int compare(Location a, Location b) {
				if(base.get(a) < base.get(b)) {
					return 1;
				} else if(base.get(a).equals(base.get(b))) {
					return 0;
				} else {
					return -1;
				}
			}
		}
		ValueComparator bvc = new ValueComparator(this.redstoneActivityTable);
		TreeMap<Location, Integer> sortedMap = new TreeMap<>(bvc);
		sortedMap.putAll(this.redstoneActivityTable);
		this.redstoneActivityList.clear();
		this.redstoneActivityList.addAll(sortedMap.entrySet());
	}
	
	@EventHandler
	public void onBlockRedstoneChange(BlockPhysicsEvent event)  {
		if(this.taskId == Integer.MIN_VALUE)
			return;
		Block block = event.getBlock();
		if(block.getBlockPower() == 0)
			return;
		Location loc = event.getBlock().getLocation();
		int count = 1;
		if(this.redstoneActivityTable.containsKey(loc)) {
			count += this.redstoneActivityTable.get(loc);
		}
		this.redstoneActivityTable.put(loc, count);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		try {
			if(!topCommand.execute(sender, args)) {
				topCommand.showUsage(sender, command.getName());
			}
		} catch (PermissionsException e) {
			sender.sendMessage(String.format(ChatColor.RED + "You do not have permission of %s", e.getPerms()));
		} catch (UsageException e) {
			sender.sendMessage("Usage: " + ChatColor.YELLOW + command.getName() + " " + e.getUsage());
			sender.sendMessage(ChatColor.RED + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
	
	
	protected HashMap<Location, Integer> redstoneActivityTable = null;
	protected List<Entry<Location, Integer>> redstoneActivityList = null;
	protected Worker worker = null;
	protected CommandSender sender = null;
	protected int taskId = Integer.MIN_VALUE;
	protected IOutput toConsole = null;
	protected AbstractCommand topCommand = null;
}
