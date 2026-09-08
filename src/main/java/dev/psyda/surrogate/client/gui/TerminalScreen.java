package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.block.TerminalBlockEntity;
import dev.psyda.surrogate.client.ClientMissionState;
import dev.psyda.surrogate.client.ClientPilotState;
import dev.psyda.surrogate.network.MissionPayload;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.client.transit.TransitClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The company's terminal OS: a green screen with a list of files on the left and the open one on the right.
 * Pages come from the language file under {@code terminal.surrogate.<unit>.<n>.title} and {@code .body}, so
 * every unit can carry its own logs, and the first page of every unit is a live status readout.
 */
@Environment(EnvType.CLIENT)
public class TerminalScreen extends Screen {
	private static final int MAX_PAGES = 16;
	private static final int BLACK = 0xF4030704;
	private static final int GREEN = 0xFF4CE07A;
	private static final int GREEN_DIM = 0xFF1F7A3C;
	private static final int GREEN_DARK = 0xFF0E3A1E;
	private static final int AMBER = 0xFFF5B342;
	private static final int RED = 0xFFF06060;
	private static final int SCANLINE = 0x14000000;

	private final String unit;
	private final List<String> titles = new ArrayList<>();
	private final List<String> bodies = new ArrayList<>();
	private int selected;
	private long openedAt;
	private int x;
	private int y;
	private int w;
	private int h;
	private int listWidth;

	public TerminalScreen(String unit) {
		this(unit, -1);
	}

	public TerminalScreen(String unit, int survey) {
		super(Text.translatable("screen.surrogate.terminal"));
		this.unit = unit;
		titles.add(I18n.translate("terminal.surrogate.status"));
		bodies.add(null);
		for (int i = 1; i <= MAX_PAGES; i++) {
			String titleKey = "terminal.surrogate." + unit + "." + i + ".title";
			if (!I18n.hasTranslation(titleKey)) break;
			titles.add(I18n.translate(titleKey));
			bodies.add(I18n.translate("terminal.surrogate." + unit + "." + i + ".body"));
		}
		// Okafor's survey, last, and only on a hub that has had the disk put in it. A mask of -1 means no
		// disk; a mask of 0 means the software is running and nobody has filed anything yet, which is worth
		// a page of its own because it tells the player where to start.
		if (survey >= 0) {
			titles.add(I18n.translate("terminal.surrogate.survey.title"));
			bodies.add(surveyPage(survey));
		}
		// The mission board, last, and only on a hub the conference in act four opened one on. It arrives on
		// its own payload immediately before this screen, so by the time we are building pages it is here.
		if (ClientMissionState.hasBoard() && TerminalBlockEntity.HUB.equals(unit)) {
			titles.add(I18n.translate("terminal.surrogate.board.title"));
			bodies.add(boardPage());
		}
		openedAt = Util.getMeasuringTimeMs();
	}

	/**
	 * The survey page: every subject on the table, with Okafor's note under the ones that have a reading
	 * and a blank under the ones that do not. The blanks are the point — the page is the checklist.
	 */
	private static String surveyPage(int survey) {
		StringBuilder out = new StringBuilder();
		dev.psyda.surrogate.fauna.Specimen[] all = dev.psyda.surrogate.fauna.Specimen.all();
		out.append(I18n.translate("terminal.surrogate.survey.header", Integer.bitCount(survey), all.length)).append("\n\n");
		for (dev.psyda.surrogate.fauna.Specimen specimen : all) {
			boolean known = (survey & specimen.bit()) != 0;
			out.append(known ? "+ " : "- ").append(I18n.translate(specimen.nameKey())).append('\n');
			out.append("  ").append(known ? I18n.translate(specimen.noteKey())
					: I18n.translate("terminal.surrogate.survey.unread")).append("\n\n");
		}
		return out.toString();
	}

