package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.network.SurveyPayloads;
import dev.psyda.surrogate.survey.SurveyScan;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * The survey table's picture: a wireframe of the ground, drawn in light, spinning slowly, with the places
 * you know about glowing on it.
 *
 * <p>It is a projection of the heightmap and nothing more — no textures, no blocks, no shading model. Every
 * sample is a point, points that are neighbours get a line between them, and the whole lattice is rotated
 * about its middle and squashed by a fixed tilt. The gaps are real: eight blocks between samples means a
 * mesa comes out as a stack of terraces and a chasm can vanish between two columns. That is what the
 * instrument can see, and the picture is honest about it.
 *
 * <p>Controls are one drag and one scroll: turn it and zoom it. There is nothing else to do here.
 */
@Environment(EnvType.CLIENT)
public class SurveyScreen extends Screen {
	/** How far the lattice is tilted away from head-on. Fixed: the fun of it is that it is not a map. */
	private static final float TILT = 0.55f;
	/** Vertical exaggeration. Real relief on Sallow is subtle and reads as flat without this. */
	private static final float RELIEF = 1.35f;

	private static final int GRID = 0x4478E0B0;
	private static final int GRID_HIGH = 0xAA9CFFD8;
	private static final int SKY = 0xE0060B0A;
	private static final int FRAME = 0xFF1E4438;

	private final SurveyPayloads.Survey survey;

	/** Where the camera is: heading in radians, and blocks-per-pixel zoom. */
	private float heading = 0.6f;
	private float zoom = 1.0f;
	private boolean dragging;
	private double dragFrom;
	private float dragHeading;

	/** The lowest and highest sample in the picture, so the relief scales to whatever is actually here. */
	private final int floor;
	private final int ceiling;

