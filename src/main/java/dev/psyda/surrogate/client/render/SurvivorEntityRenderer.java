package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survivor.SurvivorEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** A survivor in a hazmat suit, drawn with the vanilla player model and a skin per character. */
@Environment(EnvType.CLIENT)
public class SurvivorEntityRenderer extends MobEntityRenderer<SurvivorEntity, PlayerEntityModel<SurvivorEntity>> {
	public SurvivorEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public Identifier getTexture(SurvivorEntity entity) {
		return Surrogate.id("textures/entity/survivor_" + entity.getCharacter().key() + ".png");
	}
}