	/**
	 * The campaign's checklist: everyone still out there, what their air is doing and what is in the way.
	 *
	 * <p>The blocked column is the whole value of the page. Six names with six states is a list; six names
	 * with "the Rift" written next to three of them is a plan.
	 */
	private static String boardPage() {
		StringBuilder out = new StringBuilder();
		int home = 0;
		for (MissionPayload.Row row : ClientMissionState.rows) {
			if (row.state() == MissionPayload.HOME) home++;
		}
		out.append(I18n.translate("terminal.surrogate.board.header", home, ClientMissionState.rows.size())).append("\n\n");
		for (MissionPayload.Row row : ClientMissionState.rows) {
			Survivor who = Survivor.byId(row.survivor());
			String state = switch (row.state()) {
				case MissionPayload.HOME -> I18n.translate("terminal.surrogate.board.home");
				case MissionPayload.ABOARD -> I18n.translate("terminal.surrogate.board.aboard");
				case MissionPayload.REACHED -> I18n.translate("terminal.surrogate.board.reached");
				default -> I18n.translate("terminal.surrogate.board.unreached");
			};
			String mark = row.state() == MissionPayload.HOME ? "+ " : row.state() == MissionPayload.ABOARD ? "> " : "- ";
			out.append(mark).append(I18n.translate(who.nameKey())).append(" - ").append(state).append('\n');
			// A scrubber reading only means something while they are still living behind it.
			if (row.state() != MissionPayload.HOME && row.state() != MissionPayload.ABOARD && row.scrubber() >= 0) {
				String air = I18n.translate("terminal.surrogate.board.scrubber", row.scrubber());
				out.append(row.scrubber() < 60 ? "! " : "  ").append(air).append('\n');
			}
			if (!row.blocked().isEmpty()) {
				out.append("  ").append(I18n.translate("terminal.surrogate.board.blocked", I18n.translate(row.blocked()))).append('\n');
			}
			out.append('\n');
		}
		for (String note : ClientMissionState.notes) out.append("# ").append(I18n.translate(note)).append('\n');
		return out.toString();
	}

	@Override
	protected void init() {
		w = Math.min(420, width - 16);
		h = Math.min(260, height - 16);
		x = (width - w) / 2;
		y = (height - h) / 2;
		listWidth = 118;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		long now = Util.getMeasuringTimeMs();
		boolean blink = (now / 500) % 2 == 0;

		// Frame and header.
		context.fill(x, y, x + w, y + h, BLACK);
		context.fill(x, y, x + w, y + 1, GREEN_DIM);
		context.fill(x, y + h - 1, x + w, y + h, GREEN_DIM);
		context.fill(x, y, x + 1, y + h, GREEN_DIM);
		context.fill(x + w - 1, y, x + w, y + h, GREEN_DIM);
		String unitName = I18n.hasTranslation("terminal.surrogate." + unit + ".name") ? I18n.translate("terminal.surrogate." + unit + ".name") : unit.toUpperCase();
		Text header = Text.translatable("terminal.surrogate.header", unitName);
		context.drawTextWithShadow(textRenderer, header, x + 8, y + 6, GREEN);
		String clock = TransitClientState.aboard() ? String.format("D%d %02d:%02d", TransitClientState.day(), (int) TransitClientState.hours(), (int) ((TransitClientState.hours() % 1f) * 60f)) : "";
		if (!clock.isEmpty()) context.drawTextWithShadow(textRenderer, clock, x + w - 8 - textRenderer.getWidth(clock), y + 6, GREEN_DIM);
		context.fill(x + 1, y + 18, x + w - 1, y + 19, GREEN_DARK);

		// The file list.
		int top = y + 24;
		context.fill(x + listWidth, y + 19, x + listWidth + 1, y + h - 1, GREEN_DARK);
		for (int i = 0; i < titles.size(); i++) {
			int rowY = top + i * 12;
			boolean on = i == selected;
			if (on) context.fill(x + 4, rowY - 2, x + listWidth - 4, rowY + 9, GREEN_DARK);
			String title = (on ? "> " : "  ") + titles.get(i);
			context.drawTextWithShadow(textRenderer, title, x + 8, rowY, on ? GREEN : GREEN_DIM);
		}
		Text keys = Text.translatable("terminal.surrogate.keys");
		context.drawTextWithShadow(textRenderer, keys, x + 8, y + h - 12, GREEN_DARK);

		// The open page, typed out.
		int bodyX = x + listWidth + 10;
		int bodyW = w - listWidth - 20;
		int bodyY = top;
		String title = titles.get(selected);
		context.drawTextWithShadow(textRenderer, title, bodyX, bodyY, AMBER);
		context.fill(bodyX, bodyY + 10, bodyX + textRenderer.getWidth(title), bodyY + 11, GREEN_DARK);
		bodyY += 16;
		String body = selected == 0 ? status() : bodies.get(selected);
		int reveal = (int) ((now - openedAt) / 6);
		String shown = body.length() > reveal ? body.substring(0, Math.max(0, reveal)) : body;
		int maxY = y + h - 16;
		for (String paragraph : shown.split("\n")) {
			if (paragraph.isEmpty()) {
				bodyY += 5;
				continue;
			}
			int color = paragraph.startsWith("!") ? RED : paragraph.startsWith("#") ? AMBER : GREEN;
			String text = paragraph.startsWith("!") || paragraph.startsWith("#") ? paragraph.substring(1).trim() : paragraph;
			for (OrderedText line : textRenderer.wrapLines(Text.literal(text), bodyW)) {
				if (bodyY > maxY) break;
				context.drawTextWithShadow(textRenderer, line, bodyX, bodyY, color);
				bodyY += 10;
			}
		}
		if (blink && bodyY <= maxY) context.fill(bodyX, bodyY, bodyX + 5, bodyY + 8, GREEN);

		// Scanlines over the lot.
		for (int row = y + 1; row < y + h - 1; row += 2) context.fill(x + 1, row, x + w - 1, row + 1, SCANLINE);
	}

