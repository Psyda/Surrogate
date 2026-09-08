package dev.psyda.surrogate.hazard;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.network.HazardPayload;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The weather on Sallow, and the clock behind it. A magnetic storm gives ninety seconds of warning, holds at
 * full strength for a quarter of an hour and then takes an age to fade; while it is up the sun is worth
 * almost nothing, the band is noise, and an unsheltered uplink tears itself apart. The belt keeps its own rain
 * clock alongside it. Everything here is off when {@code hazards} is off, and nothing outside the Toxic Wastes
 * ever pays for it.
 */
public final class Hazards {
	/** Intensity past which the band is nothing but carrier and hiss. */
	public static final float RADIO_NOISE_AT = 0.4f;
	/** Intensity counted as the peak, where a chassis in the open starts losing hull. */
	private static final float PEAK_AT = 0.95f;
	/** How far from a player a chassis is looked for when the storm bites. Beyond it, nobody would see it. */
	private static final double BITE_RANGE = 64.0;

	// The clock is read far more often than it is written (every solar collector, every tick), so the phase is
	// kept here as three plain fields and the saved state is only touched by the tick that advances it.
	private static float intensity;
	private static boolean warning;
	private static boolean raining;
	private static boolean lastWarning;
	private static boolean lastRaining;
	/** Ticks into the storm that is running, or -1 between them. How long a link has been under it. */
	private static long stormAge = -1L;

