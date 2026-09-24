package mit.cyf;

import mit.cyf.dialogue.ButtonSprite;
import mit.cyf.dialogue.DialogueGlyphs;
import mit.cyf.dialogue.DialogueScreen;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;

/** Converts a {@link MapDefinition} and a viewport into an absolute-positioned dialogue canvas. */
final class MapScreenFactory {
	private static final Key SPACER_FONT = Key.key(MapRenderProtocol.NAMESPACE, "spacers");
	private static final Key UI_FONT = Key.key(MapRenderProtocol.NAMESPACE, "ui");
	private static final Key PLAYER_FONT = Key.key(MapRenderProtocol.NAMESPACE, "player");
	private static final Key BUTTON_FONT = Key.key(MapRenderProtocol.NAMESPACE, "buttons");

	private MapScreenFactory() {
	}

	static MapViewport initialViewport(MapDefinition definition, Location location) {
		MapDefinition.ZoomLevel level = definition.zoomLevels().getFirst();
		return new MapViewport(0,
			clampX(definition, level, sourcePixel(location.getX(), definition.originX(), level.blocksPerPixel()) - definition.ui().viewportWidth() / 2),
			clampY(definition, level, sourcePixel(location.getZ(), definition.originY(), level.blocksPerPixel()) - definition.ui().viewportHeight() / 2));
	}

	static MapViewport pan(MapDefinition definition, MapViewport viewport, int directionX, int directionY) {
		MapDefinition.ZoomLevel level = level(definition, viewport);
		return new MapViewport(viewport.zoomIndex(), clampX(definition, level, viewport.sourceX() + directionX * 16),
			clampY(definition, level, viewport.sourceY() + directionY * 16));
	}

	static MapViewport zoom(MapDefinition definition, MapViewport viewport, boolean zoomIn) {
		int target = Math.clamp(viewport.zoomIndex() + (zoomIn ? -1 : 1), 0, definition.zoomLevels().size() - 1);
		if (target == viewport.zoomIndex()) return viewport;
		MapDefinition.ZoomLevel current = level(definition, viewport);
		MapDefinition.ZoomLevel next = definition.zoomLevels().get(target);
		int worldCenterX = (viewport.sourceX() + definition.ui().viewportWidth() / 2) * current.blocksPerPixel();
		int worldCenterY = (viewport.sourceY() + definition.ui().viewportHeight() / 2) * current.blocksPerPixel();
		return new MapViewport(target,
			clampX(definition, next, Math.floorDiv(worldCenterX, next.blocksPerPixel()) - definition.ui().viewportWidth() / 2),
			clampY(definition, next, Math.floorDiv(worldCenterY, next.blocksPerPixel()) - definition.ui().viewportHeight() / 2));
	}

	static DialogueScreen create(MapDefinition definition, MapViewport viewport, Location playerLocation) {
		MapDefinition.ZoomLevel level = level(definition, viewport);
		DialogueScreen.Builder screen = DialogueScreen.builder(Component.empty(), definition.ui().width(), definition.ui().height(), 9);
		addMapTiles(screen, definition, viewport, level);
		addUi(screen, definition);
		addButtons(screen, definition);
		if (playerLocation != null) addPlayer(screen, definition, viewport, level, playerLocation);
		int footerRow = nativeBodyLineCount(definition.ui().height()) - 2;
		screen.line(0, footerRow * 9, layoutHeightSpacer());
		return screen.build();
	}

	private static void addMapTiles(DialogueScreen.Builder screen, MapDefinition definition, MapViewport viewport, MapDefinition.ZoomLevel level) {
		int firstPageX = Math.floorDiv(viewport.sourceX(), definition.macroPageSize());
		int firstPageY = Math.floorDiv(viewport.sourceY(), definition.macroPageSize());
		int shiftX = Math.floorMod(viewport.sourceX(), definition.macroPageSize());
		int shiftY = Math.floorMod(viewport.sourceY(), definition.macroPageSize());
		int pagesWide = definition.pagesWide(level);
		int pagesHigh = ceilDiv(level.height(), definition.macroPageSize());
		for (int slotY = 0; slotY < 3; slotY++) for (int slotX = 0; slotX < 3; slotX++) {
			int pageX = firstPageX + slotX;
			int pageY = firstPageY + slotY;
			if (pageX >= pagesWide || pageY >= pagesHigh) continue;
			int codepoint = definition.codepointStart(level.index()) + pageY * pagesWide + pageX;
			Component glyph = Component.text(new String(Character.toChars(codepoint)))
				.font(Key.key(MapRenderProtocol.NAMESPACE, "map-r" + (level.index() + 1)))
				.color(TextColor.color(shiftX, shiftY, 48 + slotY * 3 + slotX))
				.append(DialogueGlyphs.horizontalOffset(-MapRenderProtocol.MAP_GLYPH_ADVANCE));
			screen.visual(definition.ui().viewportX(), definition.ui().viewportY(), 0, glyph);
		}
	}

