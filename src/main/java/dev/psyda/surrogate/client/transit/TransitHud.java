package dev.psyda.surrogate.client.transit;

import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.client.ClientPilotState;
import dev.psyda.surrogate.client.cinematic.CinematicOverlay;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.entity.RobotEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The ship's readout in the corner of the eye: what day it is, how far Sallow is, how long is left, and
 * whether anything is wrong. The numbers are the clock of the week; the sky outside is the other clock.
 */
@Environment(EnvType.CLIENT)
public final class TransitHud {
	private static final int PANEL = 0x99000000;
	private static final int CYAN = 0xFF22D3EE;
	private static final int GREY = 0xFFB8B8B8;
	private static final int DIM = 0xFF7A7A7A;
	private static final int RED = 0xFFEF4444;
	private static final int AMBER = 0xFFF59E0B;
	private static final int GREEN = 0xFF4ADE80;

	private TransitHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) return;
		if (!TransitClientState.aboard()) return;
		if (CinematicState.isCameraDetached()) return;
		if (client.player.getVehicle() instanceof RobotEntity) return;
		context.draw(() -> renderPanel(context, tickCounter, client));
	}

	private static void renderPanel(DrawContext context, RenderTickCounter tickCounter, MinecraftClient client) {
		TextRenderer font = client.textRenderer;
		float delta = tickCounter.getTickDelta(false);
		int age = client.player.age;
		boolean blink = (age / 8) % 2 == 0;

		int x = 6;
		int y = ClientPilotState.fatigueFraction() >= 0.5f ? 18 : 6;
		// The objective banner sits at the top of the screen; on a narrow window the two would collide.
		if (CinematicState.objectiveState != dev.psyda.surrogate.network.CinematicPayloads.OBJECTIVE_CLEAR) y += CinematicOverlay.objectiveHeight;
		int width = 178;
		int height = 46;
		context.fill(x - 3, y - 3, x + width, y + height, PANEL);

		Text ship = Text.translatable("hud.surrogate.transit.ship");
		context.drawTextWithShadow(font, ship, x, y, CYAN);
		int day = TransitClientState.day();
		Text dayText = TransitClientState.descending()
				? Text.translatable("hud.surrogate.transit.descent").formatted(Formatting.RED)
				: Text.translatable("hud.surrogate.transit.day", Math.min(7, day));
		context.drawTextWithShadow(font, dayText, x + width - 6 - font.getWidth(dayText), y, GREY);

		y += 12;
		int km = TransitClientState.distanceKm();
		Text distance = km <= 0 || TransitClientState.descending()
				? Text.translatable("hud.surrogate.transit.orbit")
				: Text.translatable("hud.surrogate.transit.distance", String.format("%,d", km));
		context.drawTextWithShadow(font, distance, x, y, GREY);

		y += 12;
		float eta = TransitClientState.etaHours();
		int etaDays = (int) (eta / 24f);
		int etaHours = (int) (eta % 24f);
		Text etaText = km <= 0 || TransitClientState.descending()
				? Text.translatable("hud.surrogate.transit.eta.none")
				: Text.translatable("hud.surrogate.transit.eta", etaDays, String.format("%02d", etaHours));
		context.drawTextWithShadow(font, etaText, x, y, GREY);
		Text gravity = Text.translatable("hud.surrogate.transit.gravity", TransitClientState.gravity() ? "1.0" : "0.0");
		context.drawTextWithShadow(font, gravity, x + width - 6 - font.getWidth(gravity), y, TransitClientState.gravity() ? GREY : (blink ? AMBER : GREY));

		y += 12;
		Text status;
		int color;
		if (TransitClientState.descending()) {
			status = Text.translatable("hud.surrogate.transit.descent");
			color = blink ? RED : AMBER;
		} else if (TransitClientState.alarm()) {
			status = Text.translatable(TransitClientState.breach() ? "hud.surrogate.transit.breach" : "hud.surrogate.transit.alarm");
			color = blink ? RED : DIM;
		} else if (TransitClientState.turningOver(delta)) {
			status = Text.translatable("hud.surrogate.transit.turnover");
			color = AMBER;
		} else if (!TransitClientState.gravity()) {
			status = Text.translatable("hud.surrogate.transit.freefall");
			color = AMBER;
		} else if (ClientPilotState.airState == Exposure.LEAK || ClientPilotState.airState == Exposure.EXPOSED) {
			status = Text.translatable("hud.surrogate.transit.air_bad");
			color = blink ? RED : DIM;
		} else if (TransitClientState.lights() == 1) {
			status = Text.translatable("hud.surrogate.transit.night");
			color = DIM;
		} else {
			status = Text.translatable("hud.surrogate.transit.nominal");
			color = GREEN;
		}
		context.drawTextWithShadow(font, status, x, y, color);
		int hours = (int) TransitClientState.hours();
		int minutes = (int) ((TransitClientState.hours() - hours) * 60f);
		Text clock = Text.literal(String.format("%02d:%02d", hours, minutes));
		context.drawTextWithShadow(font, clock, x + width - 6 - font.getWidth(clock), y, DIM);
	}
}
