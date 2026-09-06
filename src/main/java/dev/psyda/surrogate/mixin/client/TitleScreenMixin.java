package dev.psyda.surrogate.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.psyda.surrogate.Surrogate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.AccessibilityOnboardingButtons;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * The title screen is the game's: Sallow hanging in a starfield instead of the panorama, the name of the
 * mod instead of the logo, and the same buttons underneath. The vanilla widgets, logo, splash and realms
 * notice are never built; everything else about the screen (options, quitting, language) is vanilla.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Unique
	private static final Identifier SURROGATE$PLANET = Surrogate.id("textures/sky/sallow.png");
	@Unique
	private static final int SURROGATE$STARS = 260;
	@Unique
	private static final int SURROGATE$SULFUR = 0xFFE8C458;
	@Unique
	private static final int SURROGATE$PALE = 0xFFD2D6DC;
	@Unique
	private static final int SURROGATE$DIM = 0xFF8A93A0;

	protected TitleScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("HEAD"), cancellable = true)
	private void surrogate$init(CallbackInfo ci) {
		int cx = width / 2;
		int top = height / 4 + 48;
		addDrawableChild(ButtonWidget.builder(Text.translatable("menu.singleplayer"), button -> client.setScreen(new SelectWorldScreen(this)))
				.dimensions(cx - 100, top, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("menu.multiplayer"), button -> client.setScreen(new MultiplayerScreen(this)))
				.dimensions(cx - 100, top + 24, 200, 20).build());
		TextIconButtonWidget language = addDrawableChild(AccessibilityOnboardingButtons.createLanguageButton(20,
				button -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())), true));
		language.setPosition(cx - 124, top + 60);
		addDrawableChild(ButtonWidget.builder(Text.translatable("menu.options"), button -> client.setScreen(new OptionsScreen(this, client.options)))
				.dimensions(cx - 100, top + 60, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("menu.quit"), button -> client.scheduleStop())
				.dimensions(cx + 2, top + 60, 98, 20).build());
		TextIconButtonWidget accessibility = addDrawableChild(AccessibilityOnboardingButtons.createAccessibilityButton(20,
				button -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)), true));
		accessibility.setPosition(cx + 104, top + 60);
		ci.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void surrogate$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		super.render(context, mouseX, mouseY, delta);
		int cx = width / 2;
		int titleY = height / 4 - 14;
		context.getMatrices().push();
		context.getMatrices().translate(cx, titleY, 0);
		context.getMatrices().scale(4f, 4f, 1f);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("title.surrogate.name"), 0, 0, SURROGATE$PALE);
		context.getMatrices().pop();
		Text tagline = Text.translatable("title.surrogate.tagline");
		context.drawCenteredTextWithShadow(textRenderer, tagline, cx, titleY + 40, SURROGATE$SULFUR);
		context.fill(cx - 60, titleY + 52, cx + 60, titleY + 53, 0x66E8C458);

		String version = "Surrogate " + FabricLoader.getInstance().getModContainer("surrogate").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev")
				+ " / Minecraft " + SharedConstants.getGameVersion().getName();
		context.drawTextWithShadow(textRenderer, version, 2, height - 10, SURROGATE$DIM);
		String copyright = "Copyright Mojang AB. Do not distribute!";
		context.drawTextWithShadow(textRenderer, copyright, width - textRenderer.getWidth(copyright) - 6, height - 10, SURROGATE$DIM);
		ci.cancel();
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void surrogate$renderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		long now = Util.getMeasuringTimeMs();
		context.fillGradient(0, 0, width, height, 0xFF04060C, 0xFF0B1020);
		Random rng = new Random(41L);
		for (int i = 0; i < SURROGATE$STARS; i++) {
			int sx = (int) (rng.nextFloat() * width);
			int sy = (int) (rng.nextFloat() * height);
			float base = 0.35f + rng.nextFloat() * 0.65f;
			float twinkle = 0.75f + 0.25f * MathHelper.sin(now / 900f + i * 1.7f);
			int a = MathHelper.clamp(Math.round(base * twinkle * 255f), 30, 255);
			int size = rng.nextInt(9) == 0 ? 2 : 1;
			context.fill(sx, sy, sx + size, sy + size, (a << 24) | 0xE6ECF5);
		}
		// Sallow, off to the right, breathing very slowly.
		int size = Math.min((int) (height * 0.95f), 340);
		int px = width - (int) (size * 0.62f);
		int py = (int) (height * 0.12f + 5f * MathHelper.sin(now / 4000f));
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		context.drawTexture(SURROGATE$PLANET, px, py, 0f, 0f, size, size, size, size);
		// The night side: the terminator is a run of strips, since the gradient fill only runs top to bottom.
		// The terminator sits a third of the way across the disc, inside the window, and the rest is night.
		int strips = 48;
		int stripW = Math.max(1, size / strips);
		float radius = size / 2f;
		for (int i = 0; i < strips; i++) {
			float t = (i + 0.5f) / strips;
			int a = (int) (210f * MathHelper.clamp((t - 0.3f) / 0.25f, 0f, 1f));
			if (a <= 0) continue;
			float dx = (i + 0.5f) * stripW - radius;
			if (Math.abs(dx) >= radius) continue;
			int half = (int) Math.sqrt(radius * radius - dx * dx);
			context.fill(px + i * stripW, py + (int) radius - half, px + (i + 1) * stripW, py + (int) radius + half, a << 24);
		}
		RenderSystem.disableBlend();
		ci.cancel();
	}
}
