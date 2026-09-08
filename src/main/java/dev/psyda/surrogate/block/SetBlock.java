package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Something you switch on: the radio and the television in the flashback's rooms, and afterwards wherever the
 * player carries them.
 *
 * <p>One block class for both because they are the same object with a different face — a brown box from a
 * factory that closed before anyone here was born, with a switch on it. Right-click toggles; while it is on
 * it plays its noise on a loop and, on the set, throws a little light on the wall in front of it.
 *
 * <p>No block entity. A running set only needs to know one thing — when to make its next sound — and a
 * scheduled tick that reschedules itself is exactly that, for none of the cost of a ticking block entity in
 * every chunk somebody left one in.
 */
public class SetBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty ON = Properties.LIT;

	private final Map<Direction, VoxelShape> shapes;
	private final Supplier<SoundEvent> noise;
	private final int interval;
	private final MapCodec<? extends SetBlock> codec;

	public SetBlock(Settings settings, VoxelShape north, Supplier<SoundEvent> noise, int interval) {
		super(settings);
		this.shapes = ShapeUtil.horizontal(north);
		this.noise = noise;
		this.interval = Math.max(20, interval);
		this.codec = createCodec(s -> new SetBlock(s, north, noise, interval));
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(ON, false));
	}

	@Override
	protected MapCodec<? extends SetBlock> getCodec() {
		return codec;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, ON);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(FACING));
	}

	// ------------------------------------------------------------------ the switch

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		boolean on = !state.get(ON);
		world.setBlockState(pos, state.with(ON, on), Block.NOTIFY_ALL);
		world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5f, on ? 1.1f : 0.9f);
		if (on && !world.isClient) {
			play(world, pos);
			world.scheduleBlockTick(pos, this, interval);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!state.get(ON)) return;
		play(world, pos);
		world.scheduleBlockTick(pos, this, interval);
	}

	/**
	 * A set switched on by the world rather than by a hand — the one standing in the corner when the room is
	 * built — still has to start its own clock, or it is a lit box that never says anything.
	 */
	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!world.isClient && state.get(ON) && !oldState.isOf(this)) {
			world.scheduleBlockTick(pos, this, interval);
		}
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, net.minecraft.item.ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && state.get(ON)) world.scheduleBlockTick(pos, this, interval);
	}

	private void play(World world, BlockPos pos) {
		SoundEvent sound = noise.get();
		if (sound != null) world.playSound(null, pos, sound, SoundCategory.RECORDS, 0.6f, 1.0f);
		if (world instanceof ServerWorld server) {
			Direction facing = server.getBlockState(pos).get(FACING);
			server.spawnParticles(ParticleTypes.NOTE,
					pos.getX() + 0.5 + facing.getOffsetX() * 0.4,
					pos.getY() + 0.9,
					pos.getZ() + 0.5 + facing.getOffsetZ() * 0.4,
					1, 0.15, 0.1, 0.15, 0.0);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