	private static void addUi(DialogueScreen.Builder screen, MapDefinition definition) {
		Component glyph = Component.text(new String(Character.toChars(MapRenderProtocol.UI_CODEPOINT))).font(UI_FONT);
		screen.visual(0, 0, definition.ui().width(), DialogueGlyphs.withExactBitmapAdvance(glyph));
	}

	private static void addButtons(DialogueScreen.Builder screen, MapDefinition definition) {
		List<MapDefinition.Button> buttons = definition.buttons();
		for (int index = 0; index < buttons.size(); index++) {
			MapDefinition.Button button = buttons.get(index);
			ButtonSprite sprite = new ButtonSprite(BUTTON_FONT, MapRenderProtocol.FIRST_BUTTON_CODEPOINT + index * MapRenderProtocol.BUTTON_VARIANTS,
				button.width(), button.height());
			screen.button(button.x(), button.y(), sprite, button.action());
		}
	}

	private static void addPlayer(DialogueScreen.Builder screen, MapDefinition definition, MapViewport viewport, MapDefinition.ZoomLevel level,
		Location player) {
		int x = sourcePixel(player.getX(), definition.originX(), level.blocksPerPixel()) - viewport.sourceX();
		int y = sourcePixel(player.getZ(), definition.originY(), level.blocksPerPixel()) - viewport.sourceY();
		if (x < 0 || y < 0 || x >= definition.ui().viewportWidth() || y >= definition.ui().viewportHeight()) return;
		int heading = Math.floorMod(Math.round((player.getYaw() + 180.0f) * 16.0f / 360.0f), 16);
		int blue = 128 + (x / 256) * 16 + heading;
		Component glyph = Component.text(new String(Character.toChars(MapRenderProtocol.PLAYER_CODEPOINT))).font(PLAYER_FONT)
			.color(TextColor.color(x & 0xFF, y, blue));
		screen.visual(definition.ui().viewportX(), definition.ui().viewportY(), MapRenderProtocol.PLAYER_SIZE,
			DialogueGlyphs.withExactBitmapAdvance(glyph));
	}

	private static MapDefinition.ZoomLevel level(MapDefinition definition, MapViewport viewport) {
		if (viewport.zoomIndex() < 0 || viewport.zoomIndex() >= definition.zoomLevels().size()) throw new IllegalArgumentException("Unknown zoom level");
		return definition.zoomLevels().get(viewport.zoomIndex());
	}

	private static int sourcePixel(double world, int origin, int blocksPerPixel) {
		return Math.floorDiv((int) Math.floor(world) - origin, blocksPerPixel);
	}

	private static int clampX(MapDefinition definition, MapDefinition.ZoomLevel level, int sourceX) {
		return Math.clamp(sourceX, 0, level.width() - definition.ui().viewportWidth());
	}

	private static int clampY(MapDefinition definition, MapDefinition.ZoomLevel level, int sourceY) {
		return Math.clamp(sourceY, 0, level.height() - definition.ui().viewportHeight());
	}

	private static int nativeBodyLineCount(int height) {
		return ceilDiv(Math.max(1, height - 8), 9);
	}

	private static Component layoutHeightSpacer() {
		Component spacer = Component.text(new String(Character.toChars(MapRenderProtocol.SPACER_CODEPOINT_START))).font(SPACER_FONT);
		return spacer.append(Component.newline()).append(spacer);
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}

	record MapViewport(int zoomIndex, int sourceX, int sourceY) {
	}
}