	private Hazards() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(Hazards::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	// ------------------------------------------------------------------ asking

	/** Storm intensity where this world is, 0 to 1. Zero everywhere but Sallow, and zero with hazards off. */
	public static float storm(ServerWorld world) {
		if (intensity <= 0f || !Surrogate.CONFIG.hazards) return 0f;
		return onSallow(world) ? intensity : 0f;
	}

	/**
	 * Start a storm now, whatever the clock said. The conference in act four calls this: the sky arriving
	 * mid-scene is the beat, and a scripted flash that the weather did not actually agree to would leave the
	 * player walking out of the pod into a clear afternoon.
	 */
	public static void forceStorm(MinecraftServer server) {
		if (!Surrogate.CONFIG.hazards) return;
		ServerWorld world = server.getOverworld();
		if (!Valleys.isMesaWorld(world)) return;
		HazardState state = HazardState.get(server);
		if (state.stormStart >= 0) return;
		state.stormStart = world.getTime();
		state.markDirty();
		warning = true;
		announce(server, "message.surrogate.storm.warning");
	}

	/** True through the run-up only: the window in which parking is still a decision. */
	public static boolean stormWarning(ServerWorld world) {
		return warning && Surrogate.CONFIG.hazards && onSallow(world);
	}

	/** True while the belt's clock says rain. It says nothing about where you are standing. */
	public static boolean acidRain(ServerWorld world) {
		return raining && Surrogate.CONFIG.hazards
				&& world.getRegistryKey() == World.OVERWORLD && Valleys.isMesaWorld(world);
	}

	/** Raining, in the belt, and open to the sky: the three things the acid needs. */
	public static boolean rainedOn(ServerWorld world, BlockPos pos) {
		if (!acidRain(world)) return false;
		if (!Valleys.inBelt(world, pos.getX(), pos.getZ())) return false;
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
		return world.isSkyVisible(pos.up());
	}

	/** Under a roof that counts: a scrubbed room, a crawler cabin, or standing on your own dock. */
	public static boolean shielded(ServerWorld world, BlockPos pos) {
		if (CrawlerDimension.isCabin(world)) return true;
		if (Atmosphere.volumeAt(world, pos) != null) return true;
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
		for (BlockPos near : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
			if (world.getBlockState(near).isOf(ModBlocks.CHARGING_DOCK)) return true;
		}
		return false;
	}

	/** Past this the bearings scatter and the calls do not come. */
	public static boolean radioNoise(ServerWorld world) {
		return storm(world) >= RADIO_NOISE_AT;
	}

	/** The wastes and the cabins that drive over them. The ship in orbit is somebody else's weather. */
	private static boolean onSallow(ServerWorld world) {
		if (CrawlerDimension.isCabin(world)) return true;
		return world.getRegistryKey() == World.OVERWORLD && Valleys.isMesaWorld(world);
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		if (!Surrogate.CONFIG.hazards) {
			if (intensity != 0f || warning || raining) reset();
			return;
		}
		ServerWorld overworld = server.getOverworld();
		if (!Valleys.isMesaWorld(overworld)) return;

		HazardState state = HazardState.get(server);
		long now = overworld.getTime();
		advanceStorm(server, overworld, state, now);
		advanceRain(overworld, state, now);

		boolean flipped = warning != lastWarning || raining != lastRaining;
		lastWarning = warning;
		lastRaining = raining;
		if (flipped || now % 20 == 0) report(server);
		if (intensity >= PEAK_AT) {
			if (now % 40 == 0) bite(server);
			if (now % 600 == 0) crack(server);
		}
	}

	private static void advanceStorm(MinecraftServer server, ServerWorld world, HazardState state, long now) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		int warn = Math.max(1, cfg.stormWarningTicks);
		int peak = Math.max(1, cfg.stormPeakTicks);
		int tail = Math.max(1, cfg.stormTailTicks);

		if (state.stormStart >= 0) {
			long elapsed = now - state.stormStart;
			stormAge = elapsed;
			if (elapsed < 0 || elapsed >= (long) warn + peak + tail) {
				state.stormStart = -1L;
				state.nextStorm = now + stormGap(world);
				state.markDirty();
				intensity = 0f;
				warning = false;
				stormAge = -1L;
				announce(server, "message.surrogate.storm.clear");
			} else if (elapsed < warn) {
				intensity = (float) elapsed / warn;
				warning = true;
			} else if (elapsed < (long) warn + peak) {
				intensity = 1f;
				warning = false;
			} else {
				intensity = 1f - (float) (elapsed - warn - peak) / tail;
				warning = false;
			}
			return;
		}

		intensity = 0f;
		warning = false;
		stormAge = -1L;
		if (state.nextStorm < 0) {
			state.nextStorm = now + stormGap(world);
			state.markDirty();
			return;
		}
		if (now < state.nextStorm) return;
		// The opening has enough going wrong in it already; the sky waits until the script is done.
		if (Prologue.isRunning()) {
			state.nextStorm = now + 1200L;
			state.markDirty();
			return;
		}
		state.stormStart = now;
		state.markDirty();
		warning = true;
		announce(server, "message.surrogate.storm.warning");
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (onSallow(player.getServerWorld())) player.playSoundToPlayer(ModSounds.STORM_WASH, SoundCategory.WEATHER, 0.7f, 1.0f);
		}
	}

	private static void advanceRain(ServerWorld world, HazardState state, long now) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		if (state.rainStart >= 0) {
			long elapsed = now - state.rainStart;
			if (elapsed < 0 || elapsed >= Math.max(20, cfg.acidRainLengthTicks)) {
				state.rainStart = -1L;
				state.nextRain = now + rainGap(world);
				state.markDirty();
				raining = false;
			} else {
				raining = true;
			}
			return;
		}
		raining = false;
		if (state.nextRain < 0) {
			state.nextRain = now + rainGap(world);
			state.markDirty();
			return;
		}
		if (now < state.nextRain) return;
		state.rainStart = now;
		state.markDirty();
		raining = true;
	}

	private static long stormGap(ServerWorld world) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		int jitter = Math.max(1, cfg.stormIntervalJitterTicks);
		return Math.max(200, cfg.stormIntervalTicks) + world.getRandom().nextInt(jitter);
	}

	private static long rainGap(ServerWorld world) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		int jitter = Math.max(1, cfg.acidRainJitterTicks);
		return Math.max(200, cfg.acidRainIntervalTicks) + world.getRandom().nextInt(jitter);
	}

	private static void reset() {
		intensity = 0f;
		warning = false;
		raining = false;
		lastWarning = false;
		lastRaining = false;
		stormAge = -1L;
	}

	/**
	 * How far into a storm an uplink out in the open lasts: the point on the run-up where the intensity
	 * crosses the drop threshold, and twice that with Reyes' shielding on the chassis.
	 */
	private static long linkHolds(boolean shielded) {
		double at = Math.max(0.0, Math.min(1.0, Surrogate.CONFIG.stormLinkDropAt));
		long ticks = Math.round(Math.max(1, Surrogate.CONFIG.stormWarningTicks) * at);
		return shielded ? ticks * 2L : ticks;
	}

	// ------------------------------------------------------------------ the teeth

	/**
	 * One packet per player: the storm and the rain where their eyes are, the nearest borer, and how much of
	 * the uplink is left. A link that has gone past the drop threshold ends here rather than fading forever.
	 */
	private static void report(MinecraftServer server) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			RobotEntity chassis = chassis(player);
			float noise = 0f;
			boolean dropping = false;
			if (chassis != null) {
				float storm = storm(player.getServerWorld());
				if (storm > 0f && !shielded(player.getServerWorld(), chassis.getBlockPos())) {
					boolean shield = chassis.hasModule(RobotModule.SHIELD);
					// The shield halves what the storm does to the picture and holds the link for twice as
					// long. It is a delay, not an exemption: a factor on the noise alone made a shielded
					// link undroppable, because half of one is under the threshold in any storm there is.
					noise = storm * (shield ? (float) cfg.stormShieldFactor : 1f);
					dropping = storm > cfg.stormLinkDropAt && stormAge >= linkHolds(shield);
				}
			}
			if (dropping) {
				// Never a silent drop: the pilot wakes in the chair and is told why.
				PilotManager.disconnect(player, false, Text.translatable("message.surrogate.storm.link_lost"), true);
				chassis = null;
				noise = 0f;
			}
			// Read the world again: a dropped link has just put this player back in their chair.
			ServerWorld world = player.getServerWorld();
			BlockPos at = chassis != null ? chassis.getBlockPos() : player.getBlockPos();
			Vec3d eyes = chassis != null ? chassis.getPos() : player.getPos();
			ServerPlayNetworking.send(player, new HazardPayload(storm(world), stormWarning(world), rainedOn(world, at),
					Borers.threat(world, eyes), noise));
		}
	}

	/** A chassis standing in it loses hull every couple of seconds. Only the ones a player could be watching. */
	private static void bite(MinecraftServer server) {
		Set<RobotEntity> caught = new HashSet<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerWorld world = player.getServerWorld();
			if (!onSallow(world)) continue;
			Box box = player.getBoundingBox().expand(BITE_RANGE);
			caught.addAll(world.getEntitiesByClass(RobotEntity.class, box, RobotEntity::isAlive));
		}
		for (RobotEntity robot : caught) {
			if (!(robot.getWorld() instanceof ServerWorld world)) continue;
			BlockPos at = robot.getBlockPos();
			if (shielded(world, at) || !world.isSkyVisible(at.up())) continue;
			robot.damage(ModDamageTypes.storm(world), Surrogate.CONFIG.stormChassisDamage);
		}
	}

	/** Something in the upper air letting go, heard through the hull as much as over it. */
	private static void crack(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerWorld world = player.getServerWorld();
			if (!onSallow(world)) continue;
			float pitch = 0.8f + world.getRandom().nextFloat() * 0.4f;
			player.playSoundToPlayer(ModSounds.STORM_CRACK, SoundCategory.WEATHER, 0.6f, pitch);
		}
	}

	private static void announce(MinecraftServer server, String key) {
		Text line = Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(Crew.HALLORAN.displayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable(key).formatted(Formatting.WHITE));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (onSallow(player.getServerWorld())) player.sendMessage(line, false);
		}
	}

	/** The chassis this player is looking through, or null if they are only a body today. */
	@Nullable
	private static RobotEntity chassis(ServerPlayerEntity player) {
		return player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) ? robot : null;
	}
}
