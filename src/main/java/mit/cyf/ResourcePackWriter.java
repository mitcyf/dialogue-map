package mit.cyf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Builds the entire client pack from a {@link MapDefinition}; it has no world-generation concern. */
final class ResourcePackWriter {
	private static final int ATLAS_COLUMNS = 16;
	private static final int MAP_FONT_ASCENT = 15;
	private static final int PLAYER_TEXTURE_MARKER = 0xFF45B2E9;
	private static final int MAP_BORDER_MARKER = 0xFF00005A;
	private static final int PAD_COLOUR = 0xFF182531;

	private ResourcePackWriter() {
	}

	static boolean isSupportedMacroPageUsableSize(int usableSize) {
		return usableSize >= 192 && usableSize <= MapRenderProtocol.MACRO_CELL_SIZE - 2 && usableSize % 2 == 0;
	}

	static void write(Path output, MapDefinition definition) throws IOException {
		BufferedImage source = readImage(definition.mapImage());
		BufferedImage ui = readImage(definition.uiImage());
		BufferedImage player = readImage(definition.playerImage());
		List<BufferedImage> levels = resolutionLevels(source, definition.zoomLevels().size());
		List<BufferedImage> buttonImages = new ArrayList<>();
		for (MapDefinition.Button button : definition.buttons()) buttonImages.add(readImage(button.texture()));

		deleteTree(output);
		Path sourceDirectory = output.resolve("source");
		Path fontDirectory = output.resolve("assets").resolve(MapRenderProtocol.NAMESPACE).resolve("font");
		Path textureDirectory = output.resolve("assets").resolve(MapRenderProtocol.NAMESPACE).resolve("textures").resolve("font");
		Path shaderDirectory = output.resolve("assets/minecraft/shaders/core");
		Files.createDirectories(sourceDirectory);
		Files.createDirectories(fontDirectory);
		Files.createDirectories(textureDirectory);
		Files.createDirectories(shaderDirectory);

		Files.writeString(output.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"description\": \"DialogueMap generated map UI\",\n    \"min_format\": [75, 0],\n    \"max_format\": [75, 0]\n  }\n}\n", StandardCharsets.UTF_8);
		List<LevelLayout> layouts = new ArrayList<>();
		int firstCodepoint = MapRenderProtocol.FIRST_MAP_CODEPOINT;
		for (MapDefinition.ZoomLevel level : definition.zoomLevels()) {
			BufferedImage image = levels.get(level.index());
			ImageIO.write(image, "PNG", sourceDirectory.resolve("map-r" + (level.index() + 1) + ".png").toFile());
			LevelLayout layout = writeLevel(level, image, firstCodepoint, definition.macroPageSize(), fontDirectory, textureDirectory);
			layouts.add(layout);
			firstCodepoint += layout.pageCount;
		}
		ImageIO.write(ui, "PNG", sourceDirectory.resolve("ui.png").toFile());
		ImageIO.write(player, "PNG", sourceDirectory.resolve("player.png").toFile());
		for (int index = 0; index < buttonImages.size(); index++) {
			ImageIO.write(buttonImages.get(index), "PNG", sourceDirectory.resolve("button-" + index + ".png").toFile());
		}
		writeSpacerFont(fontDirectory);
		writeUiFonts(ui, player, definition.buttons(), buttonImages, fontDirectory, textureDirectory);
		writeTextShaders(shaderDirectory, definition);
		writePackIcon(source, output.resolve("pack.png"));
		writeManifest(output.resolve("manifest.json"), definition, layouts);
		writeArchive(output);
	}

