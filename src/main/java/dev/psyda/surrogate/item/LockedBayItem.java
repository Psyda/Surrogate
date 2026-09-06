package dev.psyda.surrogate.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Fills the inventory slots a chassis has no cargo bay for while someone is piloting it. It cannot be picked
 * up, dropped or used (see the Slot mixin), and it is stripped back out the moment the link ends.
 */
public class LockedBayItem extends Item {
	public LockedBayItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.locked_bay").formatted(Formatting.DARK_GRAY));
	}
}
