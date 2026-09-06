package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CrawlerModule;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Bolted on by using the item on a hull that is standing still. */
public class CrawlerModuleItem extends Item {
	private final CrawlerModule module;

	public CrawlerModuleItem(CrawlerModule module, Settings settings) {
		super(settings);
		this.module = module;
	}

	public CrawlerModule getModule() {
		return module;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		switch (module) {
			case CLADDING -> tooltip.add(Text.translatable("tooltip.surrogate.ceramic_cladding",
					Math.round(Surrogate.CONFIG.acidCladdingFactor * 100.0)).formatted(Formatting.GRAY));
		}
		tooltip.add(Text.translatable("tooltip.surrogate.parked_only").formatted(Formatting.DARK_GRAY));
	}
}
