package mit.cyf.dialogue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Shared exact-advance helpers for generated bitmap fonts. */
public final class DialogueGlyphs {

	private static final Key SPACER_FONT = Key.key("dialogue_map", "spacers");
	private static final int SPACER_CODEPOINT_START = 0x10F000;
	private static final int NEGATIVE_SPACER_OFFSET = 10;

	private DialogueGlyphs() {
	}

	/** Bitmap providers add one trailing advance; cancel it so the declared sprite span is exact. */
	public static Component withExactBitmapAdvance(Component glyph) {
		return glyph.append(horizontalOffset(-1));
	}

	public static Component horizontalOffset(int pixels) {
		Component offset = Component.empty();
		int remaining = Math.abs(pixels);
		int codepointBase = pixels < 0 ? SPACER_CODEPOINT_START + NEGATIVE_SPACER_OFFSET : SPACER_CODEPOINT_START;
		while (remaining > 0) {
			int power = Math.min(9, 31 - Integer.numberOfLeadingZeros(remaining));
			offset = offset.append(Component.text(new String(Character.toChars(codepointBase + power))).font(SPACER_FONT));
			remaining -= 1 << power;
		}
		return offset;
	}
}
