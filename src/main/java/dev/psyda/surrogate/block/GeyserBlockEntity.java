package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The clock behind a geyser. Three quarters of it is a counter running down; the last twelve seconds do the
 * work. Capped by a geothermal tap it stops erupting, which is the whole of the trade: power or sulfur.
 */
public class GeyserBlockEntity extends BlockEntity {
	/** How far a shake carries, in blocks. */
	private static final double SHAKE_RANGE = 24.0;
	/** How far from the vent the eruption is willing to lay crust. */
	private static final int CRUST_RADIUS = 3;

	private int timer;
	private boolean started;
	private boolean capped;

	public GeyserBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GEYSER, pos, state);
	}

	/** True once a tap is bolted on. A capped vent holds at quiet and makes no more sulfur. */
	public boolean isCapped() {
		return capped;
	}

	/**
	 * Try to cap the vent. Fails while the ground is moving or the column is up, which is the placement rule
	 * the tap is built around.
	 */
	public boolean cap() {
		if (world == null) return false;
		BlockState state = world.getBlockState(pos);
		if (!state.contains(GeyserBlock.STAGE)) return false;
		GeyserBlock.Stage stage = state.get(GeyserBlock.STAGE);
		if (stage == GeyserBlock.Stage.RUMBLE || stage == GeyserBlock.Stage.ERUPTING) return false;
		capped = true;
		timer = Math.max(timer, 1);
		markDirty();
		return true;
	}

	/** The tap has come off. The clock picks up where it left off. */
	public void uncap() {
		if (!capped) return;
		capped = false;
		markDirty();
	}

	public void sendInfo(ServerPlayerEntity player) {
		if (capped) {
			player.sendMessage(Text.translatable("message.surrogate.geyser.capped"), false);
			return;
		}
		GeyserBlock.Stage stage = getCachedState().get(GeyserBlock.STAGE);
		if (stage == GeyserBlock.Stage.QUIET) {
			player.sendMessage(Text.translatable("message.surrogate.geyser.quiet", Math.max(1, timer / 20)), false);
			return;
		}
		player.sendMessage(Text.translatable("message.surrogate.geyser." + stage.asString()), false);
	}

	public static void tick(World world, BlockPos pos, BlockState state, GeyserBlockEntity geyser) {
		GeyserBlock.Stage stage = state.get(GeyserBlock.STAGE);
		if (geyser.timer > 0) {
			geyser.timer--;
			// Three quarters of the cycle is quiet, and quiet is a decrement and nothing else.
			if (stage == GeyserBlock.Stage.QUIET) return;
		}
		if (!(world instanceof ServerWorld server)) return;
		if (geyser.timer > 0) {
			geyser.work(server, pos, stage);
			return;
		}
		geyser.advance(server, pos, stage);
	}

	/** What the vent does on a tick that is not quiet. Only the column ever looks for entities. */
	private void work(ServerWorld world, BlockPos pos, GeyserBlock.Stage stage) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		switch (stage) {
			case STEAM -> {
				if (timer % 10 == 0) world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 2, 0.15, 0.1, 0.15, 0.02);
			}
			case RUMBLE -> {
				if (timer % 4 == 0) {
					world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 4, 0.3, 0.2, 0.3, 0.05);
					world.spawnParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.0, 0.2, 0.0);
				}
				// The shake decays on its own, so it is re-sent as the four seconds run out and it builds.
				if (timer % 20 == 0) {
					int rumble = Math.max(1, Surrogate.CONFIG.geyserRumbleTicks);
					shake(world, pos, 0.12f + 0.38f * (1f - (float) timer / rumble), 30);
				}
			}
			case ERUPTING -> {
				if (timer % 2 == 0) column(world, pos);
				if (timer % 5 == 0) scald(world, pos);
				if (timer == Surrogate.CONFIG.geyserEruptTicks / 2) {
					world.playSound(null, pos, ModSounds.GEYSER_ERUPT, SoundCategory.BLOCKS, 2.4f, 1.05f);
				}
			}
			default -> {
			}
		}
	}

	/** The end of a stage. Everything that only happens once per cycle happens here. */
	private void advance(ServerWorld world, BlockPos pos, GeyserBlock.Stage stage) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		if (!started) {
			// A field of these should not breathe in unison, so each starts somewhere inside its own quiet.
			started = true;
			timer = 1 + world.getRandom().nextInt(Math.max(2, cfg.geyserQuietTicks));
			if (stage != GeyserBlock.Stage.QUIET) setStage(world, pos, GeyserBlock.Stage.QUIET);
			markDirty();
			return;
		}
		if (!running(world)) {
			// Capped, switched off, or somewhere that is not Sallow. Sit at quiet and look again in a minute.
			timer = Math.max(20, cfg.geyserQuietTicks);
			if (stage != GeyserBlock.Stage.QUIET) setStage(world, pos, GeyserBlock.Stage.QUIET);
			markDirty();
			return;
		}
		switch (stage) {
			case QUIET -> {
				timer = Math.max(1, cfg.geyserSteamTicks);
				setStage(world, pos, GeyserBlock.Stage.STEAM);
				world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 0.7f);
			}
			case STEAM -> {
				timer = Math.max(1, cfg.geyserRumbleTicks);
				setStage(world, pos, GeyserBlock.Stage.RUMBLE);
				world.playSound(null, pos, ModSounds.GEYSER_RUMBLE, SoundCategory.BLOCKS, 1.6f, 1.0f);
				shake(world, pos, 0.12f, 30);
			}
			case RUMBLE -> {
				timer = Math.max(1, cfg.geyserEruptTicks);
				setStage(world, pos, GeyserBlock.Stage.ERUPTING);
				world.playSound(null, pos, ModSounds.GEYSER_ERUPT, SoundCategory.BLOCKS, 2.6f, 1.0f);
				shake(world, pos, 0.7f, 40);
				world.spawnParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
			}
			case ERUPTING -> {
				timer = Math.max(1, cfg.geyserQuietTicks);
				setStage(world, pos, GeyserBlock.Stage.QUIET);
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.2f, 0.6f);
				layCrust(world, pos);
			}
		}
		markDirty();
	}

	/** The hazard layer is on, the tap is off, and this is the planet the clock belongs to. */
	private boolean running(ServerWorld world) {
		return Surrogate.CONFIG.hazards && !capped && Valleys.isMesaWorld(world);
	}

	private void setStage(ServerWorld world, BlockPos pos, GeyserBlock.Stage stage) {
		BlockState state = world.getBlockState(pos);
		if (!state.contains(GeyserBlock.STAGE)) return;
		if (state.get(GeyserBlock.STAGE) == stage) return;
		world.setBlockState(pos, state.with(GeyserBlock.STAGE, stage), Block.NOTIFY_ALL);
	}

	/** The column itself: eight seconds of scalding water where a chassis used to be standing. */
	private void column(ServerWorld world, BlockPos pos) {
		int height = Math.max(1, Surrogate.CONFIG.geyserHeight);
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		double middle = pos.getY() + 1.0 + height / 2.0;
		world.spawnParticles(ParticleTypes.CLOUD, x, middle, z, 8, 0.18, height / 2.0, 0.18, 0.05);
		world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, pos.getY() + 1.2, z, 2, 0.1, 0.0, 0.1, 0.35);
		world.spawnParticles(ParticleTypes.LAVA, x, pos.getY() + 1.1, z, 1, 0.15, 0.0, 0.15, 0.0);
	}

	/** Anything standing in the column is burned and thrown out of the top of it. */
	private void scald(ServerWorld world, BlockPos pos) {
		int height = Math.max(1, Surrogate.CONFIG.geyserHeight);
		Box column = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0 + height, pos.getZ() + 1.0);
		List<Entity> caught = world.getOtherEntities(null, column, entity -> entity.isAlive() && !entity.isSpectator());
		if (caught.isEmpty()) return;
		DamageSource source = ModDamageTypes.geyser(world);
		float damage = Surrogate.CONFIG.geyserDamage;
		for (Entity entity : caught) {
			// A hull was built for this planet and a person was not, so a body takes the column three times over.
			entity.damage(source, entity instanceof RobotEntity ? damage : damage * 3f);
			entity.addVelocity(0.0, 0.9, 0.0);
			entity.velocityModified = true;
		}
	}

	/** What the eruption leaves behind, which is why a geyser field is worth working to a clock. */
	private void layCrust(ServerWorld world, BlockPos pos) {
		Random random = world.getRandom();
		int laid = 0;
		for (int attempt = 0; attempt < 6 && laid < 2; attempt++) {
			if (random.nextInt(2) != 0) continue;
			int dx = random.nextInt(CRUST_RADIUS * 2 + 1) - CRUST_RADIUS;
			int dz = random.nextInt(CRUST_RADIUS * 2 + 1) - CRUST_RADIUS;
			if (dx == 0 && dz == 0) continue;
			BlockPos spot = ground(world, pos.add(dx, 0, dz));
			if (spot == null) continue;
			world.setBlockState(spot, ModBlocks.SULFUR_CRUST.getDefaultState(), Block.NOTIFY_ALL);
			laid++;
		}
	}

	/**
	 * The first free space with something solid under it, within a block of the vent's own lip. Deliberately
	 * short: this runs once a cycle and must never go looking for the surface.
	 */
	@Nullable
	private static BlockPos ground(ServerWorld world, BlockPos near) {
		if (!world.isChunkLoaded(near.getX() >> 4, near.getZ() >> 4)) return null;
		for (int dy = 1; dy >= -1; dy--) {
			BlockPos spot = near.up(dy);
			if (!world.getBlockState(spot).isReplaceable()) continue;
			BlockState below = world.getBlockState(spot.down());
			// Crust never stacks on crust, or a field grows a tower instead of a floor.
			if (below.isOf(ModBlocks.SULFUR_CRUST)) continue;
			if (!below.isSideSolidFullSquare(world, spot.down(), Direction.UP)) continue;
			return spot;
		}
		return null;
	}

	/** Shake the camera of anyone close enough to feel it, falling off with distance. */
	private void shake(ServerWorld world, BlockPos pos, float strength, int ticks) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		for (ServerPlayerEntity player : world.getPlayers()) {
			double squared = player.squaredDistanceTo(x, y, z);
			if (squared > SHAKE_RANGE * SHAKE_RANGE) continue;
			float falloff = (float) (1.0 - Math.sqrt(squared) / SHAKE_RANGE);
			ServerPlayNetworking.send(player, new CinematicPayloads.Effect(CinematicPayloads.EFFECT_SHAKE, strength * falloff, ticks));
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putInt("Timer", timer);
		nbt.putBoolean("Started", started);
		nbt.putBoolean("Capped", capped);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		timer = nbt.getInt("Timer");
		started = nbt.getBoolean("Started");
		capped = nbt.getBoolean("Capped");
	}
}
