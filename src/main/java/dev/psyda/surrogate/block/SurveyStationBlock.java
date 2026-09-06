package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.network.SurveyPayloads;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.survey.SurveyNetwork;
import dev.psyda.surrogate.survey.SurveyScan;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A table with a glass top and a projector under it. Lean on it and it draws you the ground for a few
 * hundred metres in every direction, badly, in light.
 *
 * <p>What it can draw is what the beacon network covers — see {@link SurveyNetwork} — so the table on its
 * own is a disc around the room it is in, and everything beyond that is a walk with a bag of beacons.
 */
public class SurveyStationBlock extends BlockWithEntity {
	public static final MapCodec<SurveyStationBlock> CODEC = createCodec(SurveyStationBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);

	public SurveyStationBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SurveyStationBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, ModBlockEntities.SURVEY_STATION, SurveyStationBlockEntity::tick);
	}

	/**
	 * Reading the table. The scan is done here and sent whole rather than streamed: it is about seventeen
	 * kilobytes of shorts, it is only built when somebody actually leans on the table, and the alternative is
	 * a screen that fills in for ten seconds while the player watches.
	 */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.PASS;
		}
		int radius = Surrogate.CONFIG.surveyStationRadius;
		if (world.getBlockEntity(pos) instanceof SurveyStationBlockEntity station) radius = station.radius(serverWorld);
		SurveyNetwork.Result network = SurveyNetwork.walk(serverWorld, pos, radius);
		SurveyScan.Picture picture = SurveyScan.scan(serverWorld, pos, network);
		ServerPlayNetworking.send(serverPlayer, SurveyPayloads.Survey.of(picture));
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.8f);
		return ActionResult.CONSUME;
	}
}
