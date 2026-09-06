package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.block.ChargingDockBlock;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

/** Draws the parked chassis standing on the dock pad, facing the charging pillar. */
@Environment(EnvType.CLIENT)
public class ChargingDockRenderer implements BlockEntityRenderer<ChargingDockBlockEntity> {
	public ChargingDockRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(ChargingDockBlockEntity dock, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		RobotEntity robot = dock.getClientRobot();
		if (robot == null) return;
		Direction facing = dock.getCachedState().contains(ChargingDockBlock.FACING) ? dock.getCachedState().get(ChargingDockBlock.FACING) : Direction.NORTH;
		float yaw = facing.getOpposite().asRotation();
		robot.setYaw(yaw);
		robot.prevYaw = yaw;
		robot.bodyYaw = yaw;
		robot.prevBodyYaw = yaw;
		robot.headYaw = yaw;
		robot.prevHeadYaw = yaw;

		EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
		matrices.push();
		dispatcher.setRenderShadows(false);
		dispatcher.render(robot, 0.5 - facing.getOffsetX() * 0.08, 3.0 / 16.0, 0.5 - facing.getOffsetZ() * 0.08, yaw, tickDelta, matrices, vertexConsumers, light);
		dispatcher.setRenderShadows(true);
		matrices.pop();
	}
}
