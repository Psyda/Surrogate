package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * What is left of a machine the belt got to: a slumped, holed casing with the shape of the thing still in
 * it. It is not a full cube, so a corroded life support unit is also a hole in the wall, which is how the
 * player usually finds out. Use a hull plate on it and the machine it was comes back.
 */
public class CorrodedMachineBlock extends BlockWithEntity {
	public static final MapCodec<CorrodedMachineBlock> CODEC = createCodec(CorrodedMachineBlock::new);
	private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 13, 15);

	public CorrodedMachineBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CorrodedMachineBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!stack.isOf(ModItems.HULL_PLATING) && !stack.isOf(ModItems.REPAIR_KIT)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		BlockState was = AcidRain.original(world, pos);
		if (was == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (world.isClient) return ItemActionResult.SUCCESS;
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		world.setBlockState(pos, was, Block.NOTIFY_ALL);
		if (world instanceof ServerWorld server) {
			AcidRain.clear(server, pos);
			server.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
		return ItemActionResult.SUCCESS;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) {
			BlockState was = AcidRain.original(world, pos);
			Text what = was != null ? was.getBlock().getName() : Text.translatable("message.surrogate.acid.something");
			player.sendMessage(Text.translatable("message.surrogate.acid.needs_plate", what).formatted(Formatting.GRAY), true);
			world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 0.6f, 0.8f);
		}
		return ActionResult.SUCCESS;
	}
}
