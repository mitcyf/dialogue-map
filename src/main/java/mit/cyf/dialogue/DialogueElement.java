package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

/** A display-list item expressed in logical canvas pixels. */
public sealed interface DialogueElement permits DialogueText, DialogueVisual, DialogueButton {

	int x();

	int y();

	/** Materialises this item after the renderer has selected its native 9px text row. */
	Component render(int rowHeight);

	/**
	 * The component's declared pen advance in font pixels, or {@code -1} when the caller did
	 * not supply one. Positioned items always declare a span; flow text deliberately does not.
	 */
	default int advance() {
		return -1;
	}
}
