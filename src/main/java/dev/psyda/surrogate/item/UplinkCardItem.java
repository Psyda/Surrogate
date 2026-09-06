package dev.psyda.surrogate.item;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.registry.ModComponents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

/** Use on a chassis (or a dock holding one) to record it, then use on a dive chair to bind them. */
public class UplinkCardItem extends Item {
	public UplinkCardItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof RobotEntity robot)) return ActionResult.PASS;
		// An NPC's chassis is driven by a script and will never answer a chair.
		if (!robot.isClaimable()) {
			if (!user.getWorld().isClient) {
				user.sendMessage(Text.translatable("message.surrogate.card_refused", robot.getName()).formatted(Formatting.RED), true);
			}
			return ActionResult.success(user.getWorld().isClient);
		}
		if (!user.getWorld().isClient) {
			stack.set(ModComponents.UPLINK_TARGET, new ModComponents.UplinkTarget(robot.getUuid(), robot.getName().getString()));
			user.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.6f, 1.8f);
			user.sendMessage(Text.translatable("message.surrogate.card_linked", robot.getName()), true);
		}
		return ActionResult.success(user.getWorld().isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		ModComponents.UplinkTarget target = stack.get(ModComponents.UPLINK_TARGET);
		if (target == null) {
			tooltip.add(Text.translatable("tooltip.surrogate.uplink.blank").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.surrogate.uplink.linked", target.name()).formatted(Formatting.AQUA));
		}
		tooltip.add(Text.translatable("tooltip.surrogate.uplink.use").formatted(Formatting.DARK_GRAY));
	}
}
