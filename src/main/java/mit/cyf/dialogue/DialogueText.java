package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** A normal-font, non-interactive flow-text line. It is intentionally not a positioned sprite. */
public record DialogueText(int x, int y, Component text) implements DialogueElement {

	public DialogueText {
		if (x < 0 || y < 0) throw new IllegalArgumentException("Dialogue coordinates cannot be negative.");
		Objects.requireNonNull(text, "text");
	}

	@Override
	public int advance() {
		return -1;
	}

	@Override
	public Component render(int rowHeight) {
		return text;
	}
}
