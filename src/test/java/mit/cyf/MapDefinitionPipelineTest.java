package mit.cyf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import mit.cyf.dialogue.DialogueScreen;
import mit.cyf.dialogue.DialogueVisual;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapDefinitionPipelineTest {
	private static final MapDefinition DEFINITION = new MapDefinition(
		Path.of("map.png"), Path.of("ui.png"), Path.of("player.png"), -2048, -2048, 254,
		new MapDefinition.UiLayout(448, 384, 32, 32, 384, 256), 4096, 4096, List.of());

	@Test
	void largestTransparentRectangleDefinesTheMapAperture() throws Exception {
		BufferedImage ui = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < ui.getHeight(); y++) for (int x = 0; x < ui.getWidth(); x++) ui.setRGB(x, y, 0xFF123456);
		for (int y = 5; y < 16; y++) for (int x = 7; x < 25; x++) ui.setRGB(x, y, 0x00000000);

		MapDefinition.UiLayout layout = MapDefinition.UiLayout.detect(ui);

		assertEquals(7, layout.viewportX());
		assertEquals(5, layout.viewportY());
		assertEquals(18, layout.viewportWidth());
		assertEquals(11, layout.viewportHeight());
	}

	@Test
	void generatedZoomLevelsStopBeforeTheApertureNoLongerFits() {
		assertEquals(List.of(1, 2, 4, 8), DEFINITION.zoomLevels().stream().map(MapDefinition.ZoomLevel::blocksPerPixel).toList());
	}

	@Test
	void mapTilesShareTheApertureOriginAndEncodeOnlyCropAndSlotMetadata() {
		MapScreenFactory.MapViewport viewport = new MapScreenFactory.MapViewport(0, 0, 0);
		DialogueScreen screen = MapScreenFactory.create(DEFINITION, viewport, null);
		List<DialogueVisual> maps = screen.elements().stream()
			.filter(DialogueVisual.class::isInstance).map(DialogueVisual.class::cast)
			.filter(visual -> containsMapGlyph(visual.glyph())).toList();

		assertEquals(9, maps.size());
		assertEquals(1, maps.stream().map(DialogueVisual::x).distinct().count());
		assertEquals(1, maps.stream().map(DialogueVisual::y).distinct().count());
		assertTrue(maps.stream().allMatch(tile -> tile.x() == 32 && tile.y() == 32 && tile.advance() == 0));
		assertEquals(List.of(48, 49, 50, 51, 52, 53, 54, 55, 56), maps.stream().map(tile -> tile.glyph().color().blue()).toList());
		assertTrue(maps.stream().allMatch(tile -> tile.glyph().color().red() == 0 && tile.glyph().color().green() == 0));
	}

	@Test
	void playerAndZoomUseTheConfiguredWorldOrigin() {
		Location player = new Location(null, -2000, 64, -1900, -90, 0);
		MapScreenFactory.MapViewport initial = MapScreenFactory.initialViewport(DEFINITION, player);
		MapScreenFactory.MapViewport zoomed = MapScreenFactory.zoom(DEFINITION, initial, false);

		assertEquals(0, initial.zoomIndex());
		assertEquals(1, zoomed.zoomIndex());
		assertEquals(16, MapScreenFactory.pan(DEFINITION, zoomed, 1, 0).sourceX() - zoomed.sourceX());
	}

	@Test
	void builderProducesACompletePackFromOnlyTheDeclaredAssets(@TempDir Path temporary) throws Exception {
		Path assets = temporary.resolve("assets");
		java.nio.file.Files.createDirectories(assets);
		BufferedImage map = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
		BufferedImage ui = new BufferedImage(448, 384, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < ui.getHeight(); y++) for (int x = 0; x < ui.getWidth(); x++) ui.setRGB(x, y, 0xFF556677);
		for (int y = 32; y < 288; y++) for (int x = 32; x < 416; x++) ui.setRGB(x, y, 0);
		BufferedImage player = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		ImageIO.write(map, "PNG", assets.resolve("map.png").toFile());
		ImageIO.write(ui, "PNG", assets.resolve("ui.png").toFile());
		ImageIO.write(player, "PNG", assets.resolve("player.png").toFile());
		MapDefinition definition = new MapDefinition(assets.resolve("map.png"), assets.resolve("ui.png"), assets.resolve("player.png"),
			0, 0, 254, MapDefinition.UiLayout.detect(ui), 512, 512, List.of());

		Path output = temporary.resolve("generated-resource-pack");
		ResourcePackWriter.write(output, definition);

		assertTrue(java.nio.file.Files.isRegularFile(output.resolve("dialogue-map.zip")));
		try (ZipFile zip = new ZipFile(output.resolve("dialogue-map.zip").toFile())) {
			assertTrue(zip.getEntry("assets/minecraft/shaders/core/rendertype_text.vsh") != null);
			assertTrue(zip.getEntry("assets/dialogue_map/font/ui.json") != null);
			assertTrue(zip.getEntry("manifest.json") != null);
		}
	}

	private static boolean containsMapGlyph(Component component) {
		if (component instanceof TextComponent text && text.content().codePoints().anyMatch(codepoint ->
			codepoint >= MapRenderProtocol.FIRST_MAP_CODEPOINT && codepoint < MapRenderProtocol.SPACER_CODEPOINT_START)) return true;
		return component.children().stream().anyMatch(MapDefinitionPipelineTest::containsMapGlyph);
	}
}
