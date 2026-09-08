package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.block.enums.BedPart;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A crew bunk: two blocks long, like the beds it stands beside. A one-block bunk read as a dog bed.
 *
 * <p>It is a real bed. It was furniture for a while — the shape of one with none of the behaviour — and the
 * six of them in {@link dev.psyda.surrogate.world.ModuleTwo} are the whole point of the housewarming, so a
 * room of things that cannot be slept in was the wrong answer twice over. Extending {@link BedBlock} is what
 * buys that: the night, the spawn point, the head and foot pair, and {@code OCCUPIED}, which is the flag a
 * survivor's brain will claim a bunk with once they are living up here. The model is the only thing kept
 * back from vanilla — beds are drawn by a block entity renderer and this one is drawn from its own JSON, so
 * the render type is forced back to {@code MODEL} and no block entity is made for it.
 *
 * <p>It does not bounce, either. A bed is a mattress and this is a steel frame with a pad on it.
 */
public class BunkBlock extends BedBlock {
	public static final MapCodec<BedBlock> CODEC = createCodec(BunkBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<BedPart> PART = Properties.BED_PART;

	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontal(Block.createCuboidShape(0, 0, 0, 16, 9, 16));

	public BunkBlock(Settings settings) {
		// The dye colour only ever reaches the vanilla bed renderer, which never runs for this block.
		super(DyeColor.LIGHT_GRAY, settings);
	}

	@Override
	public MapCodec<BedBlock> getCodec() {
		return CODEC;
	}

	/** Drawn from {@code blockstates/bunk.json}, not by the bed renderer. */
	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	/**
	 * None. {@link BedBlock} makes a {@code BedBlockEntity} for its colour, and {@code BlockEntityType.BED}
	 * does not list this block, so one saved here would be dropped with a warning on every chunk load.
	 */
	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return null;
	}

	/** Bolted to the deck: both halves want something under them. */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
				|| world.getBlockState(pos.down()).isOf(this);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPES.get(state.get(FACING));
	}

	// ------------------------------------------------------------------ not a trampoline

	@Override
	public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
		entity.handleFallDamage(fallDistance, 1.0f, world.getDamageSources().fall());
	}

	@Override
	public void onEntityLand(BlockView world, Entity entity) {
		entity.setVelocity(entity.getVelocity().multiply(1.0, 0.0, 1.0));
	}
}