	/** The live page: what this unit can see right now. */
	private String status() {
		StringBuilder out = new StringBuilder();
		if (TransitClientState.aboard()) {
			out.append(I18n.translate("terminal.surrogate.status.ship")).append('\n');
			out.append(I18n.translate("terminal.surrogate.status.day", TransitClientState.day())).append('\n');
			int km = TransitClientState.distanceKm();
			out.append(km <= 0 || TransitClientState.descending() ? I18n.translate("hud.surrogate.transit.orbit")
					: I18n.translate("hud.surrogate.transit.distance", String.format("%,d", km))).append('\n');
			out.append(I18n.translate("hud.surrogate.transit.gravity", TransitClientState.gravity() ? "1.0" : "0.0")).append('\n');
			out.append('\n');
			if (TransitClientState.breach()) out.append("! ").append(I18n.translate("terminal.surrogate.status.breach")).append('\n');
			else if (TransitClientState.alarm()) out.append("! ").append(I18n.translate("hud.surrogate.transit.alarm")).append('\n');
			else if (!TransitClientState.engine()) out.append("# ").append(I18n.translate("terminal.surrogate.status.engine_off")).append('\n');
			else out.append(I18n.translate("terminal.surrogate.status.nominal")).append('\n');
		} else {
			out.append(I18n.translate("terminal.surrogate.status.site")).append('\n');
			String air = switch (ClientPilotState.airState) {
				case Exposure.LEAK -> "! " + I18n.translate("hud.surrogate.air.leak");
				case Exposure.EXPOSED -> "! " + I18n.translate("hud.surrogate.air.exposed");
				case Exposure.SEALED -> I18n.translate("hud.surrogate.air.sealed");
				default -> I18n.translate("hud.surrogate.air.safe");
			};
			out.append(I18n.translate("hud.surrogate.air")).append(": ").append(air).append('\n');
			out.append(I18n.translate("hud.surrogate.air_quality", Math.round(ClientPilotState.airQuality * 100f))).append('\n');
			out.append(I18n.translate("hud.surrogate.toxin", Math.round(ClientPilotState.toxin * 100f))).append('\n');
			out.append(I18n.translate("terminal.surrogate.status.fatigue", Math.round(ClientPilotState.fatigueFraction() * 100f))).append('\n');
		}
		out.append('\n');
		out.append(I18n.translate("terminal.surrogate.status.footer"));
		return out.toString();
	}

	private void select(int index) {
		if (index < 0 || index >= titles.size() || index == selected) return;
		selected = index;
		openedAt = Util.getMeasuringTimeMs();
		if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
				net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1.7f, 0.3f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= x && mouseX < x + listWidth && mouseY >= y + 22) {
			int index = (int) ((mouseY - (y + 22)) / 12);
			if (index >= 0 && index < titles.size()) {
				select(index);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
			select(Math.min(titles.size() - 1, selected + 1));
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
			select(Math.max(0, selected - 1));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
