package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** A positioned, interactive bitmap button with an exact logical hitbox. */
public record DialogueButton(int x, int y, ButtonSprite sprite, String command) implements DialogueElement {

	public DialogueButton {
		if (x < 0 || y < 0) throw new IllegalArgumentException("Button coordinates cannot be negative.");
		Objects.requireNonNull(sprite, "sprite");
		if (command == null || command.isBlank()) throw new IllegalArgumentException("A button command is required.");
	}

	@Override
	public int advance() {
		return sprite.width();
	}

	@Override
	public Component render(int rowHeight) {
		return sprite.renderAt(y, rowHeight, command);
	}
}
