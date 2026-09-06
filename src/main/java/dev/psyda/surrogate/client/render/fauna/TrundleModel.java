package dev.psyda.surrogate.client.render.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.TrundleEntity;
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
 * A ball with a tuft on it. UV layout must match tools/gen_textures.py.
 *
 * <p>The whole animation is one number: {@link TrundleEntity#rolled()}, the ground it has covered, turned
 * into a pitch on the body. That is why it is a ball — a shape that can be spun about its own axis without
 * anybody having to believe in legs. Asleep, it pulls the tuft in and settles a little into the dirt.
 */
@Environment(EnvType.CLIENT)
public class TrundleModel extends SinglePartEntityModel<TrundleEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("trundle"), "main");

	/** Blocks of travel per full turn of the ball. Its own circumference, near enough, so it does not skid. */
	private static final float ROLL = (float) (Math.PI * 0.625);

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart tuft;

	public TrundleModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.tuft = this.body.getChild("tuft");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-5f, -5f, -5f, 10f, 10f, 10f),
				ModelTransform.pivot(0f, 19f, 0f));
		body.addChild("tuft",
				ModelPartBuilder.create().uv(40, 0).cuboid(-2f, -2f, -2f, 4f, 2f, 4f),
				ModelTransform.pivot(0f, -5f, 0f));
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(TrundleEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		if (entity.isDozing()) {
			// Tucked: the tuft folds flat and the ball sinks, so a sleeping one reads as a stone from any
			// distance at which you could not already tell.
			body.pitch = 0f;
			body.pivotY = 20.5f;
			tuft.pivotY = -4f;
			tuft.yScale = 0.4f;
			return;
		}
		body.pivotY = 19f;
		tuft.pivotY = -5f;
		tuft.yScale = 1f;
		// Rolled distance straight onto pitch. No modulo: ModelPart takes radians and MathHelper does not
		// care how many turns are in the number.
		body.pitch = (float) (entity.rolled() / ROLL);
		// The tuft counter-rotates, so it stays roughly upright while the ball turns under it. It does not
		// quite keep up, which is the joke.
		tuft.pitch = -body.pitch * 0.85f + MathHelper.sin(animationProgress * 0.2f) * 0.1f;
	}
}
