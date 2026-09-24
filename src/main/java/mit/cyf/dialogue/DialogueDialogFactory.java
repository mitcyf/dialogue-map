package mit.cyf.dialogue;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;

/** Adapts a rendered canvas to Paper's native dialog screen. */
public final class DialogueDialogFactory {

	public Dialog create(DialogueScreen screen, Component contents) {
		ActionButton closeButton = ActionButton.create(
			Component.text("Close"),
			null,
			100,
			DialogAction.staticAction(ClickEvent.runCommand("/dialoguemap close"))
		);
		DialogBase base = DialogBase.builder(screen.title())
			.canCloseWithEscape(true)
			.pause(false)
			/*
			 * A run-command text control is not a dialog form response. WAIT_FOR_RESPONSE puts the
			 * client into its pending/hidden state while the command round-trip happens. NONE keeps the
			 * current map visible until the replacement dialog packet arrives.
			 */
			.afterAction(DialogBase.DialogAfterAction.NONE)
			/*
			 * Keep the public canvas size separate from Paper's transport width. PlainMessage's
			 * FocusableTextWidget removes 16px from the supplied width, so a 448px visual
			 * canvas must travel in a 464px dialog to retain one unwrapped 448px text line.
			 */
			.body(List.of(DialogBody.plainMessage(contents, screen.nativeDialogWidth())))
			.inputs(List.of())
			.build();
		return Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice(closeButton)));
	}
}
