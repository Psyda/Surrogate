package dev.psyda.surrogate.item;

import dev.psyda.surrogate.errand.ErrandState;
import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Okafor's software, on a disk, because she has no way to send a file to a habitat whose uplink she is not
 * on. Used on any terminal in the base, once, and then the terminal has a page it did not have before:
 * everything the sampler has read, written up in her own words.
 *
 * <p>The disk is consumed. There is exactly one, and the readings live on the world rather than on it, so
 * losing it after it is in costs nothing and losing it before is a trip back to the greenhouse.
 */
public class AnalysisDiskItem extends Item {
	public AnalysisDiskItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient) return ActionResult.SUCCESS;
		if (!context.getWorld().getBlockState(context.getBlockPos()).isOf(ModBlocks.TERMINAL)) return ActionResult.PASS;
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;
		ErrandState errands = ErrandState.get(player.server);
		if (errands.disk) {
			player.sendMessage(Text.translatable("message.surrogate.disk.already"), true);
			return ActionResult.CONSUME;
		}
		errands.disk = true;
		errands.markDirty();
		if (!player.getAbilities().creativeMode) context.getStack().decrement(1);
		context.getWorld().playSound(null, context.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
				SoundCategory.BLOCKS, 0.7f, 1.6f);
		player.sendMessage(Text.translatable("message.surrogate.disk.installed").formatted(Formatting.GREEN), false);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.analysis_disk").formatted(Formatting.GRAY));
	}
}
