package dev.psyda.surrogate.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A keepsake with a job: they will not grow in the wastes, and someone six hundred metres away wants them. */
public class TomatoSeedsItem extends Item {
	public TomatoSeedsItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.tomato_seeds").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.tomato_seeds.use").formatted(Formatting.DARK_GRAY));
	}
}
