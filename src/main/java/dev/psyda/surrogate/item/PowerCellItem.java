package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class PowerCellItem extends Item {
	public PowerCellItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.power_cell", Surrogate.CONFIG.powerCellEnergy).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.surrogate.power_cell.use").formatted(Formatting.DARK_GRAY));
	}
}
