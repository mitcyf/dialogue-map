package mit.cyf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The complete user-facing map contract. All generated artifacts and runtime layout derive from
 * this one definition; no UI coordinates are compiled into Java or GLSL.
 */
public record MapDefinition(
	Path mapImage,
	Path uiImage,
	Path playerImage,
	int originX,
	int originY,
	int macroPageSize,
	UiLayout ui,
	int mapWidth,
	int mapHeight,
	List<Button> buttons
) {
	public static final int PLAYER_SIZE = 16;

	public MapDefinition {
		Objects.requireNonNull(mapImage, "mapImage");
		Objects.requireNonNull(uiImage, "uiImage");
		Objects.requireNonNull(playerImage, "playerImage");
		Objects.requireNonNull(ui, "ui");
		buttons = List.copyOf(buttons);
		if (!ResourcePackWriter.isSupportedMacroPageUsableSize(macroPageSize)) {
			throw new IllegalArgumentException("macro-page-usable-size must be even and within 192..254");
		}
		if (ui.width < 1 || ui.width > 1008 || ui.height < 1 || ui.height > 1024) {
			throw new IllegalArgumentException("ui.png must fit the vanilla dialogue limit of 1008x1024 pixels");
		}
		if (ui.viewportX < 0 || ui.viewportY < 0 || ui.viewportWidth < 1 || ui.viewportHeight < 1
			|| ui.viewportX + ui.viewportWidth > ui.width || ui.viewportY + ui.viewportHeight > ui.height) {
			throw new IllegalArgumentException("the detected UI map aperture is outside ui.png");
		}
		if (ui.viewportWidth > macroPageSize * 3 || ui.viewportHeight > macroPageSize * 3) {
			throw new IllegalArgumentException("the UI map aperture exceeds the 3x3 page compositor capacity");
		}
		if (mapWidth < ui.viewportWidth || mapHeight < ui.viewportHeight) {
			throw new IllegalArgumentException("map.png must be at least as large as the transparent UI map aperture");
		}
	}

	public static MapDefinition load(JavaPlugin plugin) throws IOException {
		FileConfiguration config = plugin.getConfig();
		Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
		Path map = inputPath(dataDirectory, config.getString("assets.map", "assets/map.png"));
		Path ui = inputPath(dataDirectory, config.getString("assets.ui", "assets/ui.png"));
		Path player = inputPath(dataDirectory, config.getString("assets.player", "assets/player.png"));
		BufferedImage mapImage = readImage(map, "map image");
		BufferedImage uiImage = readImage(ui, "UI image");
		BufferedImage playerImage = readImage(player, "player image");
		if (playerImage.getWidth() != PLAYER_SIZE || playerImage.getHeight() != PLAYER_SIZE) {
			throw new IOException("player image must be exactly 16x16 pixels: " + player);
		}
		UiLayout layout = configuredOrDetectedLayout(config, uiImage);
		int originX = config.getInt("map.top-left.x");
		int originY = config.getInt("map.top-left.y");
		int pageSize = config.getInt("map.macro-page-usable-size", 254);
		List<Button> buttons = loadButtons(config, dataDirectory, uiImage.getWidth(), uiImage.getHeight());
		return new MapDefinition(map, ui, player, originX, originY, pageSize, layout,
			mapImage.getWidth(), mapImage.getHeight(), buttons);
	}

	private static UiLayout configuredOrDetectedLayout(FileConfiguration config, BufferedImage image) throws IOException {
		if (!config.isConfigurationSection("ui.map-viewport")) return UiLayout.detect(image);
		int x = config.getInt("ui.map-viewport.x");
		int y = config.getInt("ui.map-viewport.y");
		int width = config.getInt("ui.map-viewport.width");
		int height = config.getInt("ui.map-viewport.height");
		return new UiLayout(image.getWidth(), image.getHeight(), x, y, width, height);
	}

	public List<ZoomLevel> zoomLevels() {
		List<ZoomLevel> levels = new ArrayList<>();
		int width = mapWidth;
		int height = mapHeight;
		int blocksPerPixel = 1;
		while (true) {
			levels.add(new ZoomLevel(levels.size(), blocksPerPixel, width, height));
			if (width / 2 < ui.viewportWidth || height / 2 < ui.viewportHeight) break;
			width /= 2;
			height /= 2;
			blocksPerPixel *= 2;
		}
		return List.copyOf(levels);
	}

	public int codepointStart(int zoomIndex) {
		int codepoint = MapRenderProtocol.FIRST_MAP_CODEPOINT;
		for (ZoomLevel level : zoomLevels()) {
			if (level.index == zoomIndex) return codepoint;
			codepoint += pageCount(level);
		}
		throw new IllegalArgumentException("Unknown zoom index " + zoomIndex);
	}

	public int pageCount(ZoomLevel level) {
		return ceilDiv(level.width, macroPageSize) * ceilDiv(level.height, macroPageSize);
	}

	public int pagesWide(ZoomLevel level) {
		return ceilDiv(level.width, macroPageSize);
	}

	private static List<Button> loadButtons(FileConfiguration config, Path dataDirectory, int uiWidth, int uiHeight) throws IOException {
		List<Button> buttons = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (Map<?, ?> raw : config.getMapList("buttons")) {
			String id = required(raw, "id");
			if (!ids.add(id)) throw new IOException("button id is duplicated: " + id);
			Path texture = inputPath(dataDirectory, required(raw, "texture"));
			BufferedImage image = readImage(texture, "button '" + id + "' texture");
			int x = integer(raw, "x");
			int y = integer(raw, "y");
			String action = required(raw, "action");
			if (!action.startsWith("/")) action = "/" + action;
			if (x < 0 || y < 0 || x + image.getWidth() > uiWidth || y + image.getHeight() > uiHeight) {
				throw new IOException("button '" + id + "' does not fit inside ui.png");
			}
			buttons.add(new Button(id, texture, x, y, image.getWidth(), image.getHeight(), action));
		}
		return buttons;
	}

	private static Path inputPath(Path dataDirectory, String configuredPath) throws IOException {
		if (configuredPath == null || configuredPath.isBlank()) throw new IOException("asset path is missing");
		Path path = dataDirectory.resolve(configuredPath).normalize();
		if (!path.startsWith(dataDirectory)) throw new IOException("asset path must stay inside the plugin data folder: " + configuredPath);
		return path;
	}

	private static BufferedImage readImage(Path path, String description) throws IOException {
		if (!Files.isRegularFile(path)) throw new IOException("Missing " + description + ": " + path);
		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null) throw new IOException("Cannot decode " + description + ": " + path);
		return image;
	}

	private static String required(Map<?, ?> values, String name) throws IOException {
		Object value = values.get(name);
		if (value == null || value.toString().isBlank()) throw new IOException("button field '" + name + "' is required");
		return value.toString();
	}

	private static int integer(Map<?, ?> values, String name) throws IOException {
		Object value = values.get(name);
		if (!(value instanceof Number number)) throw new IOException("button field '" + name + "' must be an integer");
		return number.intValue();
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}

	public record UiLayout(int width, int height, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
		static UiLayout detect(BufferedImage image) throws IOException {
			int width = image.getWidth();
			int height = image.getHeight();
			boolean[] visited = new boolean[width * height];
			int[] queue = new int[visited.length];
			int bestPixels = 0;
			int bestLeft = 0, bestTop = 0, bestRight = -1, bestBottom = -1;
			for (int start = 0; start < visited.length; start++) {
				if (visited[start] || (image.getRGB(start % width, start / width) >>> 24) != 0) continue;
				int head = 0, tail = 0;
				queue[tail++] = start;
				visited[start] = true;
				int left = start % width, right = left, top = start / width, bottom = top;
				while (head < tail) {
					int pixel = queue[head++];
					int x = pixel % width;
					int y = pixel / width;
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
					for (int neighbour : new int[] {pixel - 1, pixel + 1, pixel - width, pixel + width}) {
						if (neighbour < 0 || neighbour >= visited.length) continue;
						int neighbourX = neighbour % width;
						int neighbourY = neighbour / width;
						if ((pixel % width == 0 && neighbourX == width - 1) || (pixel % width == width - 1 && neighbourX == 0)
							|| visited[neighbour] || (image.getRGB(neighbourX, neighbourY) >>> 24) != 0) continue;
						visited[neighbour] = true;
						queue[tail++] = neighbour;
					}
				}
				if (tail > bestPixels) {
					bestPixels = tail;
					bestLeft = left;
					bestRight = right;
					bestTop = top;
					bestBottom = bottom;
				}
			}
			if (bestPixels == 0) throw new IOException("ui.png has no transparent map aperture");
			return new UiLayout(width, height, bestLeft, bestTop, bestRight - bestLeft + 1, bestBottom - bestTop + 1);
		}
	}

	public record Button(String id, Path texture, int x, int y, int width, int height, String action) {
	}

	public record ZoomLevel(int index, int blocksPerPixel, int width, int height) {
	}
}
