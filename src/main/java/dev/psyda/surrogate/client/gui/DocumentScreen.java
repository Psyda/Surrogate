package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.network.DocumentPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * A thing to read, held up in front of the room: a bill, a calendar, a contract on a screen.
 *
 * <p>Two looks. Paper is cream with a dark typeface and a red rule, the way a final notice is; a screen is
 * near-black with pale text and a thin frame. Both wrap their body, scroll with the wheel if there is more
 * than fits, and close on escape, because unlike a question a letter does not need anything from you.
 */
@Environment(EnvType.CLIENT)
public class DocumentScreen extends Screen {
	private static final int WIDTH = 272;
	private static final int LINE = 10;
	private static final int PAD = 14;

	private final DocumentPayload document;
	private final List<OrderedText> lines = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int scroll;
	private int visibleLines;

	public DocumentScreen(DocumentPayload document) {
		super(Text.translatable(document.title()));
		this.document = document;
	}

	@Override
	protected void init() {
		lines.clear();
		String body = I18n.translate(document.body());
		for (String paragraph : body.split("\n", -1)) {
			if (paragraph.isEmpty()) {
				lines.add(OrderedText.EMPTY);
				continue;
			}
			lines.addAll(textRenderer.wrapLines(StringVisitable.plain(paragraph), WIDTH - PAD * 2));
		}
		int room = height - 70;
		visibleLines = Math.max(4, Math.min(lines.size(), (room - 40) / LINE));
		panelHeight = 40 + visibleLines * LINE + 30;
		panelX = (width - WIDTH) / 2;
		panelY = (height - panelHeight) / 2;
		scroll = 0;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.surrogate.document.close"), button -> close())
				.dimensions(panelX + WIDTH / 2 - 50, panelY + panelHeight - 24, 100, 18).build());
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		boolean paper = document.style() == DocumentPayload.PAPER;
		int ground = paper ? 0xFFEFE6CC : 0xFF0B1116;
		int edge = paper ? 0xFFB9AA84 : 0xFF3C6A74;
		int ink = paper ? 0xFF2A2419 : 0xFFD8E8EA;
		int rule = paper ? 0xFFB0332A : 0xFF3EB7C8;
		int x0 = panelX;
		int y0 = panelY;
		// A shadow under the page, so it reads as a sheet held up rather than a panel painted on.
		context.fill(x0 + 4, y0 + 4, x0 + WIDTH + 4, y0 + panelHeight + 4, 0x66000000);
		context.fill(x0, y0, x0 + WIDTH, y0 + panelHeight, ground);
		context.fill(x0, y0, x0 + WIDTH, y0 + 1, edge);
		context.fill(x0, y0 + panelHeight - 1, x0 + WIDTH, y0 + panelHeight, edge);
		context.fill(x0, y0, x0 + 1, y0 + panelHeight, edge);
		context.fill(x0 + WIDTH - 1, y0, x0 + WIDTH, y0 + panelHeight, edge);
		Text title = Text.translatable(document.title());
		if (paper) {
			context.drawText(textRenderer, title, x0 + PAD, y0 + 12, ink, false);
			context.fill(x0 + PAD, y0 + 24, x0 + WIDTH - PAD, y0 + 25, rule);
		} else {
			context.drawTextWithShadow(textRenderer, title, x0 + PAD, y0 + 12, rule);
			context.fill(x0 + PAD, y0 + 24, x0 + WIDTH - PAD, y0 + 25, 0xFF1E4A52);
		}
		int y = y0 + 32;
		int end = Math.min(lines.size(), scroll + visibleLines);
		for (int i = scroll; i < end; i++) {
			if (paper) context.drawText(textRenderer, lines.get(i), x0 + PAD, y, ink, false);
			else context.drawTextWithShadow(textRenderer, lines.get(i), x0 + PAD, y, ink);
			y += LINE;
		}
		if (lines.size() > visibleLines) {
			// A scroll mark rather than a bar: the page is longer than the window and that is all it says.
			String more = (scroll + visibleLines < lines.size() ? "v" : "") + (scroll > 0 ? " ^" : "");
			context.drawText(textRenderer, more.trim(), x0 + WIDTH - PAD - textRenderer.getWidth(more.trim()), y0 + panelHeight - 20, paper ? 0xFF8A7B5A : 0xFF3EB7C8, false);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount < 0 && scroll + visibleLines < lines.size()) scroll++;
		else if (verticalAmount > 0 && scroll > 0) scroll--;
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
