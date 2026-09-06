package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.VehicleFabricatorBlockEntity;
import dev.psyda.surrogate.item.CrawlerKitItem;
import dev.psyda.surrogate.item.RocketKitItem;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The hull taking shape: a cyan hologram of the vehicle on the ground ahead of the gantry, rising out of the
 * pad as the build runs and firming up as it nears the end. Which hull it is comes from the kit on the pad.
 */
@Environment(EnvType.CLIENT)
public class VehicleFabricatorRenderer implements BlockEntityRenderer<VehicleFabricatorBlockEntity> {
	private static final Identifier CRAWLER = Surrogate.id("textures/entity/crawler.png");
	private static final Identifier ROCKET = Surrogate.id("textures/entity/rocket.png");
	private final CrawlerEntityModel crawler;
	private final RocketEntityModel rocket;

	public VehicleFabricatorRenderer(BlockEntityRendererFactory.Context context) {
		this.crawler = new CrawlerEntityModel(context.getLayerModelPart(CrawlerEntityModel.LAYER));
		this.rocket = new RocketEntityModel(context.getLayerModelPart(RocketEntityModel.LAYER));
	}

	/**
	 * Which ghost the gantry draws. The kit on the pad decides: the gantry builds more than one thing, and a
	 * crawler hologram over a rocket build reads as a bug.
	 */
	private record Ghost(net.minecraft.client.model.Model model, Identifier texture) {
	}

	@Nullable
	private Ghost ghostFor(ItemStack kit) {
		if (kit.getItem() instanceof CrawlerKitItem) return new Ghost(crawler, CRAWLER);
		if (kit.getItem() instanceof RocketKitItem) return new Ghost(rocket, ROCKET);
		return null;
	}

	@Override
	public void render(VehicleFabricatorBlockEntity gantry, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (!gantry.isBuilding() || gantry.getWorld() == null) return;
		Ghost ghost = ghostFor(gantry.getKit());
		if (ghost == null) return;
		float progress = MathHelper.clamp((gantry.getProgress() + tickDelta) / gantry.getBuildTicks(), 0f, 1f);
		float time = gantry.getWorld().getTime() + tickDelta;
		Vec3d site = gantry.buildSite();
		BlockPos pos = gantry.getPos();
		matrices.push();
		matrices.translate(site.x - pos.getX(), site.y - pos.getY(), site.z - pos.getZ());
		// The hull rises out of the ground, and shivers a little while it is still being written.
		float rise = MathHelper.clamp(progress * 1.15f, 0.02f, 1f);
		float shiver = progress < 0.95f ? 0.01f * MathHelper.sin(time * 1.7f) : 0f;
		matrices.scale(1f + shiver, rise, 1f - shiver);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - gantry.buildYaw()));
		matrices.scale(-2f, -2f, 2f);
		matrices.translate(0.0, -1.501, 0.0);
		// Translucent cyan that fills in toward the end; a slow pulse keeps it reading as light, not paint.
		float pulse = 0.08f * MathHelper.sin(time * 0.25f);
		float alpha = MathHelper.clamp(0.25f + 0.55f * progress + pulse, 0.15f, 0.9f);
		int a = (int) (alpha * 255f) & 0xFF;
		int colour = (a << 24) | (0x70 << 16) | (0xE8 << 8) | 0xFF;
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(ghost.texture()));
		ghost.model().render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, colour);
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(VehicleFabricatorBlockEntity gantry) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 96;
	}
}