	public SurveyScreen(SurveyPayloads.Survey survey) {
		super(Text.translatable("screen.surrogate.survey"));
		this.survey = survey;
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int height : survey.heights()) {
			if (height == SurveyScan.UNKNOWN) continue;
			low = Math.min(low, height);
			high = Math.max(high, height);
		}
		// A picture with nothing in it still has to divide by something.
		this.floor = low == Integer.MAX_VALUE ? 0 : low;
		this.ceiling = high == Integer.MIN_VALUE ? floor + 1 : Math.max(high, floor + 1);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, SKY);
		int midX = width / 2;
		int midY = height / 2 + 20;
		float scale = Math.min(width, height) / (float) SurveyScan.SIZE * 1.5f * zoom;

		drawLattice(context, midX, midY, scale);
		drawLights(context, midX, midY, scale);
		drawChrome(context);
		super.render(context, mouseX, mouseY, delta);
	}

	/**
	 * The ground. Each sample is joined east and south to its neighbour, so the mesh is drawn as two line
	 * segments per point rather than as quads — which is both cheaper and the reason it looks like an
	 * instrument readout instead of a landscape.
	 */
	private void drawLattice(DrawContext context, int midX, int midY, float scale) {
		List<Integer> heights = survey.heights();
		int size = SurveyScan.SIZE;
		float relief = scale * RELIEF * SurveyScan.SIZE / Math.max(1f, ceiling - floor) * 0.12f;
		float cos = MathHelper.cos(heading);
		float sin = MathHelper.sin(heading);
		for (int row = 0; row < size; row++) {
			for (int col = 0; col < size; col++) {
				int here = heights.get(row * size + col);
				if (here == SurveyScan.UNKNOWN) continue;
				float[] a = project(col, row, here, midX, midY, scale, relief, cos, sin);
				// Brightness by altitude: the high ground reads first, which is what makes the mesas legible.
				float t = (here - floor) / (float) Math.max(1, ceiling - floor);
				int colour = blend(GRID, GRID_HIGH, t);
				if (col + 1 < size) {
					int east = heights.get(row * size + col + 1);
					if (east != SurveyScan.UNKNOWN) {
						float[] b = project(col + 1, row, east, midX, midY, scale, relief, cos, sin);
						line(context, a, b, colour);
					}
				}
				if (row + 1 < size) {
					int south = heights.get((row + 1) * size + col);
					if (south != SurveyScan.UNKNOWN) {
						float[] b = project(col, row + 1, south, midX, midY, scale, relief, cos, sin);
						line(context, a, b, colour);
					}
				}
			}
		}
	}

	/** Lattice coordinates to screen: rotate about the middle, tilt, then lift by height. */
	private float[] project(int col, int row, int height, int midX, int midY, float scale, float relief, float cos, float sin) {
		float x = (col - SurveyScan.HALF) * scale;
		float z = (row - SurveyScan.HALF) * scale;
		float rx = x * cos - z * sin;
		float rz = x * sin + z * cos;
		float y = (height - floor) * relief;
		return new float[]{midX + rx, midY + rz * TILT - y};
	}

	/**
	 * A line, drawn as a run of one-pixel fills. There is no line primitive in {@link DrawContext} and the
	 * segments here are a few pixels each, so Bresenham on rectangles is genuinely the cheap option.
	 */
	private void line(DrawContext context, float[] a, float[] b, int colour) {
		int x0 = Math.round(a[0]);
		int y0 = Math.round(a[1]);
		int x1 = Math.round(b[0]);
		int y1 = Math.round(b[1]);
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		int guard = 0;
		while (guard++ < 512) {
			if (x0 >= 0 && x0 < width && y0 >= 0 && y0 < height) context.fill(x0, y0, x0 + 1, y0 + 1, colour);
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 >= dy) {
				err += dy;
				x0 += sx;
			}
			if (e2 <= dx) {
				err += dx;
				y0 += sy;
			}
		}
	}

	/** The dots: home, shelters, the pad, and every beacon holding the picture up. */
	private void drawLights(DrawContext context, int midX, int midY, float scale) {
		float relief = scale * RELIEF * SurveyScan.SIZE / Math.max(1f, ceiling - floor) * 0.12f;
		float cos = MathHelper.cos(heading);
		float sin = MathHelper.sin(heading);
		List<Integer> heights = survey.heights();
		for (SurveyPayloads.Light light : survey.lights()) {
			int col = Math.round((light.x() - survey.centre().getX()) / (float) survey.step()) + SurveyScan.HALF;
			int row = Math.round((light.z() - survey.centre().getZ()) / (float) survey.step()) + SurveyScan.HALF;
			if (col < 0 || col >= SurveyScan.SIZE || row < 0 || row >= SurveyScan.SIZE) continue;
			int ground = heights.get(row * SurveyScan.SIZE + col);
			if (ground == SurveyScan.UNKNOWN) ground = floor;
			float[] at = project(col, row, ground, midX, midY, scale, relief, cos, sin);
			int colour = markColour(light.mark());
			// A dot with a stalk, so a light on a slope still reads as being on the ground under it.
			context.fill((int) at[0], (int) at[1], (int) at[0] + 1, (int) at[1] + 6, colour & 0x55FFFFFF);
			context.fill((int) at[0] - 2, (int) at[1] - 2, (int) at[0] + 3, (int) at[1] + 3, colour);
			if (textRenderer != null) {
				context.drawTextWithShadow(textRenderer, Text.translatable(light.label()),
						(int) at[0] + 5, (int) at[1] - 4, colour);
			}
		}
	}

	private static int markColour(int mark) {
		SurveyScan.Mark[] marks = SurveyScan.Mark.values();
		SurveyScan.Mark kind = mark >= 0 && mark < marks.length ? marks[mark] : SurveyScan.Mark.SHELTER;
		return switch (kind) {
			case HOME -> 0xFF7CFFB0;
			case SHELTER -> 0xFFFFD86E;
			case BEACON -> 0xFF6EE8FF;
			case ORPHAN -> 0xFFFF6E6E;
			case PAD -> 0xFFD08CFF;
		};
	}

	private static int blend(int from, int to, float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
		int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerp(from & 0xFF, to & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerp(int from, int to, float t) {
		return from + Math.round((to - from) * t);
	}

	/** The frame and the readout: what this is, how far it sees, and how to turn it. */
	private void drawChrome(DrawContext context) {
		context.fill(0, 0, width, 22, FRAME);
		context.fill(0, height - 18, width, height, FRAME);
		if (textRenderer == null) return;
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.surrogate.survey.title"), 8, 7, 0xFF9CFFD8);
		context.drawTextWithShadow(textRenderer,
				Text.translatable("screen.surrogate.survey.reach", survey.reach()),
				width - 8 - textRenderer.getWidth(Text.translatable("screen.surrogate.survey.reach", survey.reach())), 7, 0xFF7CE8C0);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.surrogate.survey.hint"), 8, height - 13, 0xFF5A9E86);
	}

	// ------------------------------------------------------------------ turning it

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			dragging = true;
			dragFrom = mouseX;
			dragHeading = heading;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			heading = dragHeading + (float) ((mouseX - dragFrom) * 0.008);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		zoom = MathHelper.clamp(zoom * (vertical > 0 ? 1.15f : 0.87f), 0.25f, 4.0f);
		return true;
	}

}
