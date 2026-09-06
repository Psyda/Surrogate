package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A new world is the Toxic Wastes unless the player picks something else; recreating a world keeps its own.
 * The world type has to be set through the creator, not just named: naming it only changed the label while
 * the generator underneath stayed vanilla until the type was cycled by hand.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@Unique
	private static final RegistryKey<WorldPreset> SURROGATE$TOXIC_WASTES = RegistryKey.of(RegistryKeys.WORLD_PRESET, Surrogate.id("toxic_wastes"));

	@Shadow
	@Final
	WorldCreator worldCreator;

	@Unique
	private boolean surrogate$presetApplied;

	@Inject(method = "init", at = @At("TAIL"))
	private void surrogate$defaultPreset(CallbackInfo ci) {
		if (surrogate$presetApplied) return;
		surrogate$presetApplied = true;
		WorldCreator.WorldType current = worldCreator.getWorldType();
		if (current == null || !current.preset().matchesKey(WorldPresets.DEFAULT)) return;
		for (WorldCreator.WorldType type : worldCreator.getNormalWorldTypes()) {
			if (type.preset().matchesKey(SURROGATE$TOXIC_WASTES)) {
				worldCreator.setWorldType(type);
				return;
			}
		}
	}
}
