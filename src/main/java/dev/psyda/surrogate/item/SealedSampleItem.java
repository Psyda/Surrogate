package dev.psyda.surrogate.item;

import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.SealedVolume;
import dev.psyda.surrogate.research.Research;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * A litre of the seep in a crimped flask, taken from the channel by a chassis that had no business standing
 * there. The seal is the whole point: break it outside and the sample is the air, not the water, and the run
 * is worth nothing. Indoors it stays shut until somebody with a bench wants it.
 */
public class SealedSampleItem extends Item {
	public SealedSampleItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.success(stack, world.isClient);
		player.getItemCooldownManager().set(this, 20);
		SealedVolume volume = Atmosphere.volumeAt(world, player.getBlockPos());
		if (volume != null && volume.quality > 0.5f) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.PLAYERS, 0.5f, 1.4f);
			player.sendMessage(Text.translatable("message.surrogate.sample.sealed").formatted(Formatting.GRAY), true);
			return TypedActionResult.success(stack, false);
		}
		// Opened in the open air. Sallow gets into it before anyone else does.
		stack.decrement(1);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 0.9f, 1.6f);
		player.sendMessage(Text.translatable("message.surrogate.sample.spoiled").formatted(Formatting.RED), false);
		Research.sampleSpoiled(player);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.sealed_sample").formatted(Formatting.GRAY));
	}
}
