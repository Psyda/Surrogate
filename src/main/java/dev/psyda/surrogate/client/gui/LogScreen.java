package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.client.SurrogateClient;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.network.CinematicPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The mission log, on the log key at any time: the current objective, the hint for it in someone's own
 * words, and a transcript of everything said so far, newest at the bottom. For the line that went by too
 * fast, and for the objective you did not catch.
 */
@Environment(EnvType.CLIENT)
public class LogScreen extends Screen {
	private static final int PANEL = 0xD0060A0E;
	private static final int EDGE = 0xFF22D3EE;
	private static final int CYAN = 0xFF22D3EE;
	private static final int YELLOW = 0xFFF5D66A;
	private static final int GREEN = 0xFF66E07A;
	private static final int AMBER = 0xFFF59E0B;
	private static final int RED = 0xFFEF4444;
	private static final int WHITE = 0xFFF2F2F2;
	private static final int GREY = 0xFFA0A0A0;
	private static final int DIM = 0xFF5C6470;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public LogScreen() {
		super(Text.translatable("screen.surrogate.log"));
	}

	@Override
	protected void init() {
		panelWidth = Math.min(380, width - 16);
		panelHeight = Math.min(300, height - 16);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(panelX + panelWidth - 68, panelY + panelHeight - 26, 60, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int x = panelX + 10;
		int y = panelY + 8;
		int textWidth = panelWidth - 20;

		context.drawTextWithShadow(textRenderer, title, x, y, CYAN);
		Text keyHint = Text.translatable("screen.surrogate.log.key", SurrogateClient.LOG.getBoundKeyLocalizedText()).formatted(Formatting.GRAY);
		context.drawTextWithShadow(textRenderer, keyHint, panelX + panelWidth - 10 - textRenderer.getWidth(keyHint), y, GREY);
		y += 14;

		// The objective and its hint.
		int state = CinematicState.objectiveState;
		boolean has = state != CinematicPayloads.OBJECTIVE_CLEAR && !CinematicState.objectiveKey.isEmpty();
		Text label = Text.translatable(!has ? "screen.surrogate.log.no_objective"
				: state == CinematicPayloads.OBJECTIVE_DONE ? "cinematic.surrogate.objective.done" : "cinematic.surrogate.objective");
		context.fill(x - 4, y - 2, x - 2, y + (has ? 24 : 10) + 2, has && state != CinematicPayloads.OBJECTIVE_DONE ? YELLOW : GREEN);
		context.drawTextWithShadow(textRenderer, label, x + 2, y, has && state != CinematicPayloads.OBJECTIVE_DONE ? YELLOW : GREEN);
		y += 11;
		if (has) {
			for (OrderedText line : textRenderer.wrapLines(Text.translatable(CinematicState.objectiveKey), textWidth - 4)) {
				context.drawTextWithShadow(textRenderer, line, x + 2, y, WHITE);
				y += 10;
			}
		}
		if (!CinematicState.hintKey.isEmpty()) {
			for (OrderedText line : textRenderer.wrapLines(Text.translatable(CinematicState.hintKey).formatted(Formatting.ITALIC), textWidth - 4)) {
				context.drawTextWithShadow(textRenderer, line, x + 2, y, GREY);
				y += 10;
			}
		}
		y += 4;
		context.fill(x, y, x + textWidth, y + 1, DIM);
		y += 5;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.surrogate.log.transcript"), x, y, CYAN);
		y += 12;

		// The transcript fills what is left, newest at the bottom.
		int bottom = panelY + panelHeight - 32;
		List<List<OrderedText>> blocks = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		int used = 0;
		Iterator<CinematicState.LogLine> it = CinematicState.log.descendingIterator();
		while (it.hasNext()) {
			CinematicState.LogLine entry = it.next();
			Text text = entry.arg().isEmpty() ? Text.translatable(entry.text()) : Text.translatable(entry.text(), entry.arg());
			Text full = entry.speaker().isEmpty() || entry.style() == CinematicPayloads.CHAPTER ? text
					: Text.empty().append(Text.translatable(entry.speaker()).formatted(Formatting.BOLD)).append(": ").append(text);
			if (entry.style() == CinematicPayloads.NARRATION) full = full.copy().formatted(Formatting.ITALIC);
			List<OrderedText> lines = textRenderer.wrapLines(full, textWidth);
			int needed = lines.size() * 10 + 2;
			if (used + needed > bottom - y) break;
			used += needed;
			blocks.add(0, lines);
			colors.add(0, colorFor(entry.style()));
		}
		if (blocks.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.surrogate.log.empty"), x, y, DIM);
		}
		for (int i = 0; i < blocks.size(); i++) {
			for (OrderedText line : blocks.get(i)) {
				context.drawTextWithShadow(textRenderer, line, x, y, colors.get(i));
				y += 10;
			}
			y += 2;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
		context.fill(panelX, panelY, panelX + 2, panelY + panelHeight, EDGE);
	}

	private static int colorFor(int style) {
		return switch (style) {
			case CinematicPayloads.RADIO -> CYAN;
			case CinematicPayloads.INTERCOM -> AMBER;
			case CinematicPayloads.SYSTEM -> GREEN;
			case CinematicPayloads.ALERT -> RED;
			case CinematicPayloads.CHAPTER -> YELLOW;
			case CinematicPayloads.NARRATION -> GREY;
			default -> WHITE;
		};
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (SurrogateClient.LOG.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
