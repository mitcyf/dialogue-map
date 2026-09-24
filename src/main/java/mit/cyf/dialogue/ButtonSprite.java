package mit.cyf.dialogue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Objects;

/**
 * A clickable bitmap sprite with nine generated font variants. Variant 0..8 maps to the vertical
 * offset -4..+4 from the nearest 9px native text baseline; each variant has a matching ascent.
 */
public record ButtonSprite(Key font, int firstCodepoint, int width, int height) {

	public static final int VERTICAL_VARIANTS = 9;
	private static final int MIN_VERTICAL_OFFSET = -4;

	public ButtonSprite {
		Objects.requireNonNull(font, "font");
		if (firstCodepoint < Character.MIN_CODE_POINT || firstCodepoint + VERTICAL_VARIANTS - 1 > Character.MAX_CODE_POINT) {
			throw new IllegalArgumentException("Button codepoint range is outside Unicode.");
		}
		if (width < 1 || height < 1) throw new IllegalArgumentException("Button dimensions must be positive.");
	}

	Component renderAt(int y, int rowHeight, String command) {
		if (rowHeight != VERTICAL_VARIANTS) {
			throw new IllegalArgumentException("Nine-ascent button sprites require the native 9px dialogue row height.");
		}
		int row = Math.round((float) y / rowHeight);
		int verticalOffset = y - row * rowHeight;
		int variant = verticalOffset - MIN_VERTICAL_OFFSET;
		if (variant < 0 || variant >= VERTICAL_VARIANTS) {
			throw new IllegalStateException("Nearest-row button offset must be in -4..4, got " + verticalOffset + ".");
		}
		Component glyph = Component.text(new String(Character.toChars(firstCodepoint + variant))).font(font)
			.clickEvent(ClickEvent.runCommand(command));
		return DialogueGlyphs.withExactBitmapAdvance(glyph);
	}
}
