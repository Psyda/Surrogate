package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.registry.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Pickaxe and shovel in one, fast, and it never wears out: every block costs the chassis power instead.
 * Outside a chassis it is dead weight, there is nothing to couple it to.
 */
public class MiningDrillItem extends MiningToolItem {
	public MiningDrillItem(Settings settings) {
		super(RobotToolMaterial.DRILL, ModTags.DRILL_MINEABLE, settings
				.attributeModifiers(MiningToolItem.createAttributeModifiers(RobotToolMaterial.DRILL, 1.0f, -2.8f))
				.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
		return RobotTools.check(miner, Surrogate.CONFIG.drillEnergyPerBlock);
	}

	@Override
	public boolean postMine(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner) {
		RobotEntity robot = RobotTools.chassisOf(miner);
		if (robot != null && !world.isClient && state.getHardness(world, pos) != 0.0f) {
			robot.drainEnergy(Surrogate.CONFIG.drillEnergyPerBlock);
			// On top of what breaking the block already cost in noise: the drill is the loudest thing that
			// has ever happened to what lives under this rock.
			if (world instanceof ServerWorld server) {
				dev.psyda.surrogate.hazard.Borers.disturb(server, pos, Surrogate.CONFIG.borerDisturbancePerBlock);
			}
		}
		return true;
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.drill", Surrogate.CONFIG.drillEnergyPerBlock).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.chassis_only").formatted(Formatting.DARK_GRAY));
	}
}
