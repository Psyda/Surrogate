package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.survey.SurveyScan;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The pillar. It is not clever and it does not have an upgrade path: it has a hole in the side that takes
 * power, and the more power has ever gone into it the further the table next to it can see.
 *
 * <p>Range goes as the square root of the charge, so the first two hundred metres are cheap and the last two
 * hundred are a project. That is the intended shape of the endgame: somebody with a geothermal field and
 * nothing left to spend it on pours it into this until the whole map is on the table.
 */
public class LongRangeScannerBlock extends BlockWithEntity {
	public static final MapCodec<LongRangeScannerBlock> CODEC = createCodec(LongRangeScannerBlock::new);
	/** Lit while it has charge worth speaking of, which is the only outward sign it is doing anything. */
	public static final BooleanProperty LIT = BooleanProperty.of("lit");

	private static final VoxelShape SHAPE = Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

	public LongRangeScannerBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(LIT, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/** What it has been fed and what that buys, because there is no other way to know. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof LongRangeScannerBlockEntity scanner)) return ActionResult.PASS;
		player.sendMessage(Text.translatable("message.surrogate.scanner.status",
				scanner.charge(), scanner.range()).formatted(Formatting.AQUA), false);
		int next = SurveyScan.rangeFor(scanner.charge() + scanner.stepCost());
		player.sendMessage(Text.translatable("message.surrogate.scanner.next",
				scanner.stepCost(), next - scanner.range()).formatted(Formatting.GRAY), false);
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LongRangeScannerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, ModBlockEntities.LONG_RANGE_SCANNER, LongRangeScannerBlockEntity::tick);
	}
}
