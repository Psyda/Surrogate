package dev.psyda.surrogate.item;

import net.minecraft.block.Block;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/** Chassis-coupled tools: no durability, they burn the robot's power instead. Iron tier for drops. */
public enum RobotToolMaterial implements ToolMaterial {
	DRILL(9.0f, 1.0f),
	CUTTER(4.0f, 4.0f);

	private final float speed;
	private final float attackDamage;

	RobotToolMaterial(float speed, float attackDamage) {
		this.speed = speed;
		this.attackDamage = attackDamage;
	}

	@Override
	public int getDurability() {
		return 1000;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		return speed;
	}

	@Override
	public float getAttackDamage() {
		return attackDamage;
	}

	@Override
	public TagKey<Block> getInverseTag() {
		return BlockTags.INCORRECT_FOR_IRON_TOOL;
	}

	@Override
	public int getEnchantability() {
		return 10;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(Items.IRON_INGOT);
	}
}
