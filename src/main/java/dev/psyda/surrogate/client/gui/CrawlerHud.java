package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.client.crawler.CrawlerClientState;
import dev.psyda.surrogate.client.crawler.PortholeRenderer;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.crawler.CrawlerSonar;
import dev.psyda.surrogate.network.CrawlerPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * The cabin's readout: heading, speed and charge for everyone aboard; at the docking console, the rear
 * camera with its reticle and how far the ring is from the collar and how many degrees off, which is how a
 * hull is backed onto a base.
 */
@Environment(EnvType.CLIENT)
public final class CrawlerHud {
	private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private CrawlerHud() {
	}

	/**
	 * The sonar: a round scope on the right of the screen, the hull at its centre facing up. Ground level
	 * with the hull is dim, anything higher brightens toward a wall, drops go dark, acid is blue, the living
	 * are white, and the nearest collar is a bracket the docking console backs the ring into. Bases within
	 * radar range show as marked blips, pinned to the rim when they are past the scope's reach. A sweep runs
	 * round it so it reads as a sensor and not a map.
	 */
	private static void drawSonar(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		CrawlerPayloads.Scan scan = CrawlerClientState.scan;
		int radius = Math.min(80, context.getScaledWindowHeight() / 4);
		int cx = context.getScaledWindowWidth() - radius - 16;
		int cy = context.getScaledWindowHeight() / 2;
		// The scope face.
		fillCircle(context, cx, cy, radius + 3, 0xCC0A1410);
		fillCircle(context, cx, cy, radius, 0xE0061A12);
		if (scan != null) {
			int size = scan.size();
			int half = size / 2;
			float cell = (float) radius / (half + 0.5f);
			byte[] cells = scan.cells();
			for (int gz = 0; gz < size; gz++) {
				for (int gx = 0; gx < size; gx++) {
					int value = cells[gz * size + gx] & 0xFF;
					int dx = gx - half;
					int dz = gz - half;
					if (dx * dx + dz * dz > half * half) continue;
					int color = cellColor(value);
					if (color == 0) continue;
					int sx = Math.round(cx + dx * cell);
					int sy = Math.round(cy - dz * cell);
					int s = Math.max(1, Math.round(cell));
					context.fill(sx - s / 2, sy - s / 2, sx - s / 2 + s, sy - s / 2 + s, color);
				}
			}
			float scale = cell / scan.step();
			if (scan.hasTarget()) {
				int tx = Math.round(cx + scan.targetX() * scale);
				int ty = Math.round(cy - scan.targetZ() * scale);
				int b = 4;
				context.fill(tx - b, ty - b, tx + b, ty - b + 1, 0xFFFDE047);
				context.fill(tx - b, ty + b - 1, tx + b, ty + b, 0xFFFDE047);
				context.fill(tx - b, ty - b, tx - b + 1, ty + b, 0xFFFDE047);
				context.fill(tx + b - 1, ty - b, tx + b, ty + b, 0xFFFDE047);
				// The line from the ring to the collar: back down it.
				context.fill(cx, Math.min(cy, ty), cx + 1, Math.max(cy, ty), 0x80FDE047);
			}
			float[] pois = scan.pois();
			for (int i = 0; i + 2 < pois.length; i += 3) {
				float px = pois[i] * scale;
				float pz = pois[i + 1] * scale;
				int kind = Math.round(pois[i + 2]);
				float d = (float) Math.hypot(px, pz);
				float rim = radius - 6;
				boolean far = d > rim;
				if (far && d > 0f) {
					px *= rim / d;
					pz *= rim / d;
				}
				int bx = Math.round(cx + px);
				int by = Math.round(cy - pz);
				int color = switch (kind) {
					case CrawlerPayloads.Scan.POI_HOME -> 0xFFA5F3FC;
					case CrawlerPayloads.Scan.POI_SITE_TWO -> 0xFFF0ABFC;
					case CrawlerPayloads.Scan.POI_SHELTER -> 0xFFFDBA74;
					default -> 0xFF86EFAC;
				};
				// A diamond, hollow when it is only a bearing.
				for (int k = 0; k <= 3; k++) {
					int w = 3 - k;
					if (far && k > 0 && k < 3) {
						context.fill(bx - w, by - k, bx - w + 1, by - k + 1, color);
						context.fill(bx + w - 1, by - k, bx + w, by - k + 1, color);
						context.fill(bx - w, by + k, bx - w + 1, by + k + 1, color);
						context.fill(bx + w - 1, by + k, bx + w, by + k + 1, color);
					} else {
						context.fill(bx - w, by - k, bx + w, by - k + 1, color);
						context.fill(bx - w, by + k, bx + w, by + k + 1, color);
					}
				}
				String label = switch (kind) {
					case CrawlerPayloads.Scan.POI_HOME -> "HOME";
					case CrawlerPayloads.Scan.POI_SITE_TWO -> "S2";
					case CrawlerPayloads.Scan.POI_SHELTER -> "SOS";
					default -> "OK";
				};
				int lx = bx + 5;
				if (lx + client.textRenderer.getWidth(label) > cx + radius) lx = bx - 5 - client.textRenderer.getWidth(label);
				context.drawText(client.textRenderer, label, lx, by - 4, color, false);
			}
		}
		// The hull, and the sweep.
		context.fill(cx - 2, cy - 4, cx + 3, cy + 4, 0xFFA5F3FC);
		double angle = (System.nanoTime() / 1.0e9 * 1.2) % (Math.PI * 2);
		for (int i = 0; i < radius; i++) {
			int px = cx + (int) Math.round(Math.sin(angle) * i);
			int py = cy - (int) Math.round(Math.cos(angle) * i);
			context.fill(px, py, px + 1, py + 1, 0x9086EFAC);
		}
		for (int r = radius / 3; r < radius; r += radius / 3) ringOutline(context, cx, cy, r, 0x3086EFAC);
		ringOutline(context, cx, cy, radius, 0xFF86EFAC);
	}

