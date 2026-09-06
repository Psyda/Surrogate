package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.client.ClientPilotState;
import dev.psyda.surrogate.client.HazardClientState;
import dev.psyda.surrogate.client.SurrogateClient;
import dev.psyda.surrogate.client.cinematic.CinematicOverlay;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.network.CinematicPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Pilot overlay: boot screen, hull/power/fatigue readouts, and the tiredness vignette for the body. */
@Environment(EnvType.CLIENT)
public final class PilotHud {
	private static final int PANEL = 0x99000000;
	private static final int CYAN = 0xFF22D3EE;
	private static final int CYAN_DARK = 0xFF0E3A45;
	private static final int RED = 0xFFEF4444;
	private static final int RED_DARK = 0xFF4A1414;
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFF59E0B;

	private PilotHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden) return;
		// A cutscene camera is not the chassis lens: no feed filter or readouts over it.
		if (CinematicState.isCameraDetached()) return;
		// Every fill would otherwise flush the GUI buffer on its own; the feed filter alone is hundreds of them.
		context.draw(() -> renderLayer(context, tickCounter, client, player));
	}

	private static void renderLayer(DrawContext context, RenderTickCounter tickCounter, MinecraftClient client, ClientPlayerEntity player) {
		TextRenderer font = client.textRenderer;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();

		if (ClientPilotState.unconscious > 0) {
			context.fill(0, 0, width, height, 0xF4000000);
			context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.unconscious"), width / 2, height / 2 - 4, 0xFFFF5555);
			return;
		}

		float fatigue = ClientPilotState.fatigueFraction();
		if (fatigue > 0.75f) {
			int alpha = (int) (MathHelper.clamp((fatigue - 0.75f) / 0.25f, 0f, 1f) * 150);
			context.fill(0, 0, width, height, alpha << 24);
		}

		if (!(player.getVehicle() instanceof RobotEntity robot) || !robot.isPilot(player)) {
			renderToxinVignette(context, width, height, ClientPilotState.toxin);
			renderBodyAir(context, font, width, player.age);
			if (fatigue >= 0.5f) {
				Text text = Text.translatable("hud.surrogate.fatigue", Math.round(fatigue * 100))
						.formatted(fatigue >= 0.9f ? Formatting.RED : Formatting.YELLOW);
				context.drawTextWithShadow(font, text, 6, 6, 0xFFFFFFFF);
			}
			return;
		}

		RobotState state = robot.getState();
		if (state == RobotState.BOOTING || state == RobotState.SHUTTING_DOWN) {
			renderBootScreen(context, font, width, height, robot, state, tickCounter.getTickDelta(false), player.age);
			return;
		}
		renderCameraFilter(context, width, height, robot, player.age);
		renderPanel(context, font, width, robot, fatigue, player.age);
	}

	/**
	 * Camera-feed look while piloting: scanlines, a soft vignette, and a rolling refresh band. As the hull
	 * takes damage the feed degrades into static, a red warning wash, and dropped frames.
	 */
	private static void renderCameraFilter(DrawContext context, int width, int height, RobotEntity robot, int age) {
		float hull = robot.getMaxHealth() <= 0 ? 0f : robot.getHealth() / robot.getMaxHealth();
		// No noise above 75% hull; full snow at zero.
		float fuzz = MathHelper.clamp((0.75f - hull) / 0.75f, 0f, 1f);
		long time = System.currentTimeMillis();

		// Vignette: darker, slightly cyan edges so it reads as a lens.
		int clear = 0x00000000;
		int vertical = height / 3;
		int horizontal = width / 4;
		int tint = 0x88000A0E;
		context.fillGradient(0, 0, width, vertical, tint, clear);
		context.fillGradient(0, height - vertical, width, height, clear, tint);
		fillGradientHorizontal(context, 0, 0, horizontal, height, tint, clear);
		fillGradientHorizontal(context, width - horizontal, 0, width, height, clear, tint);

		// Scanlines.
		for (int y = 0; y < height; y += 3) {
			context.fill(0, y, width, y + 1, 0x16000000);
		}

		// Rolling refresh band.
		int bandY = (int) ((time / 12) % (height + 60)) - 60;
		context.fillGradient(0, bandY, width, bandY + 30, 0x00FFFFFF, 0x12FFFFFF);
		context.fillGradient(0, bandY + 30, width, bandY + 60, 0x12FFFFFF, 0x00FFFFFF);

		if (fuzz <= 0f) return;

		// Static: reseed every 40 ms so the snow flickers instead of blurring into a solid haze.
		Random random = Random.create(time / 40);
		int specks = (int) (fuzz * fuzz * 1400) + (int) (fuzz * 200);
		for (int i = 0; i < specks; i++) {
			int x = random.nextInt(width);
			int y = random.nextInt(height);
			int w = 1 + random.nextInt(4);
			int grey = 0x60 + random.nextInt(0x9F);
			int alpha = 0x18 + (int) (random.nextFloat() * fuzz * 0x70);
			context.fill(x, y, x + w, y + 1 + random.nextInt(2), (alpha << 24) | (grey << 16) | (grey << 8) | grey);
		}

		// Horizontal tearing: a few bright smeared lines that jump around.
		int tears = (int) (fuzz * 6);
		for (int i = 0; i < tears; i++) {
			if (random.nextFloat() > 0.6f) continue;
			int y = random.nextInt(height);
			int x = random.nextInt(width / 2);
			int w = width / 4 + random.nextInt(width / 2);
			context.fill(x, y, x + w, y + 1 + random.nextInt(3), 0x40FFFFFF);
		}

		// Warning wash and dropped frames once the hull is critical.
		if (hull < 0.34f) {
			float pulse = 0.5f + 0.5f * MathHelper.sin((age + (time % 50) / 50f) * 0.25f);
			context.fill(0, 0, width, height, ((int) (0x28 * pulse) << 24) | 0xFF2020);
			if (Random.create(time / 90).nextFloat() < fuzz * 0.25f) {
				context.fill(0, 0, width, height, 0x60000000);
			}
		}
	}

	/** Green haze creeping in from the edges as the body fills with toxin. */
	private static void renderToxinVignette(DrawContext context, int width, int height, float toxin) {
		if (toxin <= 0.15f) return;
		int alpha = (int) (MathHelper.clamp((toxin - 0.15f) / 0.85f, 0f, 1f) * 0xA0);
		int color = (alpha << 24) | 0x3A6B12;
		int vertical = height / 3;
		int horizontal = width / 3;
		context.fillGradient(0, 0, width, vertical, color, 0);
		context.fillGradient(0, height - vertical, width, height, 0, color);
		fillGradientHorizontal(context, 0, 0, horizontal, height, color, 0);
		fillGradientHorizontal(context, width - horizontal, 0, width, height, 0, color);
	}

	/** Top-right readout of what the body is breathing, shown whenever it is not simply safe. */
	private static void renderBodyAir(DrawContext context, TextRenderer font, int width, int age) {
		int state = ClientPilotState.airState;
		float toxin = ClientPilotState.toxin;
		if (state == Exposure.SAFE && toxin <= 0f) return;
		int panelWidth = 130;
		int x = width - panelWidth - 6;
		// Under the objective banner, which owns the top edge while it is up.
		int y = CinematicState.objectiveState != CinematicPayloads.OBJECTIVE_CLEAR ? CinematicOverlay.objectiveHeight : 6;
		context.fill(x - 3, y - 3, x + panelWidth, y + 36, PANEL);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.air").formatted(Formatting.AQUA), x, y, 0xFFFFFFFF);
		Text status = airStatusText(state, age);
		context.drawTextWithShadow(font, status, x + panelWidth - 6 - font.getWidth(status), y, 0xFFFFFFFF);

		y += 12;
		float quality = state == Exposure.SAFE ? 1f : state == Exposure.EXPOSED ? 0f : ClientPilotState.airQuality;
		bar(context, x, y, 60, quality, CYAN_DARK, quality < 0.4f ? RED : quality < 0.75f ? AMBER : GREEN);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.air_quality", Math.round(quality * 100)), x + 66, y - 1, 0xFFDDDDDD);

		y += 12;
		bar(context, x, y, 60, toxin, 0xFF14301A, toxin >= 0.6f ? RED : toxin >= 0.35f ? AMBER : 0xFF84CC16);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.toxin", Math.round(toxin * 100)), x + 66, y - 1, 0xFFDDDDDD);
	}

	private static Text airStatusText(int state, int age) {
		boolean blink = (age / 8) % 2 == 0;
		return switch (state) {
			case Exposure.SEALED -> Text.translatable("hud.surrogate.air.sealed").formatted(Formatting.GREEN);
			case Exposure.LEAK -> Text.translatable("hud.surrogate.air.leak").formatted(blink ? Formatting.RED : Formatting.GOLD);
			case Exposure.EXPOSED -> Text.translatable("hud.surrogate.air.exposed").formatted(blink ? Formatting.RED : Formatting.DARK_RED);
			default -> Text.translatable("hud.surrogate.air.safe").formatted(Formatting.GRAY);
		};
	}

	/** One line in the pilot panel about the body left behind in the chair. */
	private static Text bodyLine(int age) {
		int quality = Math.round(ClientPilotState.airQuality * 100);
		int toxin = Math.round(ClientPilotState.toxin * 100);
		boolean blink = (age / 8) % 2 == 0;
		return switch (ClientPilotState.airState) {
			case Exposure.SEALED -> Text.translatable("hud.surrogate.body.sealed", quality, toxin).formatted(Formatting.GREEN);
			case Exposure.LEAK -> Text.translatable("hud.surrogate.body.leak", quality, toxin).formatted(blink ? Formatting.RED : Formatting.GOLD);
			case Exposure.EXPOSED -> Text.translatable("hud.surrogate.body.exposed", toxin).formatted(blink ? Formatting.RED : Formatting.DARK_RED);
			default -> Text.translatable("hud.surrogate.body.safe").formatted(Formatting.GRAY);
		};
	}

	static void fillGradientHorizontal(DrawContext context, int x1, int y1, int x2, int y2, int left, int right) {
		int steps = 12;
		int step = Math.max(1, (x2 - x1) / steps);
		for (int x = x1; x < x2; x += step) {
			float t = (float) (x - x1) / (x2 - x1);
			context.fill(x, y1, Math.min(x + step, x2), y2, lerpColor(left, right, t));
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int alpha = (int) MathHelper.lerp(t, (a >>> 24), (b >>> 24));
		int red = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int green = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int blue = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static void renderBootScreen(DrawContext context, TextRenderer font, int width, int height, RobotEntity robot,
										 RobotState state, float tickDelta, int age) {
		context.fill(0, 0, width, height, 0xFF000000);
		for (int y = 0; y < height; y += 3) {
			context.fill(0, y, width, y + 1, 0x14FFFFFF);
		}
		int total = state == RobotState.BOOTING ? ClientPilotState.bootTicks : ClientPilotState.shutdownTicks;
		float progress = MathHelper.clamp((robot.getProgress() + tickDelta) / total, 0f, 1f);
		int cx = width / 2;
		int cy = height / 2;
		context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.os").formatted(Formatting.AQUA), cx, cy - 34, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(font, Text.translatable(state == RobotState.BOOTING ? "hud.surrogate.booting" : "hud.surrogate.shutting_down"),
				cx, cy - 18, 0xFFDDDDDD);
		context.fill(cx - 80, cy, cx + 80, cy + 8, CYAN_DARK);
		context.fill(cx - 79, cy + 1, cx - 79 + (int) (158 * progress), cy + 7, CYAN);
		context.drawCenteredTextWithShadow(font, Text.literal(Math.round(progress * 100) + "%"), cx, cy + 14, 0xFFAAAAAA);
		if ((age / 10) % 2 == 0) {
			context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.link_stable").formatted(Formatting.DARK_GRAY), cx, cy + 34, 0xFFFFFFFF);
		}
	}

	private static void renderPanel(DrawContext context, TextRenderer font, int width, RobotEntity robot, float fatigue, int age) {
		int x = 6;
		int y = CinematicState.objectiveState != CinematicPayloads.OBJECTIVE_CLEAR ? CinematicOverlay.objectiveHeight : 6;
		int panelWidth = 170;
		boolean weather = HazardClientState.warning || HazardClientState.stormReading() > 0f || HazardClientState.seismicReading() > 0f;
		context.fill(x - 3, y - 3, x + panelWidth, y + (weather ? 96 : 84), PANEL);

		context.drawTextWithShadow(font, robot.getName().copy().formatted(Formatting.AQUA), x, y, 0xFFFFFFFF);
		Text stateText = Text.translatable(robot.getState().translationKey()).formatted(Formatting.GREEN);
		context.drawTextWithShadow(font, stateText, x + panelWidth - 6 - font.getWidth(stateText), y, 0xFFFFFFFF);

		float hullFraction = robot.getMaxHealth() <= 0 ? 0f : robot.getHealth() / robot.getMaxHealth();
		y += 12;
		bar(context, x, y, 90, hullFraction, RED_DARK, hullFraction < 0.34f ? RED : GREEN);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.hull", formatHealth(robot.getHealth()), formatHealth(robot.getMaxHealth())),
				x + 96, y - 1, 0xFFDDDDDD);

		float power = robot.getEnergyFraction();
		y += 12;
		bar(context, x, y, 90, power, CYAN_DARK, power < 0.1f ? RED : CYAN);
		int seconds = robot.getEnergy() / ClientPilotState.idleDrainPerTick / 20;
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.power", Math.round(power * 100), String.format("%d:%02d", seconds / 60, seconds % 60)),
				x + 96, y - 1, 0xFFDDDDDD);

		y += 12;
		bar(context, x, y, 90, fatigue, 0xFF3A2E10, fatigue >= 0.9f ? RED : fatigue >= 0.75f ? AMBER : 0xFFFDE047);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.pilot", Math.round(fatigue * 100)), x + 96, y - 1, 0xFFDDDDDD);

		float grime = robot.getContamination();
		y += 12;
		bar(context, x, y, 90, grime, 0xFF2E2A14, grime >= 0.6f ? AMBER : 0xFFA3A34A);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.contamination", Math.round(grime * 100)), x + 96, y - 1, 0xFFDDDDDD);

		if (weather) {
			y += 12;
			renderWeatherRow(context, font, x, y, age);
		}

		y += 12;
		context.drawTextWithShadow(font, bodyLine(age), x, y - 1, 0xFFFFFFFF);

		y += 12;
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.menu_hint", SurrogateClient.PILOT_MENU.getBoundKeyLocalizedText())
				.formatted(Formatting.DARK_GRAY), x, y, 0xFFFFFFFF);

		boolean blink = (age / 8) % 2 == 0;
		if (blink && ClientPilotState.toxinRising && ClientPilotState.toxin >= 0.05f) {
			context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.body_exposed").formatted(Formatting.RED, Formatting.BOLD), width / 2, 44, 0xFFFFFFFF);
		}
		if (blink && power < 0.1f) {
			context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.low_power").formatted(Formatting.RED, Formatting.BOLD), width / 2, 30, 0xFFFFFFFF);
		} else if (blink && hullFraction < 0.34f) {
			context.drawCenteredTextWithShadow(font, Text.translatable("hud.surrogate.hull_critical").formatted(Formatting.RED, Formatting.BOLD), width / 2, 30, 0xFFFFFFFF);
		}
	}

	/**
	 * The mast's two readings, side by side: how hard the sky is pushing on the link, and how close the
	 * nearest borer is. The seismic half is only there while something under the floor is moving.
	 */
	private static void renderWeatherRow(DrawContext context, TextRenderer font, int x, int y, int age) {
		// Through the run-up there is no storm to measure yet, so the mast shows its own rising twitch.
		float pulse = 0.5f + 0.5f * MathHelper.sin(age * 0.2f);
		float storm = HazardClientState.stormReading();
		float mag = storm > 0f ? storm : HazardClientState.warning ? 0.1f + 0.15f * pulse : 0f;
		bar(context, x, y, 28, mag, 0xFF3A2E10, mag >= 0.6f ? RED : AMBER);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.mag", Math.round(mag * 100))
				.formatted(mag >= 0.6f ? Formatting.RED : Formatting.GOLD), x + 32, y - 1, 0xFFFFFFFF);

		float seismic = HazardClientState.seismicReading();
		if (seismic <= 0f) return;
		bar(context, x + 82, y, 28, seismic, 0xFF3A1010, seismic >= 0.6f ? RED : AMBER);
		context.drawTextWithShadow(font, Text.translatable("hud.surrogate.seismic", Math.round(seismic * 100))
				.formatted(seismic >= 0.6f ? Formatting.RED : Formatting.GOLD), x + 114, y - 1, 0xFFFFFFFF);
	}

	private static void bar(DrawContext context, int x, int y, int width, float fraction, int background, int fill) {
		context.fill(x, y, x + width, y + 6, background);
		context.fill(x + 1, y + 1, x + 1 + (int) ((width - 2) * MathHelper.clamp(fraction, 0f, 1f)), y + 5, fill);
	}

	private static String formatHealth(float value) {
		return value == (int) value ? Integer.toString((int) value) : String.format("%.1f", value);
	}
}
