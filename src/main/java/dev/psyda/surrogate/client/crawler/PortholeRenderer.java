package dev.psyda.surrogate.client.crawler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.crawler.CrawlerDocking;
import dev.psyda.surrogate.crawler.CrawlerInteriors;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.network.CrawlerPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The view out of the crawler: the ground around the hull, as the server's camera scan describes it, drawn
 * as a coloured height field from the hull's own eye into a small buffer, then put on the one block monitor
 * over the helm through a barrel-distorted, vignetted grid so it reads as a grainy camera and not a window.
 * The same field seen from the ring on the back is the rear camera the docking console shows; that one
 * narrows as the ring closes on a collar, and the collar itself is drawn as a bright frame to back into.
 */
@Environment(EnvType.CLIENT)
public final class PortholeRenderer {
	/** The monitor is one block square, so its buffer is square: the same detail, crammed in. */
	private static final int FRONT_SIZE = 160;
	private static final int REAR_WIDTH = 192;
	private static final int REAR_HEIGHT = 120;
	private static final float FRONT_FOV = 100f;
	/** The rear camera: wide with nothing near, tight once a collar is in reach. */
	private static final float REAR_FOV_FAR = 70f;
	private static final float REAR_FOV_NEAR = 30f;
	private static final int SKY = 0xFFA0A050;
	private static final float BARREL = 0.22f;
	/** Kinds in the camera's entity list: 1 player, 2 chassis, 3 anything else, 4 a collar. */
	public static final int KIND_COLLAR = 4;

	private static SimpleFramebuffer front;
	private static SimpleFramebuffer rear;

	private PortholeRenderer() {
	}

	/** Called at the start of world rendering: refreshes both camera buffers when the player is aboard. */
	public static void update(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		CrawlerPayloads.Camera camera = CrawlerClientState.camera;
		if (client.player == null || camera == null || !CrawlerDimension.isCabin(client.world)) return;
		if (front == null) {
			front = new SimpleFramebuffer(FRONT_SIZE, FRONT_SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
			rear = new SimpleFramebuffer(REAR_WIDTH, REAR_HEIGHT, true, MinecraftClient.IS_SYSTEM_MAC);
		}
		float yaw = CrawlerClientState.aboard() ? headingToYaw(CrawlerClientState.heading) : camera.yaw();
		render(front, camera, yaw, new Vec3d(0.0, CrawlerEntity.HEIGHT - 0.4, CrawlerEntity.HALF_LENGTH - 0.5), 0f, FRONT_FOV);
		render(rear, camera, yaw + 180f, new Vec3d(0.0, CrawlerEntity.HEIGHT - 0.6, -(CrawlerEntity.HALF_LENGTH + 0.2)), 10f, rearFov());
	}

	/** Zoom the rear camera in as the ring closes on the collar: ten blocks out it is wide, on the collar it is tight. */
	public static float rearFov() {
		if (!CrawlerClientState.collar || CrawlerClientState.docked) return REAR_FOV_FAR;
		float t = MathHelper.clamp(CrawlerClientState.dockOffset / 10f, 0f, 1f);
		return MathHelper.lerp(t, REAR_FOV_NEAR, REAR_FOV_FAR);
	}

	private static float headingToYaw(float heading) {
		return heading - 180f;
	}

	private static void render(SimpleFramebuffer target, CrawlerPayloads.Camera camera, float yaw, Vec3d eye, float pitch, float fov) {
		MinecraftClient client = MinecraftClient.getInstance();
		Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		target.beginWrite(true);
		try {
			RenderSystem.clearColor(((SKY >> 16) & 0xFF) / 255f, ((SKY >> 8) & 0xFF) / 255f, (SKY & 0xFF) / 255f, 1f);
			RenderSystem.clear(16640, MinecraftClient.IS_SYSTEM_MAC);
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(515);
			RenderSystem.depthMask(true);
			RenderSystem.disableBlend();
			// The height field's faces are one-sided; culling them shows the insides of trees and cliffs.
			RenderSystem.disableCull();
			float aspect = (float) target.textureWidth / target.textureHeight;
			Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.2f, 160f);
			RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
			modelView.pushMatrix();
			modelView.identity();
			RenderSystem.applyModelViewMatrix();
			// The eye sits on the hull; the world is drawn relative to the hull's position.
			Vec3d eyeWorld = new Vec3d(eye.x, eye.y, eye.z).rotateY((float) Math.toRadians(-yaw));
			Matrix4f view = new Matrix4f()
					.rotateX((float) Math.toRadians(pitch))
					.rotateY((float) Math.toRadians(yaw + 180f))
					.translate((float) -eyeWorld.x, (float) -eyeWorld.y, (float) -eyeWorld.z);
			RenderSystem.setShaderFogStart(24f);
			RenderSystem.setShaderFogEnd(64f);
			RenderSystem.setShaderFogShape(FogShape.SPHERE);
			RenderSystem.setShaderFogColor(((SKY >> 16) & 0xFF) / 255f, ((SKY >> 8) & 0xFF) / 255f, (SKY & 0xFF) / 255f, 1f);
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			buildGround(builder, view, camera);
			buildEntities(builder, view, camera);
			BuiltBuffer built = builder.endNullable();
			if (built != null) BufferRenderer.drawWithGlobalProgram(built);
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
		} finally {
			RenderSystem.enableCull();
			target.endWrite();
			RenderSystem.setProjectionMatrix(oldProjection, VertexSorter.BY_DISTANCE);
			client.getFramebuffer().beginWrite(true);
			RenderSystem.enableBlend();
		}
	}

