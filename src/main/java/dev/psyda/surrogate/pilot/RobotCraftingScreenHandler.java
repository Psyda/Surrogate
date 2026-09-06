package dev.psyda.surrogate.pilot;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.item.RobotTools;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;

/** The fabricator: a full crafting bench built into the chassis, usable from the pilot menu. */
public class RobotCraftingScreenHandler extends CraftingScreenHandler {
	private final RobotEntity robot;

	public RobotCraftingScreenHandler(int syncId, PlayerInventory playerInventory, RobotEntity robot) {
		super(syncId, playerInventory, ScreenHandlerContext.create(robot.getWorld(), robot.getBlockPos()));
		this.robot = robot;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return RobotTools.chassisOf(player) == robot && robot.hasFabricator();
	}
}
