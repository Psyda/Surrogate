package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A plasma blade for the chassis' manipulator. Hits hard, never dulls, and draws chassis power per strike.
 * The swing itself is refused outside a chassis, see {@link dev.psyda.surrogate.pilot.PilotManager}.
 */
public class ArcCutterItem extends SwordItem {
	public ArcCutterItem(Settings settings) {
		super(RobotToolMaterial.CUTTER, settings
				.attributeModifiers(SwordItem.createAttributeModifiers(RobotToolMaterial.CUTTER, 3, -2.2f))
				.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		RobotEntity robot = RobotTools.chassisOf(attacker);
		if (robot != null && !attacker.getWorld().isClient) {
			robot.drainEnergy(Surrogate.CONFIG.cutterEnergyPerHit);
		}
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.cutter", Surrogate.CONFIG.cutterEnergyPerHit).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.chassis_only").formatted(Formatting.DARK_GRAY));
	}
}
