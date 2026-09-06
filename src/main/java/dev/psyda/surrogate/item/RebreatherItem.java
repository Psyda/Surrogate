package dev.psyda.surrogate.item;

import dev.psyda.surrogate.registry.ModEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** A single-use cartridge: sixty seconds of clean air for a body that has to go somewhere it should not. */
public class RebreatherItem extends Item {
	public static final int SECONDS = 60;

	public RebreatherItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!world.isClient) {
			player.addStatusEffect(new StatusEffectInstance(ModEffects.REBREATHER, SECONDS * 20, 0, false, true, true));
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.9f, 0.6f);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.4f, 1.8f);
			if (!player.getAbilities().creativeMode) stack.decrement(1);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.rebreather", SECONDS).formatted(Formatting.GRAY));
	}
}
