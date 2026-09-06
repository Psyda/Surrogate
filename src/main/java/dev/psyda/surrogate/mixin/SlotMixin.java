package dev.psyda.surrogate.mixin;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While piloting, the player's inventory is the chassis' hold. Slots without a fitted cargo bay are filled
 * with a sealed-bay placeholder that cannot be moved, and nothing can be put into them. The pocket crafting
 * grid is dead too unless the chassis has a fabricator: it has no hands for bench work.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
	@Shadow
	@Final
	public Inventory inventory;

	@Shadow
	public abstract int getIndex();

	@Shadow
	public abstract ItemStack getStack();

	@Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
	private void surrogate$keepSealedBays(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (getStack().isOf(ModItems.LOCKED_BAY)) cir.setReturnValue(false);
	}

	@Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
	private void surrogate$gateChassisSlots(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (inventory instanceof PlayerInventory playerInventory) {
			RobotEntity robot = chassisOf(playerInventory.player);
			if (robot != null && !robot.isCargoSlotOpen(getIndex())) cir.setReturnValue(false);
		} else if (inventory instanceof CraftingInventory crafting && crafting.getWidth() == 2
				&& ((CraftingInventoryAccessor) crafting).surrogate$getHandler() instanceof PlayerScreenHandler handler) {
			RobotEntity robot = chassisOf(((PlayerScreenHandlerAccessor) handler).surrogate$getOwner());
			if (robot != null && !robot.hasFabricator()) cir.setReturnValue(false);
		}
	}

	private static RobotEntity chassisOf(PlayerEntity player) {
		return player != null && player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) ? robot : null;
	}
}
