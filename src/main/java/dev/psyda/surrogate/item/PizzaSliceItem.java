package dev.psyda.surrogate.item;

import dev.psyda.surrogate.flashback.Flashback;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * A slice out of the box on the coffee table. Edible whether or not you are hungry, because the point of it is
 * not the food; it is that the last thing you ate on Earth was cold and came out of a box.
 */
public class PizzaSliceItem extends Item {
	public static final FoodComponent FOOD = new FoodComponent.Builder().nutrition(3).saturationModifier(0.3f).alwaysEdible().build();

	public PizzaSliceItem(Settings settings) {
		super(settings.food(FOOD));
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (user instanceof ServerPlayerEntity player) Flashback.noteAte(player);
		return super.finishUsing(stack, world, user);
	}
}
