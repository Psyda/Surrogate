package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.atmosphere.SealedVolume;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

public class LifeSupportBlockEntity extends BlockEntity {
	private final SimpleEnergyStorage energy;
	private final SealedVolume volume;
	private int scanCooldown;
	private boolean scanRequested = true;
	private boolean wasSealed = true;
	private int alarmCooldown;
	private boolean registered;
	/** Units placed in the same tick would otherwise all scan in the same tick, forever. */
	private int phase = -1;

	public LifeSupportBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LIFE_SUPPORT, pos, state);
		SurrogateConfig cfg = Surrogate.CONFIG;
		this.energy = new SimpleEnergyStorage(cfg.lifeSupportEnergyCapacity, cfg.lifeSupportMaxInsertPerTick, 0) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
		this.volume = new SealedVolume(pos.toImmutable());
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public SealedVolume getVolume() {
		return volume;
	}

	public int getEnergyPercent() {
		return energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
	}

	/** Something nearby changed: rescan soon, but not on this very tick, so a door that flaps only costs one scan. */
	public void requestScan() {
		if (!scanRequested) {
			scanRequested = true;
			scanCooldown = Math.min(scanCooldown, 2);
		}
	}

	public int addEnergy(int amount) {
		long space = energy.getCapacity() - energy.getAmount();
		int accepted = (int) Math.min(space, amount);
		if (accepted > 0) {
			energy.amount += accepted;
			markDirty();
		}
		return accepted;
	}

	public static void tick(World world, BlockPos pos, BlockState state, LifeSupportBlockEntity unit) {
		if (world.isClient) return;
		if (!unit.registered) {
			Atmosphere.register(world, unit);
			unit.registered = true;
		}
		SurrogateConfig cfg = Surrogate.CONFIG;
		int interval = Math.max(1, cfg.lifeSupportScanInterval);
		if (unit.phase < 0) {
			unit.phase = Math.floorMod(pos.hashCode(), interval);
			unit.scanCooldown = unit.phase;
		}
		// Nobody near enough to breathe it: a room that was airtight last time stays airtight until someone comes back.
		boolean attended = world.isPlayerInRange(pos.getX(), pos.getY(), pos.getZ(), 160.0) || !unit.wasSealed;

		boolean due = --unit.scanCooldown <= 0;
		if ((unit.scanRequested && unit.scanCooldown <= 0) || (due && attended)) {
			unit.scanRequested = false;
			// A leaking room needs no second opinion every second; the doors and the walls will say when it changes.
			unit.scanCooldown = unit.volume.sealed ? interval : interval * 3;
			Direction facing = state.contains(LifeSupportBlock.FACING) ? state.get(LifeSupportBlock.FACING) : Direction.NORTH;
			Atmosphere.scan(world, pos.offset(facing), unit.volume);
			if (unit.wasSealed && !unit.volume.sealed && unit.volume.size > 0 && unit.alarmCooldown <= 0) {
				unit.alarm((ServerWorld) world);
			}
			unit.wasSealed = unit.volume.sealed;
		} else if (due) {
			unit.scanCooldown = interval;
		}
		if (unit.alarmCooldown > 0) unit.alarmCooldown--;

		boolean powered = unit.energy.getAmount() >= cfg.lifeSupportDrainPerTick;
		if (powered) {
			unit.energy.amount -= cfg.lifeSupportDrainPerTick;
			if (world.getTime() % 20 == 0) unit.markDirty();
		}
		unit.volume.powered = powered;
		if (attended && (world.getTime() + unit.phase + 7) % 20 == 0) unit.sampleContamination(world);

		float quality = unit.volume.quality;
		if (!unit.volume.sealed) quality -= perTick(cfg.airLeakSeconds);
		else if (powered) quality += perTick(cfg.airRecoverySeconds);
		else quality -= perTick(cfg.airStaleSeconds);
		quality -= unit.volume.contaminationDrain;
		unit.volume.quality = MathHelper.clamp(quality, 0f, 1f);

		boolean sealed = unit.volume.sealed && unit.volume.size > 0;
		if (state.get(LifeSupportBlock.ACTIVE) != powered || state.get(LifeSupportBlock.SEALED) != sealed) {
			world.setBlockState(pos, state.with(LifeSupportBlock.ACTIVE, powered).with(LifeSupportBlock.SEALED, sealed), Block.NOTIFY_ALL);
		}
		if (world.getTime() % 40 == 0) unit.sync();
	}

	/**
	 * Anything that came in from outside fouls the air: a chassis caked in dust, caustic ore in someone's
	 * pockets or lying on the floor, and caustic blocks used as walls. Sampled once a second, drained every tick.
	 */
	private void sampleContamination(World world) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		double perTick = 0.0;
		int dirty = 0;
		int items = 0;
		if (volume.size > 0) {
			for (Entity entity : world.getOtherEntities(null, volume.bounds, e -> volume.contains(e.getBlockPos()))) {
				if (entity instanceof RobotEntity robot && robot.getContamination() > 0.01f) {
					perTick += robot.getContamination() * cfg.contaminationDrainPerSecond / 20.0;
					dirty++;
				} else if (entity instanceof PlayerEntity player) {
					items += Atmosphere.causticCount(player.getInventory());
				} else if (entity instanceof ItemEntity item && item.getStack().isIn(ModTags.CAUSTIC_ITEMS)) {
					items += item.getStack().getCount();
				}
			}
		}
		perTick += items * cfg.causticItemDrainPerSecond / 20.0 + volume.causticBlocks * cfg.causticBlockDrainPerSecond / 20.0;
		volume.dirtyChassis = dirty;
		volume.causticItems = items;
		volume.contaminationDrain = (float) perTick;
	}

	/** The room just stopped being airtight: tell everyone whose body is breathing it. */
	private void alarm(ServerWorld world) {
		alarmCooldown = 200;
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.5f);
		BlockPos leak = volume.leak;
		Text where = leak == null ? Text.literal("?") : Text.literal(leak.getX() + ", " + leak.getY() + ", " + leak.getZ());
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			BlockPos body = Exposure.bodyPos(player, PilotManager.data(player));
			World bodyWorld = Exposure.bodyWorld(player, PilotManager.data(player));
			if (bodyWorld != world || !volume.contains(body)) continue;
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
			player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.surrogate.breach")));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.surrogate.breach.sub", where)));
			player.sendMessage(Text.translatable("message.surrogate.breach", where).formatted(Formatting.RED), false);
		}
	}

	public void sendInfo(ServerPlayerEntity player) {
		Text status;
		if (volume.size == 0) status = Text.translatable("message.surrogate.life_support.blocked");
		else if (volume.sealed) status = Text.translatable("message.surrogate.life_support.sealed", volume.size);
		else status = Text.translatable("message.surrogate.life_support.leak",
				volume.leak == null ? "?" : volume.leak.getX() + ", " + volume.leak.getY() + ", " + volume.leak.getZ());
		player.sendMessage(Text.translatable("message.surrogate.life_support.info",
				Exposure.percent(volume.quality), getEnergyPercent(),
				Text.translatable(volume.powered ? "message.surrogate.life_support.powered" : "message.surrogate.life_support.unpowered"),
				status), false);
		if (volume.isContaminated()) {
			player.sendMessage(Text.translatable("message.surrogate.contamination", volume.dirtyChassis, volume.causticItems, volume.causticBlocks)
					.formatted(Formatting.YELLOW), false);
		}
	}

	private static float perTick(double seconds) {
		return seconds <= 0 ? 1f : (float) (1.0 / (seconds * 20.0));
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		if (world != null) Atmosphere.unregister(world, pos);
	}

	private void sync() {
		markDirty();
		if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putLong("Energy", energy.getAmount());
		nbt.putFloat("Quality", volume.quality);
		nbt.putBoolean("Sealed", volume.sealed);
		nbt.putInt("Volume", volume.size);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		energy.amount = Math.min(nbt.getLong("Energy"), energy.getCapacity());
		volume.quality = MathHelper.clamp(nbt.getFloat("Quality"), 0f, 1f);
		volume.sealed = nbt.getBoolean("Sealed");
		volume.size = nbt.getInt("Volume");
		wasSealed = volume.sealed;
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
