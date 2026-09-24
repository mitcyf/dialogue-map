package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** The structured component and portable JSON representation produced by a screen renderer. */
public record RenderedDialogue(Component component, String json) {

	public RenderedDialogue {
		Objects.requireNonNull(component, "component");
		Objects.requireNonNull(json, "json");
	}
}
