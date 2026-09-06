package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.client.crawler.CrawlerClientState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Movement keys do nothing while a cutscene has the controls, and drive the hull while a crawler console has them. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void surrogate$lockMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		Input input = (Input) (Object) this;
		if (CrawlerClientState.atConsole()) {
			CrawlerClientState.capture(input);
			return;
		}
		if (!CinematicState.isInputLocked()) return;
		input.pressingForward = false;
		input.pressingBack = false;
		input.pressingLeft = false;
		input.pressingRight = false;
		input.movementForward = 0f;
		input.movementSideways = 0f;
		input.jumping = false;
		input.sneaking = false;
	}
}
