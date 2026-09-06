package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * A door that counts as airtight while closed and slides shut on its own a few seconds after being opened.
 * Two of them with a one block chamber between make an airlock a chassis can cycle through without ever
 * connecting the inside to the outside.
 */
public class AirlockDoorBlock extends DoorBlock {
	public static final BlockSetType TYPE = new BlockSetType("surrogate:airlock", true, true, true,
			BlockSetType.ActivationRule.EVERYTHING, BlockSoundGroup.METAL,
			SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundEvents.BLOCK_IRON_DOOR_OPEN,
			SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN,
			SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON);
	public static final MapCodec<AirlockDoorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::getBlockSetType),
			createSettingsCodec()
	).apply(instance, AirlockDoorBlock::new));

	public AirlockDoorBlock(BlockSetType type, Settings settings) {
		super(type, settings);
	}

	public AirlockDoorBlock(Settings settings) {
		this(TYPE, settings);
	}

	@Override
	public MapCodec<? extends DoorBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		boolean wasOpen = state.get(OPEN);
		if (!wasOpen && !world.isClient && isInterlocked(world, pos, state, player)) {
			return ActionResult.CONSUME;
		}
		ActionResult result = super.onUse(state, world, pos, player, hit);
		if (!world.isClient) afterToggle(world, pos, wasOpen);
		return result;
	}

	/**
	 * A door between a decon chamber and a sealed room stays shut while the chamber's shower is still busy
	 * with someone, or is refusing them because of what they carry. The door to the outside never locks.
	 */
	private boolean isInterlocked(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		BlockPos base = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		Direction facing = state.get(FACING);
		for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
			BlockPos chamberCell = base.offset(side);
			BlockPos roomCell = base.offset(side.getOpposite());
			DeconShowerBlockEntity shower = DeconShowerBlockEntity.forCell(world, chamberCell);
			if (shower == null || !shower.isLocking()) continue;
			if (Atmosphere.volumeAt(world, roomCell) == null) continue;
			world.playSound(null, base, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.8f, 0.4f);
			player.sendMessage(shower.isAlarm()
					? Text.translatable("message.surrogate.airlock.contaminated", shower.getAlarmSummary()).formatted(Formatting.RED)
					: Text.translatable("message.surrogate.airlock.washing").formatted(Formatting.AQUA), true);
			return true;
		}
		return false;
	}

	/**
	 * Opens or closes the door as if someone had used it, with the same auto-close timer and atmosphere
	 * rescan. Used by scripted characters, who have no hands to click with.
	 */
	public void setOpenScripted(World world, BlockPos pos, boolean open) {
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(this)) return;
		BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		BlockState lowerState = world.getBlockState(lower);
		if (!lowerState.isOf(this) || lowerState.get(OPEN) == open) return;
		setOpen(null, world, lowerState, lower, open);
		if (!world.isClient) afterToggle(world, lower, !open);
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		boolean wasOpen = state.get(OPEN);
		super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
		if (!world.isClient) afterToggle(world, pos, wasOpen);
	}

	private void afterToggle(World world, BlockPos pos, boolean wasOpen) {
		BlockState now = world.getBlockState(pos);
		if (!now.isOf(this)) return;
		boolean open = now.get(OPEN);
		if (open && !wasOpen) {
			world.scheduleBlockTick(pos, this, Math.max(10, Surrogate.CONFIG.airlockCloseTicks));
		}
		if (open != wasOpen) {
			Atmosphere.invalidateAround(world, pos);
			Atmosphere.invalidateAround(world, pos.offset(now.get(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
		}
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!state.get(OPEN)) return;
		if (state.get(POWERED)) {
			// Held open by redstone: check again later rather than fighting the circuit.
			world.scheduleBlockTick(pos, this, 20);
			return;
		}
		setOpen(null, world, state, pos, false);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 0.35f, 1.7f);
		Atmosphere.invalidateAround(world, pos);
		Atmosphere.invalidateAround(world, pos.offset(state.get(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
	}
}
