package dev.psyda.surrogate.recipe;

import dev.psyda.surrogate.item.ScrapChassisItem;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Shapeless: scrap chassis + 2 iron ingots + servo motor, keeping the scrap's upgrades and identity. */
public class RobotRebuildRecipe extends SpecialCraftingRecipe {
	public RobotRebuildRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Nullable
	private static ItemStack findScrap(CraftingRecipeInput input) {
		ItemStack scrap = ItemStack.EMPTY;
		int iron = 0;
		int servos = 0;
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack stack = input.getStackInSlot(i);
			if (stack.isEmpty()) continue;
			if (stack.isOf(ModItems.SCRAP_CHASSIS)) {
				if (!scrap.isEmpty()) return null;
				scrap = stack;
			} else if (stack.isOf(Items.IRON_INGOT)) {
				iron++;
			} else if (stack.isOf(ModItems.SERVO_MOTOR)) {
				servos++;
			} else {
				return null;
			}
		}
		return !scrap.isEmpty() && iron == 2 && servos == 1 ? scrap : null;
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return findScrap(input) != null;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack scrap = findScrap(input);
		return scrap == null ? ItemStack.EMPTY : ScrapChassisItem.rebuild(scrap, lookup);
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 4;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.ROBOT_REBUILD;
	}
}
