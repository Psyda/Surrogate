package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.recipe.RobotRebuildRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModRecipes {
	public static final RecipeSerializer<RobotRebuildRecipe> ROBOT_REBUILD = Registry.register(Registries.RECIPE_SERIALIZER,
			Surrogate.id("robot_rebuild"), new SpecialRecipeSerializer<>(RobotRebuildRecipe::new));

	public static void register() {
	}

	private ModRecipes() {
	}
}
