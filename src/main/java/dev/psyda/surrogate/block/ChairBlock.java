package dev.psyda.surrogate.block;

import dev.psyda.surrogate.entity.SeatEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * A chair, a stool: a directional prop you can sit on. Right-click sits you facing the way it faces; sneak
 * gets you up again, the way it does off anything else.
 */
public class ChairBlock extends PropBlock {
	/** Height of the cushion above the floor of the cell, in blocks. */
	private final double seat;

	public ChairBlock(Settings settings, VoxelShape north, double seat) {
		super(settings, Mount.FACING, north);
		this.seat = seat;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity served)) return ActionResult.SUCCESS;
		sit(server, pos, state.get(FACING), seat, served);
		return ActionResult.CONSUME;
	}

	/** Sits {@code player} on the cell at {@code pos}, facing {@code facing}. Shared with the couch. */
	public static void sit(ServerWorld world, BlockPos pos, Direction facing, double seat, ServerPlayerEntity player) {
		Vec3d at = new Vec3d(pos.getX() + 0.5, pos.getY() + seat - 0.05, pos.getZ() + 0.5);
		SeatEntity.sit(world, at, facing.asRotation(), player);
	}
}
