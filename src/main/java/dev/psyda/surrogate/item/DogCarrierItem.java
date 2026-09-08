package dev.psyda.surrogate.item;

import dev.psyda.surrogate.block.DogCarrierBlock;
import dev.psyda.surrogate.registry.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * The carrier as a thing in your hands. Says whether anyone is in it, and used on a dog puts the dog in it,
 * for the player who works out the order of operations the other way round.
 */
public class DogCarrierItem extends BlockItem {
	public DogCarrierItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof WolfEntity dog) || stack.contains(ModComponents.PET_DATA)) return ActionResult.PASS;
		if (user.getWorld().isClient) return ActionResult.SUCCESS;
		NbtCompound saved = DogCarrierBlock.save(dog);
		if (saved == null) return ActionResult.PASS;
		stack.set(ModComponents.PET_DATA, saved);
		user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WOLF_WHINE, SoundCategory.NEUTRAL, 0.8f, 1.1f);
		user.sendMessage(Text.translatable("message.surrogate.carrier.in", dog.getName()).formatted(Formatting.GRAY), true);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		NbtCompound pet = stack.get(ModComponents.PET_DATA);
		if (pet == null) {
			tooltip.add(Text.translatable("tooltip.surrogate.dog_carrier.empty").formatted(Formatting.GRAY));
			return;
		}
		tooltip.add(Text.translatable("tooltip.surrogate.dog_carrier.someone").formatted(Formatting.GOLD));
	}
}
