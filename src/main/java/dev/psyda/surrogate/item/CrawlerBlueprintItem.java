package dev.psyda.surrogate.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The crawler's plans, as the researcher sent them over the port. The crawler kit cannot be assembled
 * without it on the bench, and the bench hands it back: it is a file, not a part.
 */
public class CrawlerBlueprintItem extends Item {
	public CrawlerBlueprintItem(Settings settings) {
		super(settings);
	}

	@Override
	public ItemStack getRecipeRemainder(ItemStack stack) {
		return stack.copyWithCount(1);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.crawler_blueprint").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.crawler_blueprint.use").formatted(Formatting.DARK_GRAY));
	}
}
