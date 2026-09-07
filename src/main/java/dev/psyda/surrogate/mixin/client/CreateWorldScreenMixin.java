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

	@Unique
	private boolean surrogate$seedOffered;

	/**
	 * The seed the campaign was designed against, offered rather than forced: only into an empty box, and
	 * only where the world is going to be a Toxic Wastes one. Anything the player types stays typed. It goes
	 * in at the head of init, because the seed field reads the creator once when it is built.
	 */
	@Inject(method = "init", at = @At("HEAD"))
	private void surrogate$offerLockedSeed(CallbackInfo ci) {
		if (surrogate$seedOffered) return;
		surrogate$seedOffered = true;
		String locked = Surrogate.CONFIG.lockedSeed;
		if (locked == null || locked.isBlank()) return;
		String current = worldCreator.getSeed();
		if (current != null && !current.isBlank()) return;
		WorldCreator.WorldType type = worldCreator.getWorldType();
		// Either it is already ours, or it is the vanilla default and the tail of init is about to make it ours.
		if (type != null && !type.preset().matchesKey(WorldPresets.DEFAULT) && !type.preset().matchesKey(SURROGATE$TOXIC_WASTES)) return;
		worldCreator.setSeed(locked);
	}

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
