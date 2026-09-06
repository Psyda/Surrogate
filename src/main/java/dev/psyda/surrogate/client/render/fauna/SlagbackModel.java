package dev.psyda.surrogate.client.render.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.SlagbackEntity;
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
 * A boulder with four plates on its back and four legs underneath, and for most of its life none of those
 * are visible. UV layout must match tools/gen_textures.py.
 *
 * <p>Folded, the plates lie flush and the legs are inside the shell, so what is drawn is a lump of basalt on
 * a basalt floor. Roused, the plates lift and the legs push it up off the ground, over about half a second —
 * {@link SlagbackEntity#openness(float)} is that half second, and everything here is a lerp along it.
 */
@Environment(EnvType.CLIENT)
public class SlagbackModel extends SinglePartEntityModel<SlagbackEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("slagback"), "main");

	/** How far a plate lifts when it is up, in radians. */
	private static final float PLATE_LIFT = 0.9f;
	/** And how far the shell rises off the floor when the legs come out, in model units. */
	private static final float STAND = 3.5f;

	private final ModelPart root;
	private final ModelPart shell;
	private final ModelPart[] plates = new ModelPart[4];
	private final ModelPart[] legs = new ModelPart[4];

	public SlagbackModel(ModelPart root) {
		this.root = root;
		this.shell = root.getChild("shell");
		for (int i = 0; i < 4; i++) {
			plates[i] = shell.getChild("plate" + i);
			legs[i] = shell.getChild("leg" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData shell = root.addChild("shell",
				ModelPartBuilder.create().uv(0, 0).cuboid(-7f, -5f, -6f, 14f, 5f, 12f),
				ModelTransform.pivot(0f, 24f, 0f));
		// Plates hinge on the shell's spine and swing outwards, so each pivot sits on the centre line and
		// the cuboid hangs off to one side of it.
		int[][] plateAt = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
		for (int i = 0; i < 4; i++) {
			shell.addChild("plate" + i,
					ModelPartBuilder.create().uv(0, 20).cuboid(-3f, -1f, -3f, 6f, 1f, 6f),
					ModelTransform.pivot(plateAt[i][0], -5f, plateAt[i][1]));
		}
		int[][] legAt = {{-5, -4}, {5, -4}, {-5, 4}, {5, 4}};
		for (int i = 0; i < 4; i++) {
			shell.addChild("leg" + i,
					ModelPartBuilder.create().uv(28, 20).cuboid(-1f, 0f, -1f, 2f, 4f, 2f),
					ModelTransform.pivot(legAt[i][0], 0f, legAt[i][1]));
		}
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(SlagbackEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		float open = entity.openness(0f);
		shell.pivotY = 24f - STAND * open;
		for (int i = 0; i < 4; i++) {
			// Outer corners lift away from the middle: sign on x for roll, sign on z for pitch.
			float sx = (i == 0 || i == 2) ? -1f : 1f;
			float sz = (i < 2) ? -1f : 1f;
			plates[i].roll = sx * PLATE_LIFT * open;
			plates[i].pitch = sz * PLATE_LIFT * 0.5f * open;
			// Legs are inside the shell until they are not, so they scale out rather than swinging out.
			legs[i].yScale = Math.max(0.01f, open);
			legs[i].pivotY = -0.5f;
			// A walk, but a bad one: it is a rock with legs and it moves like one.
			legs[i].pitch = open * MathHelper.cos(limbAngle * 0.5f + (i < 2 ? 0f : (float) Math.PI)) * 0.5f * limbDistance;
		}
	}
}
