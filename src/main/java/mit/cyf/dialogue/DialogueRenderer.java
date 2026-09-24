package mit.cyf.dialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Compiles the current non-overlapping, row-based canvas model into an Adventure component. */
public final class DialogueRenderer {

	public RenderedDialogue render(DialogueScreen screen) {
		NavigableMap<Integer, List<DialogueElement>> rows = groupByRoundedRow(screen);
		Component output = Component.empty();
		int lastRow = rows.isEmpty() ? 0 : rows.lastKey();
		for (int row = 0; row <= lastRow; row++) {
			List<DialogueElement> elements = rows.get(row);
			if (elements != null) {
				output = output.append(renderRow(screen, elements));
			}
			if (row < lastRow) output = output.append(Component.newline());
		}
		return new RenderedDialogue(output, GsonComponentSerializer.gson().serialize(output));
	}

	/**
	 * Renders a display-list row. Unlike the initial renderer, the pen is allowed to move in either
	 * direction between items. This keeps Builder insertion order as painter's order and allows
	 * absolute-positioned visuals/buttons to overlap without caller-side spacer arithmetic.
	 */
	private Component renderRow(DialogueScreen screen, List<DialogueElement> elements) {
		Component row = Component.empty();
		int pen = 0;
		boolean containsFlowText = elements.stream().anyMatch(element -> element.advance() < 0);
		if (containsFlowText && elements.size() != 1) {
			throw new IllegalArgumentException("Normal flow text cannot share a native row with positioned canvas items.");
		}
		for (DialogueElement element : elements) {
			if (element.advance() < 0 && elements.size() != 1) {
				throw new IllegalArgumentException("Rows with multiple elements require positioned spans.");
			}
			if (element.advance() < 0) return row.append(element.render(screen.rowHeight()));
			int nativeX = element.x() - screen.contentLeft();
			row = row.append(DialogueGlyphs.horizontalOffset(nativeX - pen)).append(element.render(screen.rowHeight()));
			pen = nativeX + element.advance();
		}
		row = row.append(DialogueGlyphs.horizontalOffset(screen.contentWidth() - pen));
		return row;
	}

	private NavigableMap<Integer, List<DialogueElement>> groupByRoundedRow(DialogueScreen screen) {
		NavigableMap<Integer, List<DialogueElement>> rows = new TreeMap<>();
		for (DialogueElement element : screen.elements()) {
			rows.computeIfAbsent(screen.rowFor(element.y()), ignored -> new ArrayList<>()).add(element);
		}
		// ArrayList insertion order is deliberate painter's order for overlapping items.
		return rows;
	}
}
