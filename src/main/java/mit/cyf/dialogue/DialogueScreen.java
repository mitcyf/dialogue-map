package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A reusable logical canvas for a dialogue. Width is the visible canvas; the native dialog
 * transport is 16px wider to accommodate PlainMessage's fixed text-widget reservation.
 */
public final class DialogueScreen {

	public static final int MIN_WIDTH = 1;
	/** Paper permits a 1024px native dialog; 16px is reserved for PlainMessage's widget framing. */
	public static final int MAX_WIDTH = 1008;
	public static final int MAX_HEIGHT = 1024;
	/*
	 * Vanilla 1.21.11 PlainMessage centers a FocusableTextWidget with 4px padding, then calls
	 * containWithin(width). FocusableTextWidget reserves padding * 4, not padding * 2: the
	 * usable native line is native dialog width - 16 and is centered 8px from either native edge.
	 * A logical canvas therefore reserves a 16px larger transport dialog: a 448px visual UI emits
	 * a 448px native line inside a 464px dialog. This prevents client wrapping while preserving
	 * exactly the visual dimensions requested by callers.
	 */
	public static final int PLAIN_MESSAGE_TEXT_INSET = 8;

	private final Component title;
	private final int width;
	private final int height;
	private final int rowHeight;
	private final List<DialogueElement> elements;

	private DialogueScreen(Builder builder) {
		title = builder.title;
		width = builder.width;
		height = builder.height;
		rowHeight = builder.rowHeight;
		elements = List.copyOf(builder.elements);
	}

	public static Builder builder(Component title, int width, int height, int rowHeight) {
		return new Builder(title, width, height, rowHeight);
	}

	public Component title() {
		return title;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int rowHeight() {
		return rowHeight;
	}

	/**
	 * X correction from the logical canvas into PlainMessage's native text line. The client already
	 * centers that line 8px inside the 464px transport body, so it must stay zero: subtracting the
	 * margin here would pin the canvas to the body's left edge and leave all 16px of transport
	 * space visibly on the right.
	 */
	public int contentLeft() {
		return 0;
	}

	/** Maximum single-line pen advance accepted without FocusableTextWidget wrapping it. */
	public int contentWidth() {
		return width;
	}

	/** Width sent to DialogBody.plainMessage, including its 8px transport margin on both sides. */
	public int nativeDialogWidth() {
		return width + PLAIN_MESSAGE_TEXT_INSET * 2;
	}

	public List<DialogueElement> elements() {
		return elements;
	}

	/** Rounds a pixel Y coordinate to the nearest text row for this initial renderer. */
	public int rowFor(int y) {
		return Math.round((float) y / rowHeight);
	}

	public static final class Builder {

		private final Component title;
		private final int width;
		private final int height;
		private final int rowHeight;
		private final List<DialogueElement> elements = new ArrayList<>();

		private Builder(Component title, int width, int height, int rowHeight) {
			this.title = Objects.requireNonNull(title, "title");
			if (width < MIN_WIDTH || width > MAX_WIDTH) {
				throw new IllegalArgumentException("Logical dialogue width must be between " + MIN_WIDTH + " and " + MAX_WIDTH + ".");
			}
			if (height < 1 || height > MAX_HEIGHT) {
				throw new IllegalArgumentException("Dialogue height must be between 1 and " + MAX_HEIGHT + ".");
			}
			if (rowHeight < 1 || rowHeight > height) {
				throw new IllegalArgumentException("Dialogue row height must be between 1 and the canvas height.");
			}
			this.width = width;
			this.height = height;
			this.rowHeight = rowHeight;
		}

		/** Adds ordinary flowing text. It may not share a native row with positioned content. */
		public Builder line(int x, int y, Component component) {
			if (x >= width || y >= height) {
				throw new IllegalArgumentException("Dialogue element must start inside the " + width + "x" + height + " canvas.");
			}
			elements.add(new DialogueText(x, y, component));
			return this;
		}

		/**
		 * Adds one non-interactive generated glyph at an absolute pixel coordinate. The supplied
		 * component is normally a single codepoint (such as a macro map page or a frame sprite).
		 * Insertion order is paint order, so visuals may overlap.
		 */
		public Builder visual(int x, int y, int advance, Component component) {
			if (y < 0 || y >= height) {
				throw new IllegalArgumentException("Dialogue visual row must start inside the " + width + "x" + height + " canvas.");
			}
			if (advance < 0) {
				throw new IllegalArgumentException("Positioned visual advance cannot be negative.");
			}
			elements.add(new DialogueVisual(x, y, advance, component));
			return this;
		}

		/**
		 * Adds a clickable sprite at an exact logical pixel coordinate. Its generated nine-codepoint
		 * font family supplies the ascent appropriate to this y coordinate; the declared hitbox is
		 * exactly {@code sprite.width() x sprite.height()}.
		 */
		public Builder button(int x, int y, ButtonSprite sprite, String command) {
			Objects.requireNonNull(sprite, "sprite");
			if (x < 0 || y < 0 || x + sprite.width() > width || y + sprite.height() > height) {
				throw new IllegalArgumentException("Button hitbox must fit inside the logical canvas.");
			}
			elements.add(new DialogueButton(x, y, sprite, command));
			return this;
		}

		public DialogueScreen build() {
			return new DialogueScreen(this);
		}
	}
}
