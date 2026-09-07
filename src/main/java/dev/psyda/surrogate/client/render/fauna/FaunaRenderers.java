package dev.psyda.surrogate.client.render.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.LanternSlugEntity;
import dev.psyda.surrogate.fauna.SlagbackEntity;
import dev.psyda.surrogate.fauna.TockerEntity;
import dev.psyda.surrogate.fauna.TrundleEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The four animals' renderers, together because there is almost nothing to any of them: a texture, a model,
 * and in two cases a layer that lights the eyes.
 *
 * <p>The eyes layer is vanilla's trick and worth knowing when reading the textures: it draws the entire
 * model a second time with a sheet that is transparent everywhere except the eyes, at full brightness. So
 * {@code tocker_eye.png} and {@code lantern_slug_eyes.png} are not eye-shaped images — they are the same UV
 * layout as the body sheet with everything but the eyes rubbed out.
 */
@Environment(EnvType.CLIENT)
public final class FaunaRenderers {
	private FaunaRenderers() {
	}

	public static class Trundle extends MobEntityRenderer<TrundleEntity, TrundleModel> {
		private static final Identifier TEXTURE = Surrogate.id("textures/entity/trundle.png");

		public Trundle(EntityRendererFactory.Context context) {
			super(context, new TrundleModel(context.getPart(TrundleModel.LAYER)), 0.4f);
		}

		@Override
		public Identifier getTexture(TrundleEntity entity) {
			return TEXTURE;
		}
	}

	public static class Slagback extends MobEntityRenderer<SlagbackEntity, SlagbackModel> {
		private static final Identifier TEXTURE = Surrogate.id("textures/entity/slagback.png");

		public Slagback(EntityRendererFactory.Context context) {
			super(context, new SlagbackModel(context.getPart(SlagbackModel.LAYER)), 0.6f);
		}

		@Override
		public Identifier getTexture(SlagbackEntity entity) {
			return TEXTURE;
		}

		/**
		 * Folded, it casts no shadow. A boulder-shaped shadow on open basalt is exactly the tell that would
		 * give the trick away from a distance, and a real boulder would not have one either.
		 */
		@Override
		protected void scale(SlagbackEntity entity, MatrixStack matrices, float amount) {
			this.shadowRadius = entity.isRoused() ? 0.6f : 0.0f;
			super.scale(entity, matrices, amount);
		}
	}

	public static class Tocker extends MobEntityRenderer<TockerEntity, TockerModel> {
		private static final Identifier TEXTURE = Surrogate.id("textures/entity/tocker.png");

		public Tocker(EntityRendererFactory.Context context) {
			super(context, new TockerModel(context.getPart(TockerModel.LAYER)), 0.3f);
			addFeature(new Eyes<>(this, Surrogate.id("textures/entity/tocker_eye.png")));
		}

		@Override
		public Identifier getTexture(TockerEntity entity) {
			return TEXTURE;
		}
	}

	public static class LanternSlug extends MobEntityRenderer<LanternSlugEntity, LanternSlugModel> {
		private static final Identifier TEXTURE = Surrogate.id("textures/entity/lantern_slug.png");

		public LanternSlug(EntityRendererFactory.Context context) {
			super(context, new LanternSlugModel(context.getPart(LanternSlugModel.LAYER)), 0.0f);
			addFeature(new Eyes<>(this, Surrogate.id("textures/entity/lantern_slug_eyes.png")));
		}

		@Override
		public Identifier getTexture(LanternSlugEntity entity) {
			return TEXTURE;
		}
	}

	/** Vanilla's eyes layer with the texture passed in rather than hard-coded per subclass. */
	private static class Eyes<T extends net.minecraft.entity.Entity, M extends EntityModel<T>> extends EyesFeatureRenderer<T, M> {
		private final RenderLayer layer;

		Eyes(FeatureRendererContext<T, M> context, Identifier texture) {
			super(context);
			this.layer = RenderLayer.getEyes(texture);
		}

		@Override
		public RenderLayer getEyesTexture() {
			return layer;
		}
	}
}
