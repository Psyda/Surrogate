package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The door in the middle of a base's docking collar. It is an airlock door that nobody can open: it stays
 * sealed until a crawler reports itself docked on the collar outside, and then it is the way from the pod
 * into the crawler. Redstone does not move it either. See docs/DESIGN-crawler.md.
 */
public class DockDoorBlock extends AirlockDoorBlock {
	public static final BooleanProperty DOCKED = BooleanProperty.of("docked");
	public static final MapCodec<DockDoorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::getBlockSetType),
			createSettingsCodec()
	).apply(instance, DockDoorBlock::new));

	public DockDoorBlock(BlockSetType type, Settings settings) {
		super(type, settings);
		setDefaultState(getDefaultState().with(DOCKED, false));
	}

	public DockDoorBlock(Settings settings) {
		this(TYPE, settings);
	}

	@Override
	public MapCodec<? extends DoorBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(DOCKED);
	}

	/**
	 * The collar door never swings: the pod would breathe the outside if it did. Coupled, using it steps the
	 * player straight through into the crawler's cabin; sealed, it says so.
	 */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		if (!state.get(DOCKED)) {
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.8f, 0.4f);
			player.sendMessage(Text.translatable("message.surrogate.dock_door.sealed").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) CrawlerInterior.boardFromCollar(serverPlayer, lower);
		return ActionResult.CONSUME;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		// Redstone never moves it either.
	}

	/** Marks both halves docked or not; an undocked door shuts. */
	public static void setDocked(World world, BlockPos pos, boolean docked) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof DockDoorBlock door)) return;
		BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		if (!docked) door.setOpenScripted(world, lower, false);
		for (BlockPos half : new BlockPos[]{lower, lower.up()}) {
			BlockState current = world.getBlockState(half);
			if (current.getBlock() instanceof DockDoorBlock && current.get(DOCKED) != docked) {
				world.setBlockState(half, current.with(DOCKED, docked), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
	}

	public static boolean isDocked(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getBlock() instanceof DockDoorBlock && state.get(DOCKED);
	}
}
