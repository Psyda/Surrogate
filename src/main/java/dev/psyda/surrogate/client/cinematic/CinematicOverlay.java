package dev.psyda.surrogate.client.cinematic;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.psyda.surrogate.client.SurrogateClient;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.rescue.CallPanel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws the cinematic layer over the HUD: letterbox bars, fades, subtitles with a typewriter reveal,
 * title cards and chapter headings, the objective banner and the hold-to-skip hint.
 */
@Environment(EnvType.CLIENT)
public final class CinematicOverlay {
	private static final int GOLD = 0xFFE0B040;
	private static final int AQUA = 0xFF5FD7E6;
	private static final int AMBER = 0xFFF0A030;
	private static final int WHITE = 0xFFF2F2F2;
	private static final int GREY = 0xFFB0B0B0;
	private static final int RED = 0xFFE84040;
	private static final int GREEN = 0xFF66E07A;
	private static final int YELLOW = 0xFFF5D66A;
	private static final int SYSTEM_CYAN = 0xFF7FE8F0;
	private static final float REVEAL_PER_TICK = 2.2f;

	private CinematicOverlay() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;
		context.draw(() -> renderLayer(context, tickCounter, client));
		// The faces go in a second batch on purpose. Inside DrawContext.draw a fill is buffered and flushed
		// at the end of the lambda, but drawTexture builds its own buffer and paints immediately, so a face
		// drawn in the first batch lands underneath the panel it is supposed to be sitting in.
		float delta = tickCounter.getTickDelta(false);
		if (CinematicState.call(delta) > 0.01f) context.draw(() -> renderCallFaces(context, client, delta));
	}

	private static void renderLayer(DrawContext context, RenderTickCounter tickCounter, MinecraftClient client) {
		float delta = tickCounter.getTickDelta(false);
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		TextRenderer font = client.textRenderer;

		float bars = CinematicState.letterbox(delta);
		int barHeight = Math.round(height * 0.13f * (bars * bars * (3f - 2f * bars)));
		if (barHeight > 0) {
			context.fill(0, 0, width, barHeight, 0xFF000000);
			context.fill(0, height - barHeight, width, height, 0xFF000000);
		}

		// The call goes under the fade. The conference ends on a fade to black and the grid is the thing it
		// is fading out, so a grid painted after the black is a grid the black never covers.
		if (CinematicState.call(delta) > 0.01f) renderCall(context, font, width, height, barHeight, delta);

		float fade = CinematicState.fade(delta);
		if (fade > 0.002f) {
			context.fill(0, 0, width, height, (Math.round(fade * 255f) << 24));
		}

		if (CinematicState.hasLine()) renderLine(context, font, width, height, barHeight, delta);
		renderObjective(context, font, width, delta);
		if (CinematicState.isInputLocked()) renderSkip(context, font, width, height, barHeight);
		else if (CinematicState.canAdvance()) renderAdvance(context, font, width, height);
	}

	// ------------------------------------------------------------------ subtitles

	private static Text lineText() {
		String arg = CinematicState.lineArg;
		return arg.isEmpty() ? Text.translatable(CinematicState.textKey) : Text.translatable(CinematicState.textKey, arg);
	}

	private static Text speakerText() {
		String arg = CinematicState.lineArg;
		return arg.isEmpty() ? Text.translatable(CinematicState.speakerKey) : Text.translatable(CinematicState.speakerKey, arg);
	}

	private static void renderLine(DrawContext context, TextRenderer font, int width, int height, int barHeight, float delta) {
		float t = CinematicState.lineElapsed + delta;
		int ticks = CinematicState.lineTicks;
		float alpha = Math.min(1f, t / 6f) * MathHelper.clamp((ticks + 10 - t) / 8f, 0f, 1f);
		if (alpha <= 0.02f) return;
		int style = CinematicState.lineStyle;
		switch (style) {
			case CinematicPayloads.TITLE -> renderTitle(context, font, width, height, t, ticks, 3f);
			case CinematicPayloads.CHAPTER -> renderTitle(context, font, width, height, t, ticks, 2f);
			case CinematicPayloads.ALERT -> renderAlert(context, font, width, height, t, alpha);
			case CinematicPayloads.NARRATION -> renderNarration(context, font, width, height, t, alpha);
			case CinematicPayloads.SYSTEM -> renderSystem(context, font, width, height, barHeight, t, alpha);
			default -> renderSpeech(context, font, width, height, barHeight, t, alpha, style);
		}
	}

	private static void renderSpeech(DrawContext context, TextRenderer font, int width, int height, int barHeight, float t, float alpha, int style) {
		String full = lineText().getString();
		int visible = Math.min(full.length(), (int) (t * REVEAL_PER_TICK));
		String shown = full.substring(0, visible);
		int maxWidth = (int) (width * 0.62f);
		List<OrderedText> lines = font.wrapLines(StringVisitable.plain(shown), maxWidth);
		int lineCount = Math.max(1, font.wrapLines(StringVisitable.plain(full), maxWidth).size());
		int blockHeight = 12 + lineCount * (font.fontHeight + 2);

		int anchorY;
		if (barHeight > 0) {
			anchorY = height - barHeight / 2 - blockHeight / 2;
		} else {
			anchorY = height - 64 - blockHeight;
			int panelWidth = Math.min(width - 20, Math.max(font.getWidth(full), 120) + 24);
			context.fill(width / 2 - panelWidth / 2, anchorY - 6, width / 2 + panelWidth / 2, anchorY + blockHeight, withAlpha(0x000000, alpha * 0.55f));
		}
		Text speaker = speakerText();
		Text name;
		int nameColor;
		switch (style) {
			case CinematicPayloads.RADIO -> {
				name = Text.empty().append(Text.translatable("cinematic.surrogate.radio")).append(" > ").append(speaker);
				nameColor = AQUA;
			}
			case CinematicPayloads.INTERCOM -> {
				name = Text.empty().append(Text.translatable("cinematic.surrogate.intercom")).append(" > ").append(speaker);
				nameColor = AMBER;
			}
			default -> {
				name = speaker;
				nameColor = GOLD;
			}
		}
		context.drawCenteredTextWithShadow(font, name, width / 2, anchorY, withAlpha(nameColor, alpha));
		int y = anchorY + font.fontHeight + 3;
		for (OrderedText line : lines) {
			context.drawCenteredTextWithShadow(font, line, width / 2, y, withAlpha(WHITE, alpha));
			y += font.fontHeight + 2;
		}
	}

	/** A machine speaking: bracketed, cyan, no typewriter, a thin rule either side of the name. */
	private static void renderSystem(DrawContext context, TextRenderer font, int width, int height, int barHeight, float t, float alpha) {
		String full = lineText().getString();
		int visible = Math.min(full.length(), (int) (t * REVEAL_PER_TICK * 1.6f));
		String shown = "[ " + full.substring(0, visible) + (visible < full.length() ? "_" : " ]");
		int maxWidth = (int) (width * 0.62f);
		List<OrderedText> lines = font.wrapLines(StringVisitable.plain(shown), maxWidth);
		int blockHeight = 12 + Math.max(1, lines.size()) * (font.fontHeight + 2);
		int anchorY = barHeight > 0 ? height - barHeight / 2 - blockHeight / 2 : height - 64 - blockHeight;
		if (barHeight <= 0) {
			int panelWidth = Math.min(width - 20, Math.max(font.getWidth(full), 120) + 24);
			context.fill(width / 2 - panelWidth / 2, anchorY - 6, width / 2 + panelWidth / 2, anchorY + blockHeight, withAlpha(0x001014, alpha * 0.7f));
		}
		Text name = speakerText();
		int nameWidth = font.getWidth(name);
		context.fill(width / 2 - nameWidth / 2 - 30, anchorY + 4, width / 2 - nameWidth / 2 - 6, anchorY + 5, withAlpha(0x2A8C99, alpha));
		context.fill(width / 2 + nameWidth / 2 + 6, anchorY + 4, width / 2 + nameWidth / 2 + 30, anchorY + 5, withAlpha(0x2A8C99, alpha));
		context.drawCenteredTextWithShadow(font, name, width / 2, anchorY, withAlpha(0x2A8C99, alpha));
		int y = anchorY + font.fontHeight + 3;
		for (OrderedText line : lines) {
			context.drawCenteredTextWithShadow(font, line, width / 2, y, withAlpha(SYSTEM_CYAN, alpha));
			y += font.fontHeight + 2;
		}
	}

	private static void renderNarration(DrawContext context, TextRenderer font, int width, int height, float t, float alpha) {
		String full = lineText().getString();
		int visible = Math.min(full.length(), (int) (t * REVEAL_PER_TICK));
		List<OrderedText> lines = font.wrapLines(StringVisitable.plain(full.substring(0, visible)), (int) (width * 0.6f));
		int y = (int) (height * 0.7f);
		for (OrderedText line : lines) {
			context.drawCenteredTextWithShadow(font, line, width / 2, y, withAlpha(GREY, alpha));
			y += font.fontHeight + 2;
		}
	}

	private static void renderAlert(DrawContext context, TextRenderer font, int width, int height, float t, float alpha) {
		boolean on = ((int) (t / 5)) % 3 != 2;
		if (!on) return;
		Text title = lineText().copy().formatted(Formatting.BOLD);
		Text sub = speakerText();
		context.getMatrices().push();
		context.getMatrices().translate(width / 2f, height * 0.36f, 0f);
		context.getMatrices().scale(2f, 2f, 1f);
		context.drawCenteredTextWithShadow(font, title, 0, 0, withAlpha(RED, alpha));
		context.getMatrices().pop();
		context.drawCenteredTextWithShadow(font, sub, width / 2, (int) (height * 0.36f) + 24, withAlpha(WHITE, alpha));
	}

	private static void renderTitle(DrawContext context, TextRenderer font, int width, int height, float t, int ticks, float scale) {
		float alpha = Math.min(1f, t / 18f) * MathHelper.clamp((ticks - t) / 18f, 0f, 1f);
		if (alpha <= 0.02f) return;
		Text title = lineText();
		Text sub = speakerText();
		int cy = (int) (height * 0.42f);
		context.getMatrices().push();
		context.getMatrices().translate(width / 2f, cy, 0f);
		context.getMatrices().scale(scale, scale, 1f);
		context.drawCenteredTextWithShadow(font, title, 0, 0, withAlpha(WHITE, alpha));
		context.getMatrices().pop();
		int ruleY = cy + (int) (font.fontHeight * scale) + 3;
		int lineWidth = (int) (font.getWidth(title) * scale * Math.min(1f, t / 30f));
		context.fill(width / 2 - lineWidth / 2, ruleY, width / 2 + lineWidth / 2, ruleY + 1, withAlpha(0x22D3EE, alpha));
		context.drawCenteredTextWithShadow(font, sub, width / 2, ruleY + 8, withAlpha(GREY, alpha));
	}

	// ------------------------------------------------------------------ the conference call

	private static final int CALL_GROUND = 0x061410;
	private static final int CALL_FRAME = 0x1F7A3C;
	private static final int CALL_LIVE = 0x4CE07A;

	/**
	 * The grid of the call, laid out once and read twice: the frames and labels go in the batched pass and
	 * the faces in the one after it.
	 *
	 * @return the panel rectangle for {@code index} as {x, y, w, h}, or null when the grid does not fit
	 */
	@Nullable
	private static int[] callSlot(int index, int width, int height, int barHeight) {
		int columns = CallPanel.COLUMNS;
		int rows = CallPanel.ROWS;
		int gap = 4;
		int panelWidth = Math.min(104, (width - 40) / columns - gap);
		int panelHeight = panelWidth * 3 / 4;
		if (panelWidth < 40) return null;
		int gridWidth = columns * panelWidth + (columns - 1) * gap;
		int gridHeight = rows * panelHeight + (rows - 1) * gap;
		// Inside the bars, and high enough that the bottom row is clear of the subtitle in the lower one.
		int top = barHeight + 8;
		if (top + gridHeight > height - barHeight - 8) top = Math.max(barHeight + 2, (height - gridHeight) / 2 - 12);
		int left = (width - gridWidth) / 2;
		int column = index % columns;
		int row = index / columns;
		return new int[]{left + column * (panelWidth + gap), top + row * (panelHeight + gap), panelWidth, panelHeight};
	}

	private static void renderCall(DrawContext context, TextRenderer font, int width, int height, int barHeight, float delta) {
		float fade = CinematicState.call(delta);
		int age = CinematicState.callAge;
		CallPanel[] all = CallPanel.all();
		for (int i = 0; i < all.length; i++) {
			int[] slot = callSlot(i, width, height, barHeight);
			if (slot == null) return;
			int x = slot[0];
			int y = slot[1];
			int w = slot[2];
			int h = slot[3];
			boolean live = (CinematicState.callShownLive() & (1 << i)) != 0;
			boolean snow = (CinematicState.callShownSnow() & (1 << i)) != 0;
			boolean speaking = CinematicState.callSpeaking == i;

			context.fill(x, y, x + w, y + h, withAlpha(CALL_GROUND, fade * 0.92f));
			int frame = speaking ? CALL_LIVE : live ? CALL_FRAME : 0x2A2A2A;
			context.fill(x, y, x + w, y + 1, withAlpha(frame, fade));
			context.fill(x, y + h - 1, x + w, y + h, withAlpha(frame, fade));
			context.fill(x, y, x + 1, y + h, withAlpha(frame, fade));
			context.fill(x + w - 1, y, x + w, y + h, withAlpha(frame, fade));

			if (snow) {
				// Torn bands rather than per-pixel noise: cheap, and it reads as a picture coming apart
				// rather than as a screen with dust on it.
				for (int band = 0; band < h - 12; band += 2) {
					int seed = (i * 9176 + band * 31 + age * 7) * 1103515245 + 12345;
					int shade = 0x303030 + ((seed >> 16) & 0x3F) * 0x010101;
					int inset = ((seed >> 8) & 0x0F);
					context.fill(x + 1 + inset, y + 2 + band, x + w - 1 - inset, y + 3 + band, withAlpha(shade, fade * 0.75f));
				}
			}
			String label = snow ? I18n.translate("cinematic.surrogate.call.lost")
					: live ? all[i].displayName().getString()
					: I18n.translate("cinematic.surrogate.call.no_carrier");
			int color = snow ? GREY : live ? (speaking ? CALL_LIVE : WHITE) : 0x5A5A5A;
			// The level meter lives in the same corner, so a speaking panel gives the name less room: without
			// it "Dr. Imani Reyes" runs straight under the bars.
			int room = w - (speaking ? 26 : 8);
			context.drawTextWithShadow(font, font.trimToWidth(label, room), x + 4, y + h - 10, withAlpha(color, fade));
			// A level meter under whoever is talking, so a panel with no lips still looks like a voice.
			if (speaking) {
				int bars = 5;
				for (int b = 0; b < bars; b++) {
					int tall = 2 + (int) (3f * Math.abs(Math.sin((age + b * 4) * 0.35f)));
					context.fill(x + w - 6 - b * 3, y + h - 4 - tall, x + w - 4 - b * 3, y + h - 4, withAlpha(CALL_LIVE, fade));
				}
			}
		}
	}

	/** The faces, in their own batch so they land on top of the panels rather than under them. */
	private static void renderCallFaces(DrawContext context, MinecraftClient client, float delta) {
		float fade = CinematicState.call(delta);
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float bars = CinematicState.letterbox(delta);
		int barHeight = Math.round(height * 0.13f * (bars * bars * (3f - 2f * bars)));
		CallPanel[] all = CallPanel.all();
		for (int i = 0; i < all.length; i++) {
			if ((CinematicState.callShownLive() & (1 << i)) == 0) continue;
			if ((CinematicState.callShownSnow() & (1 << i)) != 0) continue;
			int[] slot = callSlot(i, width, height, barHeight);
			if (slot == null) return;
			int size = Math.max(8, (slot[3] - 14) / 8 * 8);
			int fx = slot[0] + (slot[2] - size) / 2;
			int fy = slot[1] + 3;
			// Under the fade, like everything else on the grid: drawTexture paints straight past the buffered
			// black, so the face has to be taken down by hand as the scene goes out.
			RenderSystem.setShaderColor(1f, 1f, 1f, fade * (1f - CinematicState.fade(delta)));
			// The head off their own skin: the 8x8 face at (8, 8) of a 64x64 sheet, blown up whole.
			context.drawTexture(all[i].skin(), fx, fy, size, size, 8f, 8f, 8, 8, 64, 64);
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
	}

	// ------------------------------------------------------------------ objective and skip

	/** How far down the screen the objective banner reaches, for readouts that share the top edge. */
	public static int objectiveHeight = 40;

	private static void renderObjective(DrawContext context, TextRenderer font, int width, float delta) {
		int state = CinematicState.objectiveState;
		if (state == CinematicPayloads.OBJECTIVE_CLEAR) return;
		float t = CinematicState.objectiveElapsed + delta;
		float alpha = Math.min(1f, t / 8f);
		if (state == CinematicPayloads.OBJECTIVE_DONE) alpha *= MathHelper.clamp((70 - t) / 14f, 0f, 1f);
		if (alpha <= 0.02f) return;
		boolean done = state == CinematicPayloads.OBJECTIVE_DONE;
		Text label = Text.translatable(done ? "cinematic.surrogate.objective.done" : "cinematic.surrogate.objective");
		Text text = Text.translatable(CinematicState.objectiveKey);
		Text keyHint = Text.translatable("cinematic.surrogate.objective.log", SurrogateClient.LOG.getBoundKeyLocalizedText());
		// Long objectives wrap rather than run off a narrow window.
		List<OrderedText> lines = font.wrapLines(text, Math.min(320, width - 60));
		int textWidth = font.getWidth(label) + 14 + font.getWidth(keyHint);
		for (OrderedText line : lines) textWidth = Math.max(textWidth, font.getWidth(line));
		int panelWidth = textWidth + 26;
		int panelHeight = 20 + lines.size() * 10;
		int x = width / 2 - panelWidth / 2;
		int y = 8;
		objectiveHeight = y + panelHeight + 4;
		float slide = (1f - alpha) * -10f;
		context.getMatrices().push();
		context.getMatrices().translate(0f, slide, 0f);
		context.fill(x, y, x + panelWidth, y + panelHeight, withAlpha(0x000000, alpha * 0.6f));
		context.fill(x, y, x + 2, y + panelHeight, withAlpha(done ? 0x66E07A : 0xF5D66A, alpha));
		context.drawTextWithShadow(font, label, x + 10, y + 5, withAlpha(done ? GREEN : YELLOW, alpha));
		context.drawTextWithShadow(font, keyHint, x + panelWidth - 10 - font.getWidth(keyHint), y + 5, withAlpha(0x8A8A8A, alpha));
		int lineY = y + 17;
		for (OrderedText line : lines) {
			context.drawTextWithShadow(font, line, x + 10, lineY, withAlpha(WHITE, alpha));
			lineY += 10;
		}
		context.getMatrices().pop();
	}

	private static void renderSkip(DrawContext context, TextRenderer font, int width, int height, int barHeight) {
		MinecraftClient client = MinecraftClient.getInstance();
		Text hint = Text.translatable("cinematic.surrogate.skip", client.options.jumpKey.getBoundKeyLocalizedText());
		int textWidth = font.getWidth(hint);
		int x = width - textWidth - 10;
		// In the top bar when the bars are down (the subtitle owns the bottom one), otherwise low on the right.
		int y = barHeight > 14 ? Math.max(4, barHeight / 2 - 8) : height - 22;
		context.drawTextWithShadow(font, hint, x, y, 0xFF8A8A8A);
		if (CinematicState.skipHeld > 0) {
			float progress = Math.min(1f, CinematicState.skipHeld / (float) CinematicState.SKIP_HOLD_TICKS);
			context.fill(x, y + 10, x + textWidth, y + 12, 0xFF333333);
			context.fill(x, y + 10, x + (int) (textWidth * progress), y + 12, 0xFFDDDDDD);
		}
	}

	/**
	 * The quieter half of the skip prompt: what to press to read on, shown only while a line is up and the
	 * player has their hands. Dimmer than the hold-to-skip bar and with no progress on it, because it is one
	 * press and it only ever costs the rest of a subtitle.
	 */
	private static void renderAdvance(DrawContext context, TextRenderer font, int width, int height) {
		MinecraftClient client = MinecraftClient.getInstance();
		Text hint = Text.translatable("cinematic.surrogate.advance", client.options.sneakKey.getBoundKeyLocalizedText());
		context.drawTextWithShadow(font, hint, width - font.getWidth(hint) - 10, height - 22, 0xFF6A6A6A);
	}

	private static int withAlpha(int rgb, float alpha) {
		int a = MathHelper.clamp(Math.round(alpha * 255f), 4, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
