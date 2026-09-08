package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.Surrogate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dog's carrier, by the back door where it always was. Click it with the dog nearby and the dog is in
 * it; click it again and the dog is out. Pick it up and the dog comes with it, which nobody is going to say
 * out loud is allowed.
 */
public class DogCarrierBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty OCCUPIED = BooleanProperty.of("occupied");
	/** How far away the dog can be and still be called into the carrier. */
	private static final double CALL = 4.0;

	private final Map<Direction, VoxelShape> shapes;
	private final MapCodec<? extends DogCarrierBlock> codec;

	public DogCarrierBlock(Settings settings, VoxelShape north) {
		super(settings);
		this.shapes = ShapeUtil.horizontal(north);
		this.codec = createCodec(s -> new DogCarrierBlock(s, north));
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(OCCUPIED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return codec;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, OCCUPIED);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Occupied if the item placing it has a dog in it: the block entity reads the component right after
		// this, and the state has to agree with it or the door shows empty over a full crate.
		boolean occupied = ctx.getStack().contains(dev.psyda.surrogate.registry.ModComponents.PET_DATA);
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(OCCUPIED, occupied);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(FACING));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DogCarrierBlockEntity(pos, state);
	}

	// ------------------------------------------------------------------ in and out

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld server)) return ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof DogCarrierBlockEntity carrier)) {
			Surrogate.LOGGER.warn("Dog carrier at {} has no block entity", pos.toShortString());
			return ActionResult.PASS;
		}
		if (carrier.isOccupied()) {
			release(server, pos, state, carrier);
			return ActionResult.CONSUME;
		}
		WolfEntity dog = nearestDog(server, pos, player);
		Surrogate.LOGGER.info("Dog carrier at {}: {} clicked, nearest dog {}", pos.toShortString(),
				player.getGameProfile().getName(), dog == null ? "none" : dog.getBlockPos().toShortString());
		if (dog == null) {
			player.sendMessage(Text.translatable("message.surrogate.carrier.empty").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		NbtCompound saved = save(dog);
		if (saved == null) return ActionResult.CONSUME;
		carrier.setPet(saved);
		world.setBlockState(pos, state.with(OCCUPIED, true), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.ENTITY_WOLF_WHINE, SoundCategory.NEUTRAL, 0.8f, 1.1f);
		world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.3f);
		player.sendMessage(Text.translatable("message.surrogate.carrier.in", dog.getName()).formatted(Formatting.GRAY), true);
		return ActionResult.CONSUME;
	}

	/** The dog, saved whole and taken out of the world. Null if it could not be saved, in which case it stays. */
	@Nullable
	public static NbtCompound save(WolfEntity dog) {
		NbtCompound saved = new NbtCompound();
		if (!dog.saveSelfNbt(saved)) return null;
		dog.discard();
		return saved;
	}

	/** The player's own dog first, then any dog, nearest first. */
	@Nullable
	public static WolfEntity nearestDog(ServerWorld world, BlockPos pos, PlayerEntity player) {
		Vec3d centre = Vec3d.ofCenter(pos);
		List<WolfEntity> near = world.getEntitiesByClass(WolfEntity.class, new Box(pos).expand(CALL), WolfEntity::isAlive);
		return near.stream()
				.min(Comparator.<WolfEntity>comparingInt(w -> w.isOwner(player) ? 0 : 1).thenComparingDouble(w -> w.squaredDistanceTo(centre)))
				.orElse(null);
	}

	private void release(ServerWorld world, BlockPos pos, BlockState state, DogCarrierBlockEntity carrier) {
		NbtCompound saved = carrier.getPet();
		if (saved == null) return;
		Direction facing = state.get(FACING);
		BlockPos out = pos.offset(facing);
		if (!world.getBlockState(out).getCollisionShape(world, out).isEmpty()) out = pos.up();
		Entity dog = EntityType.getEntityFromNbt(saved, world).orElse(null);
		if (dog == null) {
			Surrogate.LOGGER.warn("Dog carrier at {}: could not rebuild what was in it", pos.toShortString());
			return;
		}
		// A fresh identity, in case whatever was saved is somehow still alive somewhere: two entities with
		// one uuid is a save that will not load.
		dog.setUuid(UUID.randomUUID());
		dog.refreshPositionAndAngles(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, facing.asRotation(), 0f);
		if (dog instanceof WolfEntity wolf) wolf.setSitting(false);
		world.spawnEntity(dog);
		carrier.setPet(null);
		world.setBlockState(pos, state.with(OCCUPIED, false), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.2f);
		world.playSound(null, pos, SoundEvents.ENTITY_WOLF_PANT, SoundCategory.NEUTRAL, 0.9f, 1.0f);
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
