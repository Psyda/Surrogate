package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CrawlerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

/**
 * The hull: a cab and a two part deck over a lower body between four tread sections, with the docking ring
 * on the back. Built at half scale (one unit is an eighth of a block) and drawn at twice size, so an eight
 * block hull fits a 256 wide sheet. Front is -z. UV layout must match paint_crawler in tools/gen_textures.py.
 */
@Environment(EnvType.CLIENT)
public class CrawlerEntityModel extends SinglePartEntityModel<CrawlerEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("crawler"), "main");

	private final ModelPart root;

	public CrawlerEntityModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		// Upper hull: full width over the treads. Top of the hull is y = 0, the ground y = 24.
		root.addChild("cab", ModelPartBuilder.create().uv(0, 0).cuboid(-20f, 0f, -32f, 40f, 12f, 24f), ModelTransform.NONE);
		root.addChild("deck_fore", ModelPartBuilder.create().uv(128, 0).cuboid(-20f, 0f, -8f, 40f, 12f, 16f), ModelTransform.NONE);
		root.addChild("deck_aft", ModelPartBuilder.create().uv(128, 28).cuboid(-20f, 0f, 8f, 40f, 12f, 16f), ModelTransform.NONE);
		// Lower body between the treads.
		root.addChild("body_fore", ModelPartBuilder.create().uv(0, 40).cuboid(-12f, 12f, -28f, 24f, 12f, 28f), ModelTransform.NONE);
		root.addChild("body_aft", ModelPartBuilder.create().uv(104, 64).cuboid(-12f, 12f, 0f, 24f, 12f, 28f), ModelTransform.NONE);
		// Treads: two sections a side.
		root.addChild("tread_left_fore", ModelPartBuilder.create().uv(0, 104).cuboid(-20f, 12f, -32f, 8f, 12f, 32f), ModelTransform.NONE);
		root.addChild("tread_left_aft", ModelPartBuilder.create().uv(80, 104).cuboid(-20f, 12f, 0f, 8f, 12f, 32f), ModelTransform.NONE);
		root.addChild("tread_right_fore", ModelPartBuilder.create().uv(160, 104).cuboid(12f, 12f, -32f, 8f, 12f, 32f), ModelTransform.NONE);
		root.addChild("tread_right_aft", ModelPartBuilder.create().uv(0, 148).cuboid(12f, 12f, 0f, 8f, 12f, 32f), ModelTransform.NONE);
		// The docking ring on the back, at collar height.
		root.addChild("ring", ModelPartBuilder.create().uv(208, 64).cuboid(-10f, 2f, 32f, 20f, 18f, 4f), ModelTransform.NONE);
		return TexturedModelData.of(data, 256, 192);
	}

	@Override
	public void setAngles(CrawlerEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	@Override
	public ModelPart getPart() {
		return root;
	}
}
