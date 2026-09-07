package dev.psyda.surrogate.client.render.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.TockerEntity;
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
import net.minecraft.util.math.MathHelper;

/**
 * Mostly eye, on three legs. UV layout must match tools/gen_textures.py.
 *
 * <p>The three legs are a deliberate nuisance: a tripod cannot do a walk cycle in pairs, so it hops, and the
 * hop is what makes it read as pleased to see you. Perched, the legs fold under and the body settles onto
 * whatever warm thing it has found.
 */
@Environment(EnvType.CLIENT)
public class TockerModel extends SinglePartEntityModel<TockerEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("tocker"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart eye;
	private final ModelPart[] legs = new ModelPart[3];

	public TockerModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.eye = body.getChild("eye");
		for (int i = 0; i < 3; i++) legs[i] = root.getChild("leg" + i);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-2.5f, -5f, -2.5f, 5f, 5f, 5f),
				ModelTransform.pivot(0f, 20f, 0f));
		// A flat lens on the front rather than a socket in the body: it is the part the eyes layer lights up,
		// and it wants to be one clean rectangle on the sheet.
		body.addChild("eye",
				ModelPartBuilder.create().uv(0, 12).cuboid(-2f, -4f, -3.2f, 4f, 4f, 1f),
				ModelTransform.NONE);
		float[] around = {0f, 2.094f, 4.189f};
		for (int i = 0; i < 3; i++) {
			float x = MathHelper.sin(around[i]) * 2.2f;
			float z = MathHelper.cos(around[i]) * 2.2f;
			root.addChild("leg" + i,
					ModelPartBuilder.create().uv(20 + i * 4, 0).cuboid(-0.5f, 0f, -0.5f, 1f, 4f, 1f),
					ModelTransform.pivot(x, 20f, z));
		}
		return TexturedModelData.of(data, 32, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(TockerEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		if (entity.isPerched()) {
			body.pivotY = 23f;
			for (int i = 0; i < 3; i++) {
				legs[i].pivotY = 23f;
				legs[i].yScale = 0.25f;
				legs[i].pitch = 0f;
			}
			eye.pitch = 0f;
			eye.yaw = 0f;
			return;
		}
		// The hop: one bounce on the body against three legs a third of a cycle apart each.
		float hop = MathHelper.abs(MathHelper.sin(limbAngle * 0.6f)) * limbDistance;
		body.pivotY = 20f - hop * 1.5f;
		for (int i = 0; i < 3; i++) {
			legs[i].pivotY = 20f;
			legs[i].yScale = 1f;
			legs[i].pitch = MathHelper.cos(limbAngle * 0.6f + i * 2.094f) * 0.7f * limbDistance;
		}
		eye.yaw = headYaw * ((float) Math.PI / 180f) * 0.5f;
		eye.pitch = headPitch * ((float) Math.PI / 180f) * 0.5f;
	}
}
