package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.network.CinematicPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A question, and the answers to it, over whatever is behind them.
 *
 * <p>The flashback is the only thing in the game that asks the player anything, and this is the whole of it:
 * a line of somebody else's, a short list, and no way to leave without picking. That last part is deliberate.
 * Every other screen in the mod is something you opened; this one is somebody waiting for you to say
 * something, and closing it with escape would be the one interaction in the scene that does not mean
 * anything.
 *
 * <p>Drawn in {@link #renderBackground} on purpose. Vanilla applies its blur inside that method, and
 * {@link Screen#render} calls it before the widgets: a panel painted in {@code render} and then handed to
 * {@code super.render} is painted, blurred, and painted over by its own buttons, which is how the first
 * version of this screen came out unreadable. Everything here is drawn after the blur, once.
 */
@Environment(EnvType.CLIENT)
public class ChoiceScreen extends Screen {
	private static final int PANEL = 0xE60A0C10;
	private static final int EDGE = 0xFFB08A4A;
	private static final int WHITE = 0xFFF2F2F2;
	private static final int AMBER = 0xFFE8C87A;
	private static final int DIM = 0xFF9A9A9A;
	private static final int ROW = 0x40FFFFFF;
	private static final int ROW_HOVER = 0x66E8C87A;

	private static final int WIDTH = 340;
	private static final int LINE = 11;
	private static final int ROW_HEIGHT = 22;
	private static final int GAP = 3;
	private static final int PAD = 14;

	private final String prompt;
	private final String speaker;
	private final List<String> options;
	private final List<List<OrderedText>> wrappedOptions = new ArrayList<>();
	private List<OrderedText> wrapped = List.of();
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int rowsY;
	private boolean sent;
	/** Ticks since opening: the answers fade in a beat after the question, so the question is read first. */
	private int age;

	public ChoiceScreen(CinematicPayloads.Choice payload) {
		super(Text.translatable(payload.prompt()));
		this.prompt = payload.prompt();
		this.speaker = payload.speaker();
		this.options = payload.options();
	}

	@Override
	protected void init() {
		wrapped = textRenderer.wrapLines(Text.translatable(prompt), WIDTH - PAD * 2);
		wrappedOptions.clear();
		for (String option : options) {
			wrappedOptions.add(textRenderer.wrapLines(Text.translatable(option), WIDTH - PAD * 2 - 22));
		}
		int head = 12 + (speaker.isEmpty() ? 0 : LINE + 4) + wrapped.size() * LINE + 10;
		int rows = 0;
		for (List<OrderedText> lines : wrappedOptions) rows += rowHeight(lines) + GAP;
		panelHeight = head + rows + 18;
		panelX = (width - WIDTH) / 2;
		panelY = Math.max(10, (int) (height * 0.58f) - panelHeight / 2);
		if (panelY + panelHeight > height - 8) panelY = Math.max(4, height - 8 - panelHeight);
		rowsY = panelY + head;
	}

	private int rowHeight(List<OrderedText> lines) {
		return Math.max(ROW_HEIGHT, 8 + lines.size() * LINE);
	}

	@Override
	public void tick() {
		age++;
	}

	private void pick(int index) {
		if (sent) return;
		sent = true;
		ClientPlayNetworking.send(new CinematicPayloads.Choice.Picked(index));
		close();
	}

	private int rowAt(double mouseY) {
		int y = rowsY;
		for (int i = 0; i < wrappedOptions.size(); i++) {
			int h = rowHeight(wrappedOptions.get(i));
			if (mouseY >= y && mouseY < y + h) return i;
			y += h + GAP;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseX >= panelX + PAD && mouseX <= panelX + WIDTH - PAD) {
			int row = rowAt(mouseY);
			if (row >= 0) {
				pick(row);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// The number keys answer too, for anyone who would rather not reach for the mouse mid-scene.
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + options.size()) {
			pick(keyCode - GLFW.GLFW_KEY_1);
			return true;
		}
		if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode < GLFW.GLFW_KEY_KP_1 + options.size()) {
			pick(keyCode - GLFW.GLFW_KEY_KP_1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(panelX, panelY, panelX + WIDTH, panelY + panelHeight, PANEL);
		context.fill(panelX, panelY, panelX + WIDTH, panelY + 1, EDGE);
		context.fill(panelX, panelY + panelHeight - 1, panelX + WIDTH, panelY + panelHeight, EDGE);
		int y = panelY + 12;
		if (!speaker.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable(speaker), panelX + PAD, y, AMBER);
			y += LINE + 4;
		}
		for (OrderedText line : wrapped) {
			context.drawTextWithShadow(textRenderer, line, panelX + PAD, y, WHITE);
			y += LINE;
		}
		int hovered = rowAt(mouseY);
		boolean ready = age >= 12;
		int rowY = rowsY;
		for (int i = 0; i < wrappedOptions.size(); i++) {
			List<OrderedText> lines = wrappedOptions.get(i);
			int h = rowHeight(lines);
			boolean hover = ready && hovered == i && mouseX >= panelX + PAD && mouseX <= panelX + WIDTH - PAD;
			context.fill(panelX + PAD, rowY, panelX + WIDTH - PAD, rowY + h, hover ? ROW_HOVER : ROW);
			context.fill(panelX + PAD, rowY, panelX + PAD + 2, rowY + h, hover ? EDGE : 0x80B08A4A);
			int textColor = ready ? (hover ? WHITE : 0xFFE2E2E2) : DIM;
			context.drawTextWithShadow(textRenderer, (i + 1) + ".", panelX + PAD + 8, rowY + (h - LINE * lines.size()) / 2 + 1, hover ? AMBER : DIM);
			int ly = rowY + (h - LINE * lines.size()) / 2 + 1;
			for (OrderedText line : lines) {
				context.drawTextWithShadow(textRenderer, line, panelX + PAD + 22, ly, textColor);
				ly += LINE;
			}
			rowY += h + GAP;
		}
		Text how = Text.translatable("gui.surrogate.choice.how");
		context.drawTextWithShadow(textRenderer, how, panelX + WIDTH - PAD - textRenderer.getWidth(how), panelY + panelHeight - 12, 0xFF6A6A6A);
	}

	/** Nothing behind this needs pausing: the scene is on a server clock and the room is not going anywhere. */
	@Override
	public boolean shouldPause() {
		return false;
	}

	/** There is no closing this without answering. Somebody asked you a question. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
