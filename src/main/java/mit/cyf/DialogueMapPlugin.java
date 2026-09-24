package mit.cyf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mit.cyf.dialogue.DialogueDialogFactory;
import mit.cyf.dialogue.DialogueRenderer;
import mit.cyf.dialogue.DialogueScreen;
import mit.cyf.dialogue.RenderedDialogue;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Runtime and pack builder for an asset-defined DialogueMap UI. */
public final class DialogueMapPlugin extends JavaPlugin implements Listener {
	private final Map<UUID, MapScreenFactory.MapViewport> mapViewports = new ConcurrentHashMap<>();
	private DialogueRenderer dialogueRenderer;
	private DialogueDialogFactory dialogFactory;
	private MapResourcePackService resourcePacks;
	private boolean building;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();
		dialogueRenderer = new DialogueRenderer();
		dialogFactory = new DialogueDialogFactory();
		try {
			resourcePacks = new MapResourcePackService(this);
			resourcePacks.start();
			getServer().getPluginManager().registerEvents(this, this);
			getLogger().info("DialogueMap asset-defined runtime enabled.");
		} catch (IOException exception) {
			getLogger().severe("DialogueMap resource-pack service could not start: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		if (resourcePacks != null) resourcePacks.stop();
	}

	@EventHandler
	private void onPlayerJoin(PlayerJoinEvent event) {
		resourcePacks.send(event.getPlayer());
	}

	@EventHandler
	private void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		if (resourcePacks.isOurPackEvent(event)) {
			getLogger().info(() -> "DialogueMap resource pack for " + event.getPlayer().getName() + ": " + event.getStatus());
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
		if (!command.getName().equalsIgnoreCase("dialoguemap")) return false;
		String action = arguments.length == 0 ? "open" : arguments[0].toLowerCase(java.util.Locale.ROOT);
		switch (action) {
			case "open", "test" -> open(sender, null);
			case "build" -> build(sender);
			case "reload-assets" -> reloadAssets(sender);
			case "close" -> close(sender);
			case "pan" -> pan(sender, arguments);
			case "zoom" -> zoom(sender, arguments);
			default -> sender.sendMessage("Usage: /dialoguemap <open|build|reload-assets|pan|zoom|close>");
		}
		return true;
	}

	private void build(CommandSender sender) {
		if (building) {
			sender.sendMessage("DialogueMap is already building the resource pack.");
			return;
		}
		MapDefinition definition;
		try {
			definition = MapDefinition.load(this);
		} catch (IOException | IllegalArgumentException exception) {
			sender.sendMessage("DialogueMap cannot build: " + exception.getMessage());
			return;
		}
		building = true;
		sender.sendMessage("DialogueMap is building the resource pack from the configured assets.");
		Path output = getDataFolder().toPath().resolve(getConfig().getString("build.output-directory", "generated-resource-pack")).normalize();
		getServer().getScheduler().runTaskAsynchronously(this, () -> {
			try {
				ResourcePackWriter.write(output, definition);
				getServer().getScheduler().runTask(this, () -> {
					building = false;
					resourcePacks.sendAll();
					sender.sendMessage("DialogueMap build complete. The new pack was sent to online players.");
				});
			} catch (IOException | RuntimeException exception) {
				getServer().getScheduler().runTask(this, () -> {
					building = false;
					getLogger().severe("DialogueMap build failed: " + exception.getMessage());
					sender.sendMessage("DialogueMap build failed; see the server log.");
				});
			}
		});
	}

	private void reloadAssets(CommandSender sender) {
		try {
			MapDefinition definition = MapDefinition.load(this);
			mapViewports.clear();
			sender.sendMessage("DialogueMap assets are valid: " + definition.mapWidth() + "x" + definition.mapHeight()
				+ " map; " + definition.ui().viewportWidth() + "x" + definition.ui().viewportHeight() + " detected aperture.");
		} catch (IOException | IllegalArgumentException exception) {
			sender.sendMessage("DialogueMap asset validation failed: " + exception.getMessage());
		}
	}

	private void open(CommandSender sender, MapScreenFactory.MapViewport requested) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("This command opens a dialog and must be run by a player.");
			return;
		}
		try {
			MapDefinition definition = MapDefinition.load(this);
			MapScreenFactory.MapViewport viewport = requested == null ? MapScreenFactory.initialViewport(definition, player.getLocation()) : requested;
			DialogueScreen screen = MapScreenFactory.create(definition, viewport, player.getLocation());
			RenderedDialogue rendered = dialogueRenderer.render(screen);
			player.showDialog(dialogFactory.create(screen, rendered.component()));
			mapViewports.put(player.getUniqueId(), viewport);
		} catch (IOException | IllegalArgumentException exception) {
			sender.sendMessage("DialogueMap cannot open: " + exception.getMessage());
		}
	}

	private void pan(CommandSender sender, String[] arguments) {
		if (!(sender instanceof Player player)) return;
		if (arguments.length != 2) {
			sender.sendMessage("Usage: /dialoguemap pan <north|south|east|west>");
			return;
		}
		int[] direction = switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
			case "north" -> new int[] {0, -1};
			case "south" -> new int[] {0, 1};
			case "west" -> new int[] {-1, 0};
			case "east" -> new int[] {1, 0};
			default -> null;
		};
		if (direction == null) {
			sender.sendMessage("Map direction must be north, south, east, or west.");
			return;
		}
		try {
			MapDefinition definition = MapDefinition.load(this);
			MapScreenFactory.MapViewport current = mapViewports.getOrDefault(player.getUniqueId(), MapScreenFactory.initialViewport(definition, player.getLocation()));
			open(player, MapScreenFactory.pan(definition, current, direction[0], direction[1]));
		} catch (IOException | IllegalArgumentException exception) {
			sender.sendMessage("DialogueMap cannot pan: " + exception.getMessage());
		}
	}

	private void zoom(CommandSender sender, String[] arguments) {
		if (!(sender instanceof Player player)) return;
		if (arguments.length != 2 || (!arguments[1].equalsIgnoreCase("in") && !arguments[1].equalsIgnoreCase("out"))) {
			sender.sendMessage("Usage: /dialoguemap zoom <in|out>");
			return;
		}
		try {
			MapDefinition definition = MapDefinition.load(this);
			MapScreenFactory.MapViewport current = mapViewports.getOrDefault(player.getUniqueId(), MapScreenFactory.initialViewport(definition, player.getLocation()));
			open(player, MapScreenFactory.zoom(definition, current, arguments[1].equalsIgnoreCase("in")));
		} catch (IOException | IllegalArgumentException exception) {
			sender.sendMessage("DialogueMap cannot zoom: " + exception.getMessage());
		}
	}

	private void close(CommandSender sender) {
		if (!(sender instanceof Player player)) return;
		mapViewports.remove(player.getUniqueId());
		try {
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object connection = handle.getClass().getField("connection").get(handle);
			Class<?> packetClass = Class.forName("net.minecraft.network.protocol.common.ClientboundClearDialogPacket");
			Object packet = packetClass.getField("INSTANCE").get(null);
			for (java.lang.reflect.Method method : connection.getClass().getMethods()) {
				if (method.getName().equals("send") && method.getParameterCount() == 1) {
					method.invoke(connection, packet);
					return;
				}
			}
		} catch (ReflectiveOperationException exception) {
			getLogger().warning("Could not close DialogueMap dialog: " + exception.getMessage());
		}
	}
}
