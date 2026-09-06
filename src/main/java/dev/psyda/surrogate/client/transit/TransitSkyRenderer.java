package dev.psyda.surrogate.client.transit;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.transit.TransitDimension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What is outside the windows: a field of stars with a band through it, a sun, the main engine's glow
 * astern, and Sallow, which starts the week as a point of light ahead and ends it filling the aft glass.
 * Turnover swings the whole sky through 180 degrees; the planet is shaded from the sun so its terminator
 * sits where it should.
 *
 * <p>Everything sits on a sphere of radius 100 around the camera, drawn without depth like the vanilla
 * sky, before the ship. Every mesh is baked into a vertex buffer once (the planet again whenever it has
 * grown a little) so a frame is a handful of draw calls and no geometry work.
 */
@Environment(EnvType.CLIENT)
public final class TransitSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
	private static final Identifier PLANET = Surrogate.id("textures/sky/sallow.png");
	private static final float RADIUS = 100f;
	/** Where Sallow is, before turnover: dead ahead. */
	private static final Vector3f PLANET_DIR = new Vector3f(0f, 0f, -1f);
	/** The sun: aft and to starboard, well above the plane of the ship. */
	private static final Vector3f SUN_DIR = direction(135f, 25f);
	/** The main engine, in the ship's own frame: astern and a little below. */
	private static final Vector3f ENGINE_DIR = new Vector3f(0f, -0.35f, 1f).normalize();
	private static final boolean DEBUG = Boolean.getBoolean("surrogate.devSkyDebug");
	private static final int SEGMENTS = 40;
	private static final int RINGS = 10;

	private VertexBuffer stars;
	private VertexBuffer sunGlow;
	private VertexBuffer sunCore;
	private VertexBuffer engineGlow;
	private VertexBuffer engineCore;
	private VertexBuffer planet;
	private VertexBuffer rim;
	private float bakedRadius = -1f;
	private boolean announced;
	private long lastLogged = -1L;

	private static Vector3f direction(float azimuthDeg, float elevationDeg) {
		float a = (float) Math.toRadians(azimuthDeg);
		float e = (float) Math.toRadians(elevationDeg);
		return new Vector3f(MathHelper.sin(a) * MathHelper.cos(e), MathHelper.sin(e), -MathHelper.cos(a) * MathHelper.cos(e));
	}

	@Override
	public void render(WorldRenderContext context) {
		if (context.world().getRegistryKey() != TransitDimension.WORLD) return;
		if (!announced) {
			announced = true;
			Surrogate.LOGGER.info("Transit sky: rendering (yaw {}, planet radius {} deg)", TransitClientState.attitudeYaw(0f), planetRadiusDeg());
		}
		float delta = context.tickCounter().getTickDelta(false);
		Matrix4f projection = context.projectionMatrix();
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(context.positionMatrix());

		if (stars == null) bakeStatic();
		float radiusDeg = planetRadiusDeg();
		if (planet == null || Math.abs(radiusDeg - bakedRadius) > 0.15f) bakePlanet(radiusDeg);

		BackgroundRenderer.clearFog();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		// The inertial sky, turned by however far the ship has swung round.
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(TransitClientState.attitudeYaw(delta)));
		Matrix4f sky = matrices.peek().getPositionMatrix();
		additive();
		draw(stars, sky, projection, GameRenderer.getPositionColorProgram());
		draw(sunGlow, sky, projection, GameRenderer.getPositionColorProgram());
		draw(sunCore, sky, projection, GameRenderer.getPositionColorProgram());
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, PLANET);
		draw(planet, sky, projection, GameRenderer.getPositionTexColorProgram());
		additive();
		draw(rim, sky, projection, GameRenderer.getPositionColorProgram());
		if (DEBUG) {
			glow(sky, PLANET_DIR, 16f, 1.0f, 0.0f, 1.0f, 0.9f);
			glow(sky, SUN_DIR, 16f, 0.0f, 1.0f, 0.0f, 0.9f);
			long time = context.world().getTime();
			if (time % 100 == 0 && time != lastLogged) {
				lastLogged = time;
				Surrogate.LOGGER.info("Transit sky: yaw {} engine {} day {} hours {}", TransitClientState.attitudeYaw(delta), TransitClientState.engine(), TransitClientState.day(), TransitClientState.hours());
			}
		}
		matrices.pop();

		// The engine sits on the ship, so it does not turn with the stars.
		if (TransitClientState.engine()) {
			Matrix4f ship = matrices.peek().getPositionMatrix();
			draw(engineGlow, ship, projection, GameRenderer.getPositionColorProgram());
			draw(engineCore, ship, projection, GameRenderer.getPositionColorProgram());
		}

		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void additive() {
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
	}

	private static void draw(VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderProgram program) {
		buffer.bind();
		buffer.draw(view, projection, program);
		VertexBuffer.unbind();
	}

	private static VertexBuffer upload(BufferBuilder builder) {
		VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
		buffer.bind();
		buffer.upload(builder.end());
		VertexBuffer.unbind();
		return buffer;
	}

	// ------------------------------------------------------------------ baking

	private void bakeStatic() {
		stars = upload(buildStars());
		sunGlow = upload(buildGlow(SUN_DIR, 11f, 1.0f, 0.93f, 0.78f, 0.55f));
		sunCore = upload(buildGlow(SUN_DIR, 2.2f, 1.0f, 1.0f, 0.97f, 1.0f));
		engineGlow = upload(buildGlow(ENGINE_DIR, 24f, 0.45f, 0.72f, 1.0f, 0.3f));
		engineCore = upload(buildGlow(ENGINE_DIR, 7f, 0.75f, 0.9f, 1.0f, 0.55f));
	}

	private void bakePlanet(float radiusDeg) {
		if (planet != null) planet.close();
		if (rim != null) rim.close();
		planet = upload(buildPlanet(radiusDeg));
		rim = upload(buildRim(radiusDeg));
		bakedRadius = radiusDeg;
	}

	private static BufferBuilder buildStars() {
		Random random = Random.create(2207L);
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		Vector3f bandNormal = new Vector3f(0.42f, 0.8f, 0.43f).normalize();
		int count = 2600;
		for (int i = 0; i < count; i++) {
			float x = random.nextFloat() * 2f - 1f;
			float y = random.nextFloat() * 2f - 1f;
			float z = random.nextFloat() * 2f - 1f;
			float length = MathHelper.magnitude(x, y, z);
			if (length <= 0.01f || length >= 1f) {
				i--;
				continue;
			}
			Vector3f dir = new Vector3f(x, y, z).normalize();
			// A third of the stars crowd into a band across the sky.
			if (i % 3 == 0) {
				float off = dir.dot(bandNormal);
				dir.sub(new Vector3f(bandNormal).mul(off * 0.85f)).normalize();
			}
			float size = 0.11f + random.nextFloat() * 0.17f;
			float brightness = 0.35f + random.nextFloat() * 0.65f;
			float warm = random.nextFloat();
			float r = brightness * (warm > 0.8f ? 1.0f : 0.85f + 0.15f * warm);
			float g = brightness * (0.88f + 0.12f * warm);
			float b = brightness * (warm > 0.8f ? 0.8f : 1.0f);
			Vector3f center = new Vector3f(dir).mul(RADIUS);
			Vector3f up = Math.abs(dir.y) > 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
			Vector3f u = new Vector3f(dir).cross(up).normalize().mul(size);
			Vector3f v = new Vector3f(u).cross(dir).normalize().mul(size);
			int color = color(r, g, b, 1f);
			builder.vertex(center.x - u.x - v.x, center.y - u.y - v.y, center.z - u.z - v.z).color(color);
			builder.vertex(center.x + u.x - v.x, center.y + u.y - v.y, center.z + u.z - v.z).color(color);
			builder.vertex(center.x + u.x + v.x, center.y + u.y + v.y, center.z + u.z + v.z).color(color);
			builder.vertex(center.x - u.x + v.x, center.y - u.y + v.y, center.z - u.z + v.z).color(color);
		}
		return builder;
	}

	/** A soft disc of light, bright in the middle and gone at the edge, as a fan of quads. */
	private static BufferBuilder buildGlow(Vector3f dir, float radiusDeg, float r, float g, float b, float alpha) {
		float radius = RADIUS * (float) Math.tan(Math.toRadians(radiusDeg));
		Vector3f center = new Vector3f(dir).mul(RADIUS);
		Vector3f up = Math.abs(dir.y) > 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
		Vector3f u = new Vector3f(dir).cross(up).normalize();
		Vector3f v = new Vector3f(u).cross(dir).normalize();
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < 32; i++) {
			float a0 = (float) (i * Math.PI * 2.0 / 32);
			float a1 = (float) ((i + 1) * Math.PI * 2.0 / 32);
			builder.vertex(center.x, center.y, center.z).color(r, g, b, alpha);
			rimVertex(builder, center, u, v, radius, a0).color(r, g, b, 0f);
			rimVertex(builder, center, u, v, radius, a1).color(r, g, b, 0f);
		}
		return builder;
	}

	private static BufferBuilder rimVertex(BufferBuilder builder, Vector3f center, Vector3f u, Vector3f v, float radius, float a) {
		float ca = MathHelper.cos(a);
		float sa = MathHelper.sin(a);
		builder.vertex(center.x + (u.x * ca + v.x * sa) * radius, center.y + (u.y * ca + v.y * sa) * radius, center.z + (u.z * ca + v.z * sa) * radius);
		return builder;
	}

	/** Angular radius of Sallow in degrees: under a degree on the first morning, most of the window by the end. */
	private static float planetRadiusDeg() {
		if (TransitClientState.descending()) return 78f;
		float f = TransitClientState.weekFraction();
		return Math.min(72f, 0.9f * (float) Math.pow(2.0, f * 6.2));
	}

	/**
	 * A disc mesh with the sun baked into the vertex colours: each vertex is shaded by the sphere normal it
	 * would have, so the terminator curves the way a real one does and the night side goes dark.
	 */
	private static BufferBuilder buildPlanet(float radiusDeg) {
		float radius = RADIUS * (float) Math.tan(Math.toRadians(radiusDeg));
		Vector3f center = new Vector3f(PLANET_DIR).mul(RADIUS);
		Vector3f u = new Vector3f(1f, 0f, 0f);
		Vector3f v = new Vector3f(0f, 1f, 0f);
		// The sun in the disc's own frame: across, up, and toward the viewer.
		Vector3f toViewer = new Vector3f(PLANET_DIR).negate();
		float su = SUN_DIR.dot(u);
		float sv = SUN_DIR.dot(v);
		float sw = SUN_DIR.dot(toViewer);
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		for (int ring = 0; ring < RINGS; ring++) {
			float r0 = ring / (float) RINGS;
			float r1 = (ring + 1) / (float) RINGS;
			for (int i = 0; i < SEGMENTS; i++) {
				float a0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
				float a1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
				// Inner, outer, outer, inner: counter-clockwise from inside the sphere.
				discVertex(builder, center, u, v, radius, r0 * MathHelper.cos(a0), r0 * MathHelper.sin(a0), su, sv, sw);
				discVertex(builder, center, u, v, radius, r1 * MathHelper.cos(a0), r1 * MathHelper.sin(a0), su, sv, sw);
				discVertex(builder, center, u, v, radius, r1 * MathHelper.cos(a1), r1 * MathHelper.sin(a1), su, sv, sw);
				discVertex(builder, center, u, v, radius, r0 * MathHelper.cos(a1), r0 * MathHelper.sin(a1), su, sv, sw);
			}
		}
		return builder;
	}

	private static void discVertex(BufferBuilder builder, Vector3f center, Vector3f u, Vector3f v, float radius,
								   float px, float py, float su, float sv, float sw) {
		float rho2 = Math.min(1f, px * px + py * py);
		float nz = (float) Math.sqrt(1f - rho2);
		float lit = MathHelper.clamp(px * su + py * sv + nz * sw, 0f, 1f);
		// A touch of ambient so the night side reads as a disc against the stars, and limb darkening.
		float shade = 0.035f + 0.965f * (float) Math.pow(lit, 0.8) * (0.72f + 0.28f * nz);
		float x = center.x + (u.x * px + v.x * py) * radius;
		float y = center.y + (u.y * px + v.y * py) * radius;
		float z = center.z + (u.z * px + v.z * py) * radius;
		builder.vertex(x, y, z).texture(0.5f + 0.5f * px, 0.5f - 0.5f * py).color(shade, shade, shade, 1f);
	}

	/** The atmosphere: a rim of light around the disc that is brightest where the sun is. */
	private static BufferBuilder buildRim(float radiusDeg) {
		float radius = RADIUS * (float) Math.tan(Math.toRadians(radiusDeg));
		Vector3f center = new Vector3f(PLANET_DIR).mul(RADIUS);
		Vector3f u = new Vector3f(1f, 0f, 0f);
		Vector3f v = new Vector3f(0f, 1f, 0f);
		float su = SUN_DIR.dot(u);
		float sv = SUN_DIR.dot(v);
		float inner = 0.965f;
		float outer = 1.13f;
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
			float a1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
			float lit0 = MathHelper.clamp(0.25f + 0.75f * (su * MathHelper.cos(a0) + sv * MathHelper.sin(a0)), 0.05f, 1f);
			float lit1 = MathHelper.clamp(0.25f + 0.75f * (su * MathHelper.cos(a1) + sv * MathHelper.sin(a1)), 0.05f, 1f);
			rimVertex(builder, center, u, v, radius * inner, a0).color(0.95f, 0.75f, 0.35f, 0.42f * lit0);
			rimVertex(builder, center, u, v, radius * outer, a0).color(0.95f, 0.72f, 0.3f, 0f);
			rimVertex(builder, center, u, v, radius * outer, a1).color(0.95f, 0.72f, 0.3f, 0f);
			rimVertex(builder, center, u, v, radius * inner, a1).color(0.95f, 0.75f, 0.35f, 0.42f * lit1);
		}
		return builder;
	}

	// ------------------------------------------------------------------ debug markers, drawn immediately

	private static void glow(Matrix4f matrix, Vector3f dir, float radiusDeg, float r, float g, float b, float alpha) {
		float radius = RADIUS * (float) Math.tan(Math.toRadians(radiusDeg));
		Vector3f center = new Vector3f(dir).mul(RADIUS);
		Vector3f up = Math.abs(dir.y) > 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
		Vector3f u = new Vector3f(dir).cross(up).normalize();
		Vector3f v = new Vector3f(u).cross(dir).normalize();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		builder.vertex(matrix, center.x, center.y, center.z).color(r, g, b, alpha);
		for (int i = 0; i <= 32; i++) {
			float a = (float) (i * Math.PI * 2.0 / 32);
			float cx = center.x + (u.x * MathHelper.cos(a) + v.x * MathHelper.sin(a)) * radius;
			float cy = center.y + (u.y * MathHelper.cos(a) + v.y * MathHelper.sin(a)) * radius;
			float cz = center.z + (u.z * MathHelper.cos(a) + v.z * MathHelper.sin(a)) * radius;
			builder.vertex(matrix, cx, cy, cz).color(r, g, b, 0f);
		}
		BufferRenderer.drawWithGlobalProgram(builder.end());
	}

	private static int color(float r, float g, float b, float a) {
		int ri = MathHelper.clamp(Math.round(r * 255f), 0, 255);
		int gi = MathHelper.clamp(Math.round(g * 255f), 0, 255);
		int bi = MathHelper.clamp(Math.round(b * 255f), 0, 255);
		int ai = MathHelper.clamp(Math.round(a * 255f), 0, 255);
		return (ai << 24) | (ri << 16) | (gi << 8) | bi;
	}
}
