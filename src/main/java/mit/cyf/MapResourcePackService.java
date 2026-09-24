package mit.cyf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Hosts and requires the generated DialogueMap resource pack. */
final class MapResourcePackService {
	private static final UUID PACK_ID = UUID.fromString("ea8f7c1e-1c47-4b84-a99f-bdc921f4dfdb");
	private static final String DOWNLOAD_PATH = "/dialogue-map.zip";

	private final JavaPlugin plugin;
	private final Path archive;
	private final URI publicUri;
	private final boolean required;
	private final Component prompt;
	private HttpServer server;
	private byte[] currentHash;

	MapResourcePackService(JavaPlugin plugin) {
		this.plugin = plugin;
		String archivePath = plugin.getConfig().getString("resource-pack.archive-path", "generated-resource-pack/dialogue-map.zip");
		this.archive = plugin.getDataFolder().toPath().resolve(archivePath).normalize();
		this.publicUri = URI.create(plugin.getConfig().getString("resource-pack.public-url"));
		this.required = plugin.getConfig().getBoolean("resource-pack.required", true);
		this.prompt = Component.text(plugin.getConfig().getString("resource-pack.prompt", "DialogueMap requires its map resource pack."));
	}

	void start() throws IOException {
		if (!Files.isRegularFile(archive)) {
			throw new IOException("Generated resource-pack archive is missing: " + archive);
		}
		refreshHash();
		String address = plugin.getConfig().getString("resource-pack.bind-address", "127.0.0.1");
		int port = plugin.getConfig().getInt("resource-pack.port", 8123);
		server = HttpServer.create(new InetSocketAddress(address, port), 0);
		server.createContext(DOWNLOAD_PATH, this::handleArchiveRequest);
		server.start();
		plugin.getLogger().info("Serving DialogueMap resource pack at " + publicUri + " (SHA-1 " + HexFormat.of().formatHex(currentHash) + ").");
	}

	void stop() {
		if (server != null) server.stop(0);
	}

	void send(Player player) {
		try {
			refreshHash();
			player.setResourcePack(PACK_ID, publicUri.toASCIIString(), currentHash, prompt, required);
		} catch (IOException exception) {
			plugin.getLogger().warning("Cannot send DialogueMap resource pack to " + player.getName() + ": " + exception.getMessage());
			player.sendMessage(Component.text("DialogueMap's required resource pack is not ready yet."));
		}
	}

	/** Sends a newly built pack to every online player, recalculating its content hash first. */
	void sendAll() {
		for (Player player : plugin.getServer().getOnlinePlayers()) send(player);
	}

	boolean isOurPackEvent(PlayerResourcePackStatusEvent event) {
		return PACK_ID.equals(event.getID());
	}

	private void handleArchiveRequest(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestURI().getPath().equals(DOWNLOAD_PATH)) {
			exchange.sendResponseHeaders(404, -1);
			return;
		}
		if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
			exchange.getResponseHeaders().set("Allow", "GET, HEAD");
			exchange.sendResponseHeaders(405, -1);
			return;
		}
		if (!Files.isRegularFile(archive)) {
			exchange.sendResponseHeaders(503, -1);
			return;
		}
		long size = Files.size(archive);
		exchange.getResponseHeaders().set("Content-Type", "application/zip");
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : size);
		if (exchange.getRequestMethod().equals("GET")) {
			try (OutputStream body = exchange.getResponseBody()) {
				Files.copy(archive, body);
			}
		}
	}

	private void refreshHash() throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			try (var input = Files.newInputStream(archive)) {
				input.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest));
			}
			currentHash = digest.digest();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Java does not provide SHA-1", exception);
		}
	}
}
