package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CompanyShipEntity;
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
 * The company ship. Built in model units and then scaled up hard by the renderer, so the numbers here are a
 * silhouette rather than a size: a long spine, a deep belly, a slab of engines at the back and a cargo throat
 * underneath where the payload goes in. Authored small on purpose - a hull authored at its real size needs a
 * texture sheet several times bigger than any of the mod's others.
 *
 * <p>Nose is -z, the same convention as the crawler. The UV layout must match {@code paint_company_ship} in
 * {@code tools/gen_textures.py}.
 */
@Environment(EnvType.CLIENT)
public class CompanyShipModel extends SinglePartEntityModel<CompanyShipEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("company_ship"), "main");

	private final ModelPart root;

	public CompanyShipModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// The spine, running nose to stern. Everything else hangs off it.
		root.addChild("spine", ModelPartBuilder.create().uv(0, 0).cuboid(-6f, -3f, -18f, 12f, 6f, 36f), ModelTransform.NONE);
		// The prow: narrower, and angled down so it reads as going somewhere.
		root.addChild("prow", ModelPartBuilder.create().uv(0, 100).cuboid(-3f, -1f, -24f, 6f, 4f, 6f), ModelTransform.NONE);
		// The belly, where the cargo throat is.
		root.addChild("belly", ModelPartBuilder.create().uv(0, 60).cuboid(-8f, 3f, -10f, 16f, 5f, 20f), ModelTransform.NONE);
		// Engine block astern, and the four bells on it.
		root.addChild("engines", ModelPartBuilder.create().uv(0, 140).cuboid(-9f, -4f, 18f, 18f, 9f, 6f), ModelTransform.NONE);
		// Dorsal fins, so it has a top and a bottom from any angle.
		root.addChild("fin_left", ModelPartBuilder.create().uv(0, 180).cuboid(-14f, -2f, -4f, 8f, 2f, 14f), ModelTransform.NONE);
		root.addChild("fin_right", ModelPartBuilder.create().uv(0, 180).cuboid(6f, -2f, -4f, 8f, 2f, 14f), ModelTransform.NONE);
		// A tower on the spine: the only part with windows, and nobody is looking out of them.
		root.addChild("tower", ModelPartBuilder.create().uv(0, 215).cuboid(-3f, -9f, 6f, 6f, 6f, 12f), ModelTransform.NONE);

		return TexturedModelData.of(data, 256, 256);
	}

	@Override
	public void setAngles(CompanyShipEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	@Override
	public ModelPart getPart() {
		return root;
	}
}