	/**
	 * The hull's corrosion, beside the charge: a plain bar that fills as the belt eats. Four radio warnings
	 * were the only word on it before, and a driver needs to see what the last trip out cost before the
	 * hull seizes somewhere it cannot be walked back from.
	 */
	private static void drawWear(DrawContext context, int x, int y, int wear) {
		MinecraftClient client = MinecraftClient.getInstance();
		int eaten = Math.max(0, Math.min(100, wear));
		int width = 34;
		int height = 6;
		int top = y + 1;
		int color = eaten >= 75 ? 0xFFFF6060 : eaten >= 40 ? 0xFFFDE047 : 0xFF86EFAC;
		context.fill(x - 1, top - 1, x + width + 1, top + height + 1, 0xCC0A1410);
		context.fill(x, top, x + width, top + height, 0xFF13251C);
		int filled = Math.round(width * eaten / 100f);
		if (filled > 0) context.fill(x, top, x + filled, top + height, color);
		context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.surrogate.crawler.wear", eaten), x + width + 4, y, color & 0xFFFFFF);
	}

	private static int cellColor(int value) {
		if ((value & CrawlerSonar.FLAG_UNKNOWN) != 0) return 0;
		if ((value & CrawlerSonar.FLAG_ENTITY) != 0) return 0xFFFFFFFF;
		if ((value & CrawlerSonar.FLAG_WATER) != 0) return 0xFF2F7FB0;
		int height = (value & CrawlerSonar.HEIGHT_MASK) - CrawlerSonar.HEIGHT_ZERO;
		if (height <= -3) return 0xFF07200F;
		if (height <= 1) return 0xFF145A2A;
		if (height <= 3) return 0xFF3DBF5A;
		return 0xFF9EF7B0;
	}

	private static void fillCircle(DrawContext context, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int w = (int) Math.floor(Math.sqrt(r * r - dy * dy));
			context.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
		}
	}

	private static void ringOutline(DrawContext context, int cx, int cy, int r, int color) {
		int steps = Math.max(24, r * 4);
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			int px = cx + (int) Math.round(Math.cos(a) * r);
			int py = cy + (int) Math.round(Math.sin(a) * r);
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || !CrawlerClientState.aboard()) return;
		int x = 8;
		int y = context.getScaledWindowHeight() - 52;
		if (CrawlerClientState.seat > 0) drawSonar(context, tickCounter);
		if (CrawlerClientState.seat == CrawlerInterior.SEAT_DOCK) {
			int w = Math.min(180, context.getScaledWindowWidth() / 4);
			int h = w * 5 / 8;
			int px = 16;
			int py = context.getScaledWindowHeight() / 2 - h / 2;
			boolean aligned = CrawlerClientState.aligned();
			context.fill(px - 3, py - 3, px + w + 3, py + h + 3, aligned ? 0xCC0F2A18 : 0xCC0A1410);
			PortholeRenderer.drawRearPanel(context, px, py, w, h);
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.surrogate.crawler.rear_camera"), px, py - 14, 0x86EFAC);
		}
		if (CrawlerClientState.lost) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.surrogate.crawler.lost"), x, y, 0xFF6060);
			return;
		}
		float heading = CrawlerClientState.heading;
		String point = POINTS[Math.floorMod(Math.round(heading / 45f), 8)];
		String speed = String.format("%.1f", Math.abs(CrawlerClientState.speed) * 20.0);
		int charge = CrawlerClientState.charge;
		Text dock = Text.translatable(CrawlerClientState.docked ? "hud.surrogate.crawler.docked" : "hud.surrogate.crawler.free");
		Text line = Text.translatable("hud.surrogate.crawler", String.format("%03d", Math.round(heading) % 360), point, speed, charge, dock);
		context.drawTextWithShadow(client.textRenderer, line, x, y, charge <= 10 ? 0xFF6060 : 0xA5F3FC);
		drawWear(context, x + client.textRenderer.getWidth(line) + 8, y, CrawlerClientState.wear);
		context.drawTextWithShadow(client.textRenderer,
				Text.translatable(CrawlerClientState.cladding ? "hud.surrogate.crawler.cladding" : "hud.surrogate.crawler.bare"),
				x, y - 12, CrawlerClientState.cladding ? 0x86EFAC : 0x9CA3AF);
		if (CrawlerClientState.seat == CrawlerInterior.SEAT_HELM) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.surrogate.crawler.helm"), x, y + 12, 0x9CA3AF);
		} else if (CrawlerClientState.seat == CrawlerInterior.SEAT_DOCK) {
			Text status;
			int color;
			if (CrawlerClientState.docked) {
				status = Text.translatable("hud.surrogate.crawler.dock.coupled");
				color = 0x86EFAC;
			} else if (!CrawlerClientState.collar) {
				status = Text.translatable("hud.surrogate.crawler.dock.none");
				color = 0x9CA3AF;
			} else {
				boolean aligned = CrawlerClientState.aligned();
				status = Text.translatable("hud.surrogate.crawler.dock.reading", String.format("%.1f", CrawlerClientState.dockOffset),
						String.format("%.0f", CrawlerClientState.dockAngle), Text.translatable(aligned ? "hud.surrogate.crawler.dock.aligned" : "hud.surrogate.crawler.dock.adjust"));
				color = aligned ? 0x86EFAC : 0xFDE047;
			}
			context.drawTextWithShadow(client.textRenderer, status, x, y + 12, color);
		}
	}
}
