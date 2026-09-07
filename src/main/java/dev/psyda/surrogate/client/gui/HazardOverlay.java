package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.client.HazardClientState;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.entity.RobotEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * What the weather does to the feed: the uplink tearing itself apart in a magnetic storm, and acid running
 * down the outside of the glass. Both belong to the chassis, so neither is drawn while the player is in
 * their own body, and neither costs anything while the readings are at zero.
 */
@Environment(EnvType.CLIENT)
public final class HazardOverlay {
	/** The grey a losing picture washes towards, and the green the belt leaves on the lens. */
	private static final int WASH = 0x9AA2A6;
	private static final int TEAR = 0xCFE2E6;
	private static final int ACID = 0x5A9E2A;
	/** Below this the uplink is clean; above it the picture is gone. Between them it is a straight ramp. */
	private static final float NOISE_FLOOR = 0.05f;
	private static final float NOISE_CEILING = 0.9f;

	private HazardOverlay() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden) return;
		// A cutscene camera is not the chassis lens, and neither is a pair of eyes in a chair.
		if (CinematicState.isCameraDetached() || !RobotEntity.isPiloting(player)) return;
		float delta = tickCounter.getTickDelta(false);
		float noise = HazardClientState.noiseLevel(delta);
		float rain = HazardClientState.rainLevel(delta);
		if (noise <= 0.001f && rain <= 0.001f) return;
		// Every fill would otherwise flush the GUI buffer on its own, and full snow is over a thousand of them.
		context.draw(() -> renderLayer(context, noise, rain));
	}

	private static void renderLayer(DrawContext context, float noise, float rain) {
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		if (noise > 0.001f) renderStatic(context, width, height, noise);
		if (rain > 0.001f) renderAcid(context, width, height, rain);
	}

	/**
	 * The link going: the colour drains out, tear bands walk down the picture, and the snow thickens until
	 * there is nothing left to see. Barely there at 0.15, blind at 0.9.
	 */
	private static void renderStatic(DrawContext context, int width, int height, float noise) {
		float strength = MathHelper.clamp((noise - NOISE_FLOOR) / (NOISE_CEILING - NOISE_FLOOR), 0f, 1f);
		if (strength <= 0f) return;
		long time = System.currentTimeMillis();

		// Colour going: a grey laid over everything pulls the whole feed towards nothing.
		context.fill(0, 0, width, height, withAlpha(WASH, strength * 0.36f));

		// Tear bands, each drifting down at its own speed so they slide past one another.
		int bands = 1 + (int) (strength * 7f);
		for (int i = 0; i < bands; i++) {
			// The drift has to be worked out in a long: a float loses whole seconds at this size of clock.
			long drift = (long) (time * (0.03 + i * 0.017));
			int y = (int) ((drift + i * 137L) % (height + 40L)) - 20;
			int thickness = 2 + (int) (strength * 12f);
			context.fill(0, y, width, y + thickness, withAlpha(TEAR, 0.12f + strength * 0.3f));
			context.fill(0, y + thickness, width, y + thickness + 2, withAlpha(0x000000, 0.1f + strength * 0.35f));
		}

		// Snow: reseeded every 40 ms so it flickers instead of blurring into a solid haze.
		Random random = Random.create(time / 40);
		int specks = (int) (strength * strength * 1200f) + (int) (strength * 200f);
		for (int i = 0; i < specks; i++) {
			int x = random.nextInt(width);
			int y = random.nextInt(height);
			int w = 1 + random.nextInt(5);
			int grey = 0x50 + random.nextInt(0xAF);
			int alpha = 0x18 + (int) (random.nextFloat() * strength * 0x90);
			context.fill(x, y, x + w, y + 1 + random.nextInt(2), (alpha << 24) | (grey << 16) | (grey << 8) | grey);
		}

		// Past three quarters gone the picture is not a picture any more, and no amount of snow says that.
		if (strength > 0.7f) {
			float blind = (strength - 0.7f) / 0.3f;
			context.fill(0, 0, width, height, withAlpha(0x2A3033, blind * 0.85f));
		}
	}

	/**
	 * Acid on the outside of the glass: a green cast in from the edges, and runs sliding down the sides of
	 * the picture where it collects.
	 */
	private static void renderAcid(DrawContext context, int width, int height, float rain) {
		int edge = withAlpha(ACID, rain * 0.45f);
		int clear = 0x00000000;
		context.fillGradient(0, 0, width, height / 5, edge, clear);
		context.fillGradient(0, height - height / 4, width, height, clear, edge);
		PilotHud.fillGradientHorizontal(context, 0, 0, width / 5, height, edge, clear);
		PilotHud.fillGradientHorizontal(context, width - width / 5, 0, width, height, clear, edge);

		// The runs themselves. Seeded off a constant, so a given streak keeps its lane and its width.
		long time = System.currentTimeMillis();
		Random random = Random.create(0x5AC1DL);
		int runs = 6 + (int) (rain * 10f);
		int lane = Math.max(2, width / 6);
		int head = withAlpha(ACID, rain * 0.8f);
		for (int i = 0; i < runs; i++) {
			boolean left = (i & 1) == 0;
			int offset = random.nextInt(lane);
			int x = left ? offset : width - 1 - offset;
			int w = 1 + random.nextInt(2);
			int period = 30 + random.nextInt(90);
			int length = height / 4 + random.nextInt(Math.max(1, height / 3));
			int y = (int) ((time / period + i * 211L) % (height + length)) - length;
			context.fillGradient(x, y, x + w, y + length, clear, head);
		}
	}

	private static int withAlpha(int rgb, float alpha) {
		int a = MathHelper.clamp(Math.round(alpha * 255f), 0, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
