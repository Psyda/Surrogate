package dev.psyda.surrogate.block;

import dev.psyda.surrogate.item.VehicleKit;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The gantry's memory: which crate is on the pad and how far along the build is. The hull takes shape on the
 * ground in front of the gantry; when the timer runs out the kit puts it there for real.
 */
public class VehicleFabricatorBlockEntity extends BlockEntity {
	/** How far ahead of the gantry the finished vehicle stands. */
	public static final int REACH = 4;

	private ItemStack kit = ItemStack.EMPTY;
	private int progress;

	public VehicleFabricatorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.VEHICLE_FABRICATOR, pos, state);
	}

	public ItemStack getKit() {
		return kit;
	}

	public boolean isBuilding() {
		return !kit.isEmpty();
	}

	public int getProgress() {
		return progress;
	}

	public int getBuildTicks() {
		return kit.getItem() instanceof VehicleKit vehicle ? vehicle.buildTicks() : 1;
	}

	/** Where the vehicle stands, feet on the ground, for a gantry facing {@code facing}. */
	public static Vec3d buildSite(BlockPos pos, Direction facing) {
		BlockPos ahead = pos.offset(facing, REACH);
		return new Vec3d(ahead.getX() + 0.5, ahead.getY(), ahead.getZ() + 0.5);
	}

	public Vec3d buildSite() {
		return buildSite(pos, getCachedState().get(VehicleFabricatorBlock.FACING));
	}

	public float buildYaw() {
		return getCachedState().get(VehicleFabricatorBlock.FACING).asRotation();
	}

	/** A crate offered to the pad. Takes one when the pad is idle and the ground ahead is clear. */
	public boolean start(ItemStack stack, @Nullable PlayerEntity player) {
		if (!(world instanceof ServerWorld server) || !(stack.getItem() instanceof VehicleKit vehicle)) return false;
		if (isBuilding()) {
			say(player, "message.surrogate.fabricator.busy", Formatting.GRAY);
			return false;
		}
		Box room = vehicle.footprint(buildSite());
		if (!server.isSpaceEmpty(null, room)) {
			say(player, "message.surrogate.fabricator.no_room", Formatting.RED);
			return false;
		}
		kit = stack.copyWithCount(1);
		if (player == null || !player.isCreative()) stack.decrement(1);
		progress = 0;
		server.setBlockState(pos, getCachedState().with(VehicleFabricatorBlock.BUILDING, true));
		server.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.2f);
		say(player, "message.surrogate.fabricator.started", Formatting.AQUA);
		sync();
		return true;
	}

	/** A hand on the pad with nothing in it: how far along, or what it wants. */
	public void report(PlayerEntity player) {
		if (isBuilding()) {
			int percent = Math.min(100, progress * 100 / Math.max(1, getBuildTicks()));
			player.sendMessage(Text.translatable("message.surrogate.fabricator.progress", percent).formatted(Formatting.AQUA), true);
		} else {
			say(player, "message.surrogate.fabricator.idle", Formatting.GRAY);
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, VehicleFabricatorBlockEntity gantry) {
		if (!gantry.isBuilding()) return;
		int total = gantry.getBuildTicks();
		if (gantry.progress < total) gantry.progress++;
		if (!(world instanceof ServerWorld server)) return;
		Vec3d site = gantry.buildSite();
		Direction facing = state.get(VehicleFabricatorBlock.FACING);
		if (gantry.progress % 6 == 0) {
			// Sparks off the emitters on the beam, and a drift of them over the hull taking shape.
			Vec3d beam = Vec3d.ofCenter(pos).add(0, 1.4, 0);
			server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, beam.x, beam.y, beam.z, 3, 0.4, 0.05, 0.4, 0.02);
			double reach = 2.2 * gantry.progress / (double) total;
			for (int i = 0; i < 3; i++) {
				double dx = server.random.nextDouble() * 5.0 - 2.5;
				double dz = server.random.nextDouble() * 5.0 - 2.5;
				server.spawnParticles(ParticleTypes.END_ROD, site.x + dx, site.y + reach + server.random.nextDouble() * 0.6, site.z + dz, 1, 0, 0.02, 0, 0.0);
			}
			Vec3d toward = Vec3d.of(facing.getVector());
			server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, beam.x + toward.x, beam.y - 0.3, beam.z + toward.z, 2, 0.2, 0.2, 0.2, 0.1);
		}
		if (gantry.progress % 40 == 1) {
			server.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.6f);
		}
		if (gantry.progress >= total) {
			Entity built = ((VehicleKit) gantry.kit.getItem()).assemble(server, site, gantry.buildYaw());
			if (built == null) {
				// Something is parked on the pad. Hold at the end of the build until it moves.
				if (gantry.progress % 60 == 0) server.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.6f, 0.5f);
				return;
			}
			gantry.kit = ItemStack.EMPTY;
			gantry.progress = 0;
			server.setBlockState(pos, state.with(VehicleFabricatorBlock.BUILDING, false));
			server.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
			server.playSound(null, BlockPos.ofFloored(site), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0f, 0.8f);
			server.spawnParticles(ParticleTypes.FLASH, site.x, site.y + 1.5, site.z, 1, 0, 0, 0, 0);
			gantry.sync();
		}
	}

	/** The crate goes back on the ground if the gantry is taken apart mid-build. */
	public ItemStack takeKit() {
		ItemStack out = kit;
		kit = ItemStack.EMPTY;
		progress = 0;
		return out;
	}

	private void say(@Nullable PlayerEntity player, String key, Formatting colour) {
		if (player != null) player.sendMessage(Text.translatable(key).formatted(colour), true);
	}

	private void sync() {
		markDirty();
		if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (!kit.isEmpty()) nbt.put("Kit", kit.encode(registryLookup));
		nbt.putInt("Progress", progress);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		kit = nbt.contains("Kit", NbtElement.COMPOUND_TYPE) ? ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("Kit")) : ItemStack.EMPTY;
		progress = nbt.getInt("Progress");
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}
