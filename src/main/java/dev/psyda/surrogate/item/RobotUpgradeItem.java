package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Applied by using the item on a powered-down chassis. */
public class RobotUpgradeItem extends Item {
	public enum Kind {
		PLATING_MK1,
		PLATING_MK2,
		BATTERY,
		CARGO_BAY,
		FABRICATOR
	}

	private final Kind kind;

	public RobotUpgradeItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		switch (kind) {
			case PLATING_MK1 -> tooltip.add(Text.translatable("tooltip.surrogate.plating", Surrogate.CONFIG.platingMk1Health).formatted(Formatting.GRAY));
			case PLATING_MK2 -> tooltip.add(Text.translatable("tooltip.surrogate.plating", Surrogate.CONFIG.platingMk2Health).formatted(Formatting.GRAY));
			case BATTERY -> tooltip.add(Text.translatable("tooltip.surrogate.battery", Surrogate.CONFIG.batteryTierMultiplier).formatted(Formatting.GRAY));
			case CARGO_BAY -> tooltip.add(Text.translatable("tooltip.surrogate.cargo_bay", Surrogate.CONFIG.cargoSlotsPerBay, Surrogate.CONFIG.cargoBayMaxTier).formatted(Formatting.GRAY));
			case FABRICATOR -> tooltip.add(Text.translatable("tooltip.surrogate.fabricator").formatted(Formatting.GRAY));
		}
		tooltip.add(Text.translatable("tooltip.surrogate.offline_only").formatted(Formatting.DARK_GRAY));
	}
}
