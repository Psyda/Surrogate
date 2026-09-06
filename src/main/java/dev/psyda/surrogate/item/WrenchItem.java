package dev.psyda.surrogate.item;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.pilot.RobotRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

/** Sneak-use on a powered-down chassis to pick it up. Use on a wreck to salvage it. */
public class WrenchItem extends Item {
	public WrenchItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof RobotEntity robot)) return ActionResult.PASS;
		if (!user.isSneaking()) return ActionResult.PASS;
		if (!(robot.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
		// Somebody else's chassis is not salvage while its script is still using it.
		if (!robot.isClaimable()) {
			user.sendMessage(Text.translatable("message.surrogate.not_yours", robot.getName()).formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		if (robot.getState() != RobotState.OFFLINE || robot.isPiloted() || robot.hasPassengers()) {
			user.sendMessage(Text.translatable("message.surrogate.pickup_requires_offline").formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		ItemStack chassis = RobotChassisItem.toStack(robot);
		RobotRegistry.get(world.getServer()).remove(robot.getUuid());
		robot.discard();
		if (!user.giveItemStack(chassis)) user.dropItem(chassis, false);
		user.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.6f, 1.5f);
		user.sendMessage(Text.translatable("message.surrogate.picked_up"), true);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.wrench.pickup").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.wrench.salvage").formatted(Formatting.GRAY));
	}
}
