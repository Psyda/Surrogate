package dev.psyda.surrogate.item;

import dev.psyda.surrogate.entity.DartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** One of three darts from the tin behind the bar. Thrown with a right click; the board is where it always was. */
public class DartItem extends Item {
	public DartItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT,
				SoundCategory.PLAYERS, 0.5f, 1.4f + world.random.nextFloat() * 0.2f);
		if (!world.isClient) {
			DartEntity dart = new DartEntity(world, player);
			dart.setItem(stack);
			// Flat and quick: a dart is thrown at a wall, not lobbed at a target.
			dart.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 1.6f, 0.6f);
			world.spawnEntity(dart);
		}
		player.getItemCooldownManager().set(this, 8);
		if (!player.isCreative()) stack.decrement(1);
		return TypedActionResult.success(stack, world.isClient);
	}
}