	private static BufferedImage readImage(Path path) throws IOException {
		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null) throw new IOException("Cannot decode image: " + path);
		return image;
	}

	private static List<BufferedImage> resolutionLevels(BufferedImage source, int count) {
		List<BufferedImage> levels = new ArrayList<>();
		levels.add(source);
		for (int index = 1; index < count; index++) levels.add(downsample(levels.get(index - 1)));
		return levels;
	}

	static BufferedImage downsample(BufferedImage source) {
		int width = Math.max(1, source.getWidth() / 2);
		int height = Math.max(1, source.getHeight() / 2);
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			long red = 0, green = 0, blue = 0, alpha = 0;
			for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
				int pixel = source.getRGB(Math.min(source.getWidth() - 1, x * 2 + dx), Math.min(source.getHeight() - 1, y * 2 + dy));
				alpha += pixel >>> 24;
				red += pixel >>> 16 & 0xFF;
				green += pixel >>> 8 & 0xFF;
				blue += pixel & 0xFF;
			}
			result.setRGB(x, y, (int) (alpha / 4) << 24 | (int) (red / 4) << 16 | (int) (green / 4) << 8 | (int) (blue / 4));
		}
		return result;
	}

	private static LevelLayout writeLevel(MapDefinition.ZoomLevel level, BufferedImage source, int firstCodepoint, int usableSize,
		Path fontDirectory, Path textureDirectory) throws IOException {
		int border = (MapRenderProtocol.MACRO_CELL_SIZE - usableSize) / 2;
		int pagesWide = ceilDiv(source.getWidth(), usableSize);
		int pagesHigh = ceilDiv(source.getHeight(), usableSize);
		int pageCount = pagesWide * pagesHigh;
		List<MapAtlas> atlases = new ArrayList<>();
		for (int pageOffset = 0, atlasIndex = 0; pageOffset < pageCount; atlasIndex++) {
			int pagesInAtlas = Math.min(pageCount - pageOffset, ATLAS_COLUMNS * ATLAS_COLUMNS);
			int columns = Math.min(ATLAS_COLUMNS, (int) Math.ceil(Math.sqrt(pagesInAtlas)));
			int rows = ceilDiv(pagesInAtlas, columns);
			BufferedImage atlas = new BufferedImage(MapRenderProtocol.MACRO_CELL_SIZE * columns, MapRenderProtocol.MACRO_CELL_SIZE * rows,
				BufferedImage.TYPE_INT_ARGB);
			for (int cell = 0; cell < pagesInAtlas; cell++) {
				int pageIndex = pageOffset + cell;
				BufferedImage page = createMacroPage(source, (pageIndex % pagesWide) * usableSize, (pageIndex / pagesWide) * usableSize,
					usableSize, border);
				atlas.setRGB((cell % columns) * MapRenderProtocol.MACRO_CELL_SIZE, (cell / columns) * MapRenderProtocol.MACRO_CELL_SIZE,
					MapRenderProtocol.MACRO_CELL_SIZE, MapRenderProtocol.MACRO_CELL_SIZE,
					page.getRGB(0, 0, MapRenderProtocol.MACRO_CELL_SIZE, MapRenderProtocol.MACRO_CELL_SIZE, null, 0, MapRenderProtocol.MACRO_CELL_SIZE),
					0, MapRenderProtocol.MACRO_CELL_SIZE);
			}
			String filename = "map-r" + (level.index() + 1) + "-macro-" + String.format("%03d", atlasIndex) + ".png";
			ImageIO.write(atlas, "PNG", textureDirectory.resolve(filename).toFile());
			atlases.add(new MapAtlas(filename, columns, rows, pageOffset, pagesInAtlas));
			pageOffset += pagesInAtlas;
		}
		StringBuilder font = new StringBuilder("{\n  \"providers\": [\n");
		for (int index = 0; index < atlases.size(); index++) {
			MapAtlas atlas = atlases.get(index);
			if (index > 0) font.append(",\n");
			font.append("    {\"type\":\"bitmap\",\"file\":\"").append(MapRenderProtocol.NAMESPACE).append(":font/")
				.append(atlas.filename).append("\",\"height\":254,\"ascent\":").append(MAP_FONT_ASCENT).append(",\"chars\":[");
			for (int row = 0; row < atlas.rows; row++) {
				if (row > 0) font.append(',');
				font.append('"');
				for (int column = 0; column < atlas.columns; column++) {
					int page = atlas.pageOffset + row * atlas.columns + column;
					font.appendCodePoint(page < atlas.pageOffset + atlas.pagesInAtlas ? firstCodepoint + page : 0);
				}
				font.append('"');
			}
			font.append("]}");
		}
		Files.writeString(fontDirectory.resolve("map-r" + (level.index() + 1) + ".json"), font.append("\n  ]\n}\n").toString(), StandardCharsets.UTF_8);
		return new LevelLayout(level, pagesWide, pagesHigh, pageCount, atlases.size(), firstCodepoint);
	}

	private static BufferedImage createMacroPage(BufferedImage source, int sourceLeft, int sourceTop, int usableSize, int border) {
		BufferedImage page = new BufferedImage(MapRenderProtocol.MACRO_CELL_SIZE, MapRenderProtocol.MACRO_CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = page.createGraphics();
		graphics.setColor(new Color(PAD_COLOUR, true));
		graphics.fillRect(border, border, usableSize, usableSize);
		graphics.dispose();
		for (int y = 0; y < usableSize; y++) for (int x = 0; x < usableSize; x++) {
			int sourceX = sourceLeft + x;
			int sourceY = sourceTop + y;
			if (sourceX < source.getWidth() && sourceY < source.getHeight()) page.setRGB(border + x, border + y, source.getRGB(sourceX, sourceY));
			int pixel = page.getRGB(border + x, border + y);
			page.setRGB(border + x, border + y, 0x01000000 | pixel & 0x00FFFFFF);
		}
		for (int coordinate = 0; coordinate < MapRenderProtocol.MACRO_CELL_SIZE; coordinate++) {
			page.setRGB(coordinate, 0, 0xFF000000);
			page.setRGB(coordinate, MapRenderProtocol.MACRO_CELL_SIZE - 1, 0xFF000000);
			page.setRGB(0, coordinate, 0xFF000000);
			page.setRGB(MapRenderProtocol.MACRO_CELL_SIZE - 1, coordinate, 0xFF000000);
		}
		page.setRGB(1, 0, MAP_BORDER_MARKER);
		return page;
	}

	private static void writeSpacerFont(Path fontDirectory) throws IOException {
		StringBuilder json = new StringBuilder("{\n  \"providers\": [{\n    \"type\": \"space\",\n    \"advances\": {\n");
		int index = 0;
		for (int pixels = 1; pixels <= 512; pixels <<= 1) {
			if (index++ > 0) json.append(",\n");
			json.append("      \"").appendCodePoint(MapRenderProtocol.SPACER_CODEPOINT_START + index - 1).append("\": ").append(pixels);
		}
		for (int pixels = 1; pixels <= 512; pixels <<= 1) {
			json.append(",\n      \"").appendCodePoint(MapRenderProtocol.SPACER_CODEPOINT_START + index++).append("\": -").append(pixels);
		}
		Files.writeString(fontDirectory.resolve("spacers.json"), json.append("\n    }\n  }]\n}\n").toString(), StandardCharsets.UTF_8);
	}

	private static void writeUiFonts(BufferedImage ui, BufferedImage player, List<MapDefinition.Button> buttons, List<BufferedImage> buttonImages,
		Path fontDirectory, Path textureDirectory) throws IOException {
		writeTexture(ui, textureDirectory.resolve("ui.png"));
		Files.writeString(fontDirectory.resolve("ui.json"), bitmapFontJson(List.of(new Glyph(MapRenderProtocol.UI_CODEPOINT, "ui.png", ui.getWidth(), ui.getHeight(), 11))), StandardCharsets.UTF_8);
		player.setRGB(0, 0, PLAYER_TEXTURE_MARKER);
		writeTexture(player, textureDirectory.resolve("player.png"));
		Files.writeString(fontDirectory.resolve("player.json"), bitmapFontJson(List.of(new Glyph(MapRenderProtocol.PLAYER_CODEPOINT, "player.png", 16, 16, 15))), StandardCharsets.UTF_8);
		List<Glyph> variants = new ArrayList<>();
		for (int index = 0; index < buttons.size(); index++) {
			MapDefinition.Button button = buttons.get(index);
			String filename = "button-" + index + ".png";
			writeTexture(buttonImages.get(index), textureDirectory.resolve(filename));
			int first = MapRenderProtocol.FIRST_BUTTON_CODEPOINT + index * MapRenderProtocol.BUTTON_VARIANTS;
			for (int variant = 0; variant < MapRenderProtocol.BUTTON_VARIANTS; variant++) {
				variants.add(new Glyph(first + variant, filename, button.width(), button.height(), 15 - variant));
			}
		}
		Files.writeString(fontDirectory.resolve("buttons.json"), bitmapFontJson(variants), StandardCharsets.UTF_8);
	}

	private static void writeTexture(BufferedImage image, Path destination) throws IOException {
		ensureBitmapAdvance(image);
		ImageIO.write(image, "PNG", destination.toFile());
	}

	private static void ensureBitmapAdvance(BufferedImage image) {
		int lastColumn = image.getWidth() - 1;
		for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(lastColumn, y) >>> 24) != 0) return;
		image.setRGB(lastColumn, image.getHeight() - 1, 0x01000000 | image.getRGB(lastColumn, image.getHeight() - 1) & 0x00FFFFFF);
	}

	private static String bitmapFontJson(List<Glyph> glyphs) {
		StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
		for (int index = 0; index < glyphs.size(); index++) {
			Glyph glyph = glyphs.get(index);
			if (index > 0) json.append(",\n");
			json.append("    {\"type\":\"bitmap\",\"file\":\"").append(MapRenderProtocol.NAMESPACE).append(":font/")
				.append(glyph.filename).append("\",\"height\":").append(glyph.height).append(",\"ascent\":").append(glyph.ascent)
				.append(",\"chars\":[\"").appendCodePoint(glyph.codepoint).append("\"]}");
		}
		return json.append("\n  ]\n}\n").toString();
	}

	private static void writeTextShaders(Path shaderDirectory, MapDefinition definition) throws IOException {
		int border = (MapRenderProtocol.MACRO_CELL_SIZE - definition.macroPageSize()) / 2;
		String vertex = """
			#version 330
			#moj_import <minecraft:fog.glsl>
			#moj_import <minecraft:dynamictransforms.glsl>
			#moj_import <minecraft:globals.glsl>
			#moj_import <minecraft:projection.glsl>
			in vec3 Position;
			in vec4 Color;
			in vec2 UV0;
			in ivec2 UV2;
			uniform sampler2D Sampler0;
			uniform sampler2D Sampler2;
			out float sphericalVertexDistance;
			out float cylindricalVertexDistance;
			out vec4 vertexColor;
			out vec2 texCoord0;
			flat out vec4 mapMetadata;
			flat out int mapGlyph;
			flat out int playerGlyph;
			const int MAP_DATA_SIZE = %d;
			const ivec4 MAP_ATLAS_SIGNATURE = ivec4(0, 0, 90, 255);
			void main() {
			  gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
			  sphericalVertexDistance = fog_spherical_distance(Position);
			  cylindricalVertexDistance = fog_cylindrical_distance(Position);
			  vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
			  texCoord0 = UV0;
			  mapMetadata = Color;
			  bool isMapAtlas = all(equal(ivec4(round(texelFetch(Sampler0, ivec2(1, 0), 0) * 255.0)), MAP_ATLAS_SIGNATURE));
			  int blue = int(floor(Color.b * 255.0 + 0.5));
			  int slot = blue >= 48 && blue <= 56 ? blue - 48 : -1;
			  mapGlyph = isMapAtlas ? (slot >= 0 ? 1 : 2) : 0;
			  bool hasPlayerMetadata = !isMapAtlas && blue >= 128 && blue <= 159;
			  bool isPlayerShadow = !isMapAtlas && blue >= 16 && blue <= 64;
			  playerGlyph = hasPlayerMetadata ? 1 : (isPlayerShadow ? 2 : 0);
			  if (mapGlyph == 1) {
			    vec2 slotCell = vec2(float(slot %% 3), float(slot / 3));
			    vec2 crop = vec2(float(int(floor(Color.r * 255.0 + 0.5))), float(int(floor(Color.g * 255.0 + 0.5))));
			    vec2 offset = slotCell * float(MAP_DATA_SIZE) - crop;
			    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy + offset, Position.z, 1.0);
			  }
			  if (playerGlyph == 1) {
			    int corner = gl_VertexID %% 4;
			    vec2 local = corner == 0 ? vec2(0.0) : (corner == 1 ? vec2(0.0, 16.0) : (corner == 2 ? vec2(16.0) : vec2(16.0, 0.0)));
			    int payload = blue - 128;
			    int x = int(floor(Color.r * 255.0 + 0.5)) + (payload / 16) * 256;
			    int y = int(floor(Color.g * 255.0 + 0.5));
			    float angle = float(payload %% 16) * 6.28318530718 / 16.0;
			    mat2 rotateClockwise = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));
			    vec2 destination = Position.xy - local + vec2(float(x), float(y)) + rotateClockwise * (local - vec2(8.0));
			    gl_Position = ProjMat * ModelViewMat * vec4(destination, Position.z, 1.0);
			  }
			}
			""".formatted(definition.macroPageSize());
		String fragment = """
			#version 330
			#moj_import <minecraft:fog.glsl>
			#moj_import <minecraft:dynamictransforms.glsl>
			uniform sampler2D Sampler0;
			in float sphericalVertexDistance;
			in float cylindricalVertexDistance;
			in vec4 vertexColor;
			in vec2 texCoord0;
			flat in vec4 mapMetadata;
			flat in int mapGlyph;
			flat in int playerGlyph;
			out vec4 fragColor;
			const int MAP_CELL_SIZE = 256;
			const int MAP_BORDER = %d;
			const int MAP_DATA_SIZE = %d;
			const int MAP_VIEWPORT_WIDTH = %d;
			const int MAP_VIEWPORT_HEIGHT = %d;
			const ivec4 PLAYER_ATLAS_SIGNATURE = ivec4(69, 178, 233, 255);
			vec4 mapColor() {
			  vec2 atlasSize = vec2(textureSize(Sampler0, 0));
			  vec2 atlasPixel = texCoord0 * atlasSize;
			  vec2 cell = floor(atlasPixel / float(MAP_CELL_SIZE)) * float(MAP_CELL_SIZE);
			  vec2 local = atlasPixel - cell;
			  int red = int(floor(mapMetadata.r * 255.0 + 0.5));
			  int green = int(floor(mapMetadata.g * 255.0 + 0.5));
			  int slot = int(floor(mapMetadata.b * 255.0 + 0.5)) - 48;
			  vec2 viewport = vec2(float(slot %% 3), float(slot / 3)) * float(MAP_DATA_SIZE) - vec2(float(red), float(green)) + (local - vec2(float(MAP_BORDER)));
			  if (viewport.x < 0.0 || viewport.y < 0.0 || viewport.x >= float(MAP_VIEWPORT_WIDTH) || viewport.y >= float(MAP_VIEWPORT_HEIGHT)) discard;
			  ivec2 pixel = clamp(ivec2(floor(atlasPixel)), ivec2(0), ivec2(atlasSize) - ivec2(1));
			  if (local.x < 1.0) pixel.x++; else if (local.x >= 255.0) pixel.x--;
			  if (local.y < 1.0) pixel.y++; else if (local.y >= 255.0) pixel.y--;
			  return vec4(texelFetch(Sampler0, pixel, 0).rgb, 1.0);
			}
			void main() {
			  if (mapGlyph == 2 || playerGlyph == 2) discard;
			  if (playerGlyph == 1) {
			    ivec2 size = textureSize(Sampler0, 0);
			    ivec2 pixel = clamp(ivec2(floor(texCoord0 * vec2(size))), ivec2(0), size - ivec2(1));
			    if (all(equal(ivec4(round(texelFetch(Sampler0, pixel, 0) * 255.0)), PLAYER_ATLAS_SIGNATURE))) discard;
			  }
			  vec4 color = mapGlyph == 1 ? mapColor() : (playerGlyph == 1 ? texture(Sampler0, texCoord0) : texture(Sampler0, texCoord0) * vertexColor * ColorModulator);
			  if (color.a < 0.1) discard;
			  fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
			}
			""".formatted(border, definition.macroPageSize(), definition.ui().viewportWidth(), definition.ui().viewportHeight());
		Files.writeString(shaderDirectory.resolve("rendertype_text.vsh"), vertex, StandardCharsets.UTF_8);
		Files.writeString(shaderDirectory.resolve("rendertype_text.fsh"), fragment, StandardCharsets.UTF_8);
	}

	private static void writePackIcon(BufferedImage source, Path output) throws IOException {
		BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(source, 0, 0, 64, 64, null);
		graphics.dispose();
		ImageIO.write(icon, "PNG", output.toFile());
	}

	private static void writeManifest(Path output, MapDefinition definition, List<LevelLayout> levels) throws IOException {
		StringBuilder json = new StringBuilder("{\n  \"format\": 5,\n  \"origin\": {\"x\": ").append(definition.originX())
			.append(", \"y\": ").append(definition.originY()).append("},\n  \"ui\": {\"width\": ").append(definition.ui().width())
			.append(", \"height\": ").append(definition.ui().height()).append(", \"mapViewport\": {\"x\": ").append(definition.ui().viewportX())
			.append(", \"y\": ").append(definition.ui().viewportY()).append(", \"width\": ").append(definition.ui().viewportWidth())
			.append(", \"height\": ").append(definition.ui().viewportHeight()).append("}},\n  \"levels\": [\n");
		for (int index = 0; index < levels.size(); index++) {
			LevelLayout level = levels.get(index);
			if (index > 0) json.append(",\n");
			json.append("    {\"level\": ").append(level.level.index() + 1).append(", \"blocksPerPixel\": ").append(level.level.blocksPerPixel())
				.append(", \"pagesWide\": ").append(level.pagesWide).append(", \"pagesHigh\": ").append(level.pagesHigh)
				.append(", \"codepointStart\": \"U+").append(Integer.toHexString(level.firstCodepoint).toUpperCase()).append("\"}");
		}
		json.append("\n  ]\n}\n");
		Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
	}

	private static void writeArchive(Path output) throws IOException {
		Path archive = output.resolve("dialogue-map.zip");
		Path temporary = output.resolveSibling("dialogue-map.zip.tmp");
		Files.deleteIfExists(temporary);
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary)); var files = Files.walk(output)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				ZipEntry entry = new ZipEntry(output.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"));
				entry.setTime(0L);
				zip.putNextEntry(entry);
				Files.copy(file, zip);
				zip.closeEntry();
			}
		}
		try {
			Files.move(temporary, archive, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes ignored) throws IOException {
				Files.delete(file);
				return java.nio.file.FileVisitResult.CONTINUE;
			}
			@Override public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
				if (exception != null) throw exception;
				Files.delete(directory);
				return java.nio.file.FileVisitResult.CONTINUE;
			}
		});
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}

	private record MapAtlas(String filename, int columns, int rows, int pageOffset, int pagesInAtlas) { }
	private record Glyph(int codepoint, String filename, int width, int height, int ascent) { }
	private record LevelLayout(MapDefinition.ZoomLevel level, int pagesWide, int pagesHigh, int pageCount, int atlasCount, int firstCodepoint) { }
}