	/** One quad per cell, coloured like the map item and shaded by the slope to the north. */
	private static void buildGround(BufferBuilder builder, Matrix4f view, CrawlerPayloads.Camera camera) {
		int size = camera.size();
		byte[] heights = camera.heights();
		byte[] colors = camera.colors();
		double hx = camera.hullX();
		double hy = camera.hullY();
		double hz = camera.hullZ();
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int index = z * size + x;
				byte h = heights[index];
				if (h == Byte.MIN_VALUE) continue;
				int rgb = MapColor.get(colors[index] & 0xFF).color;
				if (rgb == 0) rgb = 0x6B7280;
				float shade = 1f;
				if (z > 0 && heights[(z - 1) * size + x] != Byte.MIN_VALUE) {
					int north = heights[(z - 1) * size + x];
					shade = north > h ? 0.72f : north < h ? 1.12f : 0.9f;
				}
				int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * shade));
				int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * shade));
				int b = Math.min(255, Math.round((rgb & 0xFF) * shade));
				float x0 = (float) (camera.originX() + x - hx);
				float z0 = (float) (camera.originZ() + z - hz);
				float y = (float) (MathHelper.floor(hy) + h - hy);
				quad(builder, view, x0, y, z0, x0 + 1, y, z0 + 1, r, g, b);
				// A wall face where the ground steps down to the south or east neighbour, so cliffs read as cliffs.
				if (x + 1 < size && heights[index + 1] != Byte.MIN_VALUE && heights[index + 1] < h) {
					float y2 = (float) (MathHelper.floor(hy) + heights[index + 1] - hy);
					wall(builder, view, x0 + 1, y2, z0, x0 + 1, y, z0 + 1, r, g, b, 0.6f);
				}
				if (z + 1 < size && heights[index + size] != Byte.MIN_VALUE && heights[index + size] < h) {
					float y2 = (float) (MathHelper.floor(hy) + heights[index + size] - hy);
					wall(builder, view, x0, y2, z0 + 1, x0 + 1, y, z0 + 1, r, g, b, 0.5f);
				}
			}
		}
	}

	private static void buildEntities(BufferBuilder builder, Matrix4f view, CrawlerPayloads.Camera camera) {
		float[] entities = camera.entities();
		for (int i = 0; i + 3 < entities.length; i += 4) {
			float ex = entities[i];
			float ey = entities[i + 1];
			float ez = entities[i + 2];
			int kind = Math.round(entities[i + 3]);
			if (kind == KIND_COLLAR) {
				collarFrame(builder, view, ex, ey, ez);
				continue;
			}
			int r = kind == 1 ? 240 : kind == 2 ? 230 : 200;
			int g = kind == 1 ? 240 : kind == 2 ? 150 : 200;
			int b = kind == 1 ? 240 : kind == 2 ? 40 : 200;
			float s = 0.4f;
			float h = kind == 2 ? 1f : 1.8f;
			box(builder, view, ex - s, ey, ez - s, ex + s, ey + h, ez + s, r, g, b);
		}
	}

	/**
	 * A collar as the rear camera sees it: a bright square frame the size of the ring, standing in the
	 * door's face, so the pilot has something to centre in the reticle. The target point is the middle of
	 * the door's outer face at ring height; every collar faces west, so the frame lies across x.
	 */
	private static void collarFrame(BufferBuilder builder, Matrix4f view, float x, float y, float z) {
		float half = 0.9f;
		float bar = 0.12f;
		float depth = 0.08f;
		int r = 253;
		int g = 224;
		int b = 71;
		box(builder, view, x - depth, y + half - bar, z - half, x + depth, y + half, z + half, r, g, b);
		box(builder, view, x - depth, y - half, z - half, x + depth, y - half + bar, z + half, r, g, b);
		box(builder, view, x - depth, y - half, z - half, x + depth, y + half, z - half + bar, r, g, b);
		box(builder, view, x - depth, y - half, z + half - bar, x + depth, y + half, z + half, r, g, b);
	}

	private static void quad(BufferBuilder builder, Matrix4f m, float x0, float y, float z0, float x1, float y1, float z1, int r, int g, int b) {
		builder.vertex(m, x0, y, z0).color(r, g, b, 255);
		builder.vertex(m, x0, y, z1).color(r, g, b, 255);
		builder.vertex(m, x1, y, z1).color(r, g, b, 255);
		builder.vertex(m, x1, y, z0).color(r, g, b, 255);
	}

	private static void wall(BufferBuilder builder, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b, float shade) {
		int cr = Math.round(r * shade);
		int cg = Math.round(g * shade);
		int cb = Math.round(b * shade);
		builder.vertex(m, x0, y0, z0).color(cr, cg, cb, 255);
		builder.vertex(m, x0, y1, z0).color(cr, cg, cb, 255);
		builder.vertex(m, x1, y1, z1).color(cr, cg, cb, 255);
		builder.vertex(m, x1, y0, z1).color(cr, cg, cb, 255);
		builder.vertex(m, x1, y0, z1).color(cr, cg, cb, 255);
		builder.vertex(m, x1, y1, z1).color(cr, cg, cb, 255);
		builder.vertex(m, x0, y1, z0).color(cr, cg, cb, 255);
		builder.vertex(m, x0, y0, z0).color(cr, cg, cb, 255);
	}

	private static void box(BufferBuilder builder, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b) {
		wall(builder, m, x0, y0, z0, x1, y1, z0, r, g, b, 1f);
		wall(builder, m, x0, y0, z1, x1, y1, z1, r, g, b, 0.8f);
		wall(builder, m, x0, y0, z0, x0, y1, z1, r, g, b, 0.9f);
		wall(builder, m, x1, y0, z0, x1, y1, z1, r, g, b, 0.7f);
		quad(builder, m, x0, y1, z0, x1, y1, z1, r, g, b);
	}

	// ------------------------------------------------------------------ putting it on the glass

	/** Called after translucent blocks: paints the front camera on the monitor of the cabin the player is in. */
	public static void drawPorthole(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || front == null || CrawlerClientState.camera == null || !CrawlerDimension.isCabin(client.world)) return;
		BlockPos origin = CrawlerInteriors.origin(CrawlerInteriors.indexAt(client.player.getBlockPos()));
		Vec3d cam = context.camera().getPos();
		// The monitor is the one glass block over the helm, in the wall at z = -5; its room-side face is at z = origin.z - 4.
		float x0 = (float) (origin.getX() - cam.x);
		float x1 = (float) (origin.getX() + 1 - cam.x);
		float y0 = (float) (origin.getY() + 2 - cam.y);
		float y1 = (float) (origin.getY() + 3 - cam.y);
		float z = (float) (origin.getZ() - 4 + 0.015 - cam.z);
		Matrix4f m = context.matrixStack().peek().getPositionMatrix();
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, front.getColorAttachment());
		// Behind walls it must lose: full depth test and writes, whatever the translucent pass left behind.
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(515);
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		int grid = 12;
		float flicker = 0.92f + 0.06f * (float) Math.sin(System.nanoTime() / 1.0e8);
		java.util.Random grain = new java.util.Random(System.nanoTime() / 40_000_000L);
		// A rolling scan band: a slightly darker stripe that drifts down the glass.
		float band = (float) ((System.nanoTime() / 1.0e9 * 0.35) % 1.0);
		for (int gy = 0; gy < grid; gy++) {
			for (int gx = 0; gx < grid; gx++) {
				float[] a = corner(gx, gy, grid);
				float[] b = corner(gx + 1, gy, grid);
				float[] c = corner(gx + 1, gy + 1, grid);
				float[] d = corner(gx, gy + 1, grid);
				float noise = 0.86f + 0.14f * grain.nextFloat();
				float row = (gy + 0.5f) / grid;
				if (Math.abs(row - band) < 0.06f) noise *= 0.8f;
				float cellShade = flicker * noise;
				emit(builder, m, x0, x1, y0, y1, z, a, cellShade);
				emit(builder, m, x0, x1, y0, y1, z, d, cellShade);
				emit(builder, m, x0, x1, y0, y1, z, c, cellShade);
				emit(builder, m, x0, x1, y0, y1, z, b, cellShade);
			}
		}
		BuiltBuffer built = builder.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.enableCull();
		RenderSystem.enableBlend();
	}

	/** A grid corner: its place on the glass (0..1), its barrel-distorted texture coordinate, and its vignette. */
	private static float[] corner(int gx, int gy, int grid) {
		float px = (float) gx / grid;
		float py = (float) gy / grid;
		float u = px * 2f - 1f;
		float v = py * 2f - 1f;
		float r2 = u * u + v * v;
		float k = 1f + BARREL * r2;
		float tu = MathHelper.clamp((u * k + 1f) / 2f, 0f, 1f);
		float tv = MathHelper.clamp((v * k + 1f) / 2f, 0f, 1f);
		float vignette = MathHelper.clamp(1.15f - 0.55f * r2, 0.35f, 1f);
		return new float[]{px, py, tu, tv, vignette};
	}

	private static void emit(BufferBuilder builder, Matrix4f m, float x0, float x1, float y0, float y1, float z, float[] c, float flicker) {
		float x = MathHelper.lerp(c[0], x0, x1);
		float y = MathHelper.lerp(c[1], y0, y1);
		int shade = Math.round(255 * c[4] * flicker);
		builder.vertex(m, x, y, z).texture(c[2], c[3]).color(shade, shade, shade, 255);
	}

	/**
	 * The rear camera as a panel on the HUD, for the docking console, with the docking reticle over it: the
	 * ring's own outline in the middle of the picture, yellow while the collar is off, green once the ring is
	 * inside the coupling window.
	 */
	public static void drawRearPanel(DrawContext context, int x, int y, int w, int h) {
		if (rear == null || CrawlerClientState.camera == null) return;
		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, rear.getColorAttachment());
		RenderSystem.enableBlend();
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		int grid = 8;
		for (int gy = 0; gy < grid; gy++) {
			for (int gx = 0; gx < grid; gx++) {
				float[][] corners = {corner(gx, gy, grid), corner(gx, gy + 1, grid), corner(gx + 1, gy + 1, grid), corner(gx + 1, gy, grid)};
				for (float[] c : corners) {
					// Framebuffers are bottom-up; the panel is top-down.
					int shade = Math.round(255 * c[4]);
					builder.vertex(m, x + c[0] * w, y + c[1] * h, 0f).texture(c[2], 1f - c[3]).color(shade, shade, shade, 255);
				}
			}
		}
		BuiltBuffer built = builder.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		if (!CrawlerClientState.collar || CrawlerClientState.docked) return;
		boolean aligned = CrawlerClientState.dockOffset <= CrawlerDocking.COUPLE_OFFSET && CrawlerClientState.dockAngle <= CrawlerDocking.COUPLE_ANGLE;
		int color = aligned ? 0xFF86EFAC : 0xFFFDE047;
		int cx = x + w / 2;
		int cy = y + h / 2;
		// The reticle: the ring's outline, at the size the collar frame has once the ring is on it.
		int r = Math.max(6, h / 5);
		context.fill(cx - r, cy - r, cx + r, cy - r + 1, color);
		context.fill(cx - r, cy + r - 1, cx + r, cy + r, color);
		context.fill(cx - r, cy - r, cx - r + 1, cy + r, color);
		context.fill(cx + r - 1, cy - r, cx + r, cy + r, color);
		context.fill(cx - r - 4, cy, cx - r, cy + 1, color);
		context.fill(cx + r, cy, cx + r + 4, cy + 1, color);
		context.fill(cx, cy - r - 4, cx + 1, cy - r, color);
		context.fill(cx, cy + r, cx + 1, cy + r + 4, color);
	}
}
