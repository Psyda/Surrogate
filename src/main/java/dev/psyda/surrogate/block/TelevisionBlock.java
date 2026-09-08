package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The television. Four channels, one of which is off: the news, the credits of something nobody watched, and
 * the snow between them. Each keeps its own noise going on a loop while it is on. No block entity; a
 * scheduled tick that reschedules itself is the whole clock.
 *
 * <p>A script can ask a set to keep quiet for a while, so the anchor can be heard over the murmur of the same
 * programme played by the block underneath it.
 */
public class TelevisionBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<Channel> CHANNEL = EnumProperty.of("channel", Channel.class);

	public enum Channel implements StringIdentifiable {
		OFF("off", null, 0),
		NEWS("news", () -> ModSounds.FLASHBACK_TELEVISION, 70),
		CREDITS("credits", () -> ModSounds.FLASHBACK_TV_CREDITS, 210),
		STATIC("static", () -> ModSounds.STATIC, 50);

		private final String name;
		@Nullable
		private final java.util.function.Supplier<SoundEvent> noise;
		private final int interval;

		Channel(String name, @Nullable java.util.function.Supplier<SoundEvent> noise, int interval) {
			this.name = name;
			this.noise = noise;
			this.interval = interval;
		}

		@Override
		public String asString() {
			return name;
		}

		public Channel next() {
			Channel[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	/** Sets a script has asked to keep quiet, by position, until the world time given. */
	private static final Map<BlockPos, Long> QUIET = new HashMap<>();

	private final Map<Direction, VoxelShape> shapes;

	public TelevisionBlock(Settings settings, VoxelShape north) {
		super(settings);
		this.shapes = ShapeUtil.horizontal(north);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(CHANNEL, Channel.OFF));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, CHANNEL);
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

	// ------------------------------------------------------------------ the dial

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		tune(world, pos, state, state.get(CHANNEL).next(), true);
		return ActionResult.SUCCESS;
	}

	/** Switches the set to {@code channel}, with the click of the dial if {@code click}. */
	public static void tune(World world, BlockPos pos, BlockState state, Channel channel, boolean click) {
		if (!(state.getBlock() instanceof TelevisionBlock set)) return;
		world.setBlockState(pos, state.with(CHANNEL, channel), Block.NOTIFY_ALL);
		if (click) world.playSound(null, pos, ModSounds.FLASHBACK_TV_SWITCH, SoundCategory.BLOCKS, 0.6f, 1.0f);
		if (!world.isClient && channel != Channel.OFF) {
			if (!click) set.play(world, pos, channel);
			world.scheduleBlockTick(pos, set, channel.interval);
		}
	}

	/** Keeps the set at {@code pos} from making its own noise for {@code ticks}: somebody else is talking. */
	public static void quiet(World world, BlockPos pos, int ticks) {
		QUIET.put(pos.toImmutable(), world.getTime() + ticks);
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		Channel channel = state.get(CHANNEL);
		if (channel == Channel.OFF) return;
		play(world, pos, channel);
		world.scheduleBlockTick(pos, this, channel.interval);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!world.isClient && state.get(CHANNEL) != Channel.OFF && !oldState.isOf(this)) {
			world.scheduleBlockTick(pos, this, state.get(CHANNEL).interval);
		}
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && state.get(CHANNEL) != Channel.OFF) world.scheduleBlockTick(pos, this, state.get(CHANNEL).interval);
	}

	private void play(World world, BlockPos pos, Channel channel) {
		Long until = QUIET.get(pos);
		if (until != null) {
			if (world.getTime() < until) return;
			QUIET.remove(pos);
		}
		if (channel.noise == null) return;
		SoundEvent sound = channel.noise.get();
		if (sound != null) world.playSound(null, pos, sound, SoundCategory.RECORDS, channel == Channel.STATIC ? 0.3f : 0.55f, 1.0f);
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
