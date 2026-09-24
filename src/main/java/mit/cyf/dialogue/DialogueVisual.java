package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * One non-interactive bitmap/codepoint visual at an absolute logical position. The font resource
 * owns its vertical geometry (as the macro-map glyph does); the canvas owns x/y, span, ordering,
 * and transport padding.
 */
public record DialogueVisual(int x, int y, int advance, Component glyph) implements DialogueElement {

	public DialogueVisual {
		/* Map pages may begin beyond a canvas edge; the map shader owns final clipping. */
		if (y < 0) throw new IllegalArgumentException("Visual row cannot be negative.");
		if (advance < 0) throw new IllegalArgumentException("A positioned visual needs a non-negative span.");
		Objects.requireNonNull(glyph, "glyph");
	}

	@Override
	public Component render(int rowHeight) {
		return glyph;
	}
}
