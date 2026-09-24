package mit.cyf;

/** Shared, versioned constants for the server's dialogue encoding and generated text shader. */
public final class MapRenderProtocol {
	public static final String NAMESPACE = "dialogue_map";
	public static final int FIRST_MAP_CODEPOINT = 0xF0000;
	public static final int SPACER_CODEPOINT_START = 0x10F000;
	public static final int UI_CODEPOINT = 0x10F020;
	public static final int PLAYER_CODEPOINT = 0x10F021;
	public static final int FIRST_BUTTON_CODEPOINT = 0x10F100;
	public static final int BUTTON_VARIANTS = 9;
	public static final int MAP_GLYPH_ADVANCE = 255;
	public static final int MACRO_CELL_SIZE = 256;
	public static final int PLAYER_SIZE = 16;

	private MapRenderProtocol() {
	}
}
