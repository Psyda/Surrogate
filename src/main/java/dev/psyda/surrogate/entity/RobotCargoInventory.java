package dev.psyda.surrogate.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * A chest-shaped view of a parked chassis' hold, for loading and unloading it by hand. The hold has
 * {@link RobotEntity#CARGO_SIZE} slots laid out like a player inventory; only the fitted cargo bays are usable.
 */
public class RobotCargoInventory implements Inventory {
	private static final int ROWS = 5;
	private final RobotEntity robot;

	public RobotCargoInventory(RobotEntity robot) {
		this.robot = robot;
	}

	@Override
	public int size() {
		return ROWS * 9;
	}

	@Override
	public boolean isEmpty() {
		return robot.getCargoList().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return slot < RobotEntity.CARGO_SIZE ? robot.getCargo(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		return slot < RobotEntity.CARGO_SIZE ? Inventories.splitStack(robot.getCargoList(), slot, amount) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return slot < RobotEntity.CARGO_SIZE ? Inventories.removeStack(robot.getCargoList(), slot) : ItemStack.EMPTY;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (slot < RobotEntity.CARGO_SIZE) robot.setCargo(slot, stack);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return robot.isCargoSlotOpen(slot);
	}

	@Override
	public void markDirty() {
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return !robot.isRemoved() && !robot.isPiloted() && player.squaredDistanceTo(robot) <= 64.0;
	}

	@Override
	public void clear() {
		robot.clearCargo();
	}
}
