package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.BorerEntity;
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
 * Two boxes: a head and one body ring. The renderer draws the ring six times down the borer's own position
 * history, tapering as it goes, so the whole animal is two cuboids of geometry. Both are centred on their
 * own origin rather than standing on the ground, because nothing here stands on anything. Front is -z, and
 * the UV layout must match paint_borer in tools/gen_textures.py.
 */
@Environment(EnvType.CLIENT)
public class BorerEntityModel extends SinglePartEntityModel<BorerEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("borer"), "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart segment;

	public BorerEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.segment = root.getChild("segment");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-8f, -8f, -12f, 16f, 16f, 24f), ModelTransform.NONE);
		root.addChild("segment", ModelPartBuilder.create().uv(0, 48).cuboid(-7f, -7f, -6f, 14f, 14f, 12f), ModelTransform.NONE);
		return TexturedModelData.of(data, 128, 128);
	}

	public ModelPart head() {
		return this.head;
	}

	public ModelPart segment() {
		return this.segment;
	}

	@Override
	public void setAngles(BorerEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}
}
