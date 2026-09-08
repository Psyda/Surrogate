package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The pocket of mist on the floor of the Rift, around Novak's wreck.
 *
 * <p>Novak's own damage log says what it is: it came up the chasm at night and went back down at dawn for
 * eight days, and on day nine it did not go down. So it is not weather any more and it has no clock; it is
 * simply the bottom of the Rift now, and it eats machines.
 *
 * <p>It has to be its own sweep. The belt's rain wants the belt, an open sky and a rain clock, and the wreck
 * is under an overhang in a chasm that is not the belt, so it fails three of those four. What it deliberately
 * does <em>not</em> do is touch a body: a person on foot down there is on the same exposure clock as anywhere
 * else on Sallow, which is a rebreather and sixty seconds, and that rule was written on day four of the
 * voyage out. Ceramic cladding and acid coating are no help — a machine that goes down there is lost, and
 * that is the whole reason the last person on the planet has to be fetched by hand.
 */
public final class RiftMist {
	/** How often the pocket is looked at. Short, because the point of it is that it is quick. */
	private static final int SWEEP = 20;
	private static final double SWEEP_SECONDS = SWEEP / 20.0;
	/** How far the mist reaches from the wreck, and how far up the walls it lies. */
	public static final int RADIUS = 22;
	public static final int CEILING = 14;
	/** How far a player has to be for the pocket to be worth looking at at all. */
	private static final double INTEREST = 96.0;

	/** Who has already been told this visit, so the warning is a warning and not a drum. */
	private static final Set<UUID> WARNED = new HashSet<>();

	private RiftMist() {
	}

	public static void reset() {
		WARNED.clear();
	}

	/** The wreck at the bottom, or null on a world where Novak was never placed or never found. */
	@Nullable
	public static BlockPos wreck(MinecraftServer server) {
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			if (site.survivor() == Survivor.NOVAK && site.built) return site.origin();
		}
		return null;
	}

	/** Whether {@code pos} is in the mist: inside the pocket, and not up on the rim above it. */
	public static boolean inMist(MinecraftServer server, BlockPos pos) {
		BlockPos wreck = wreck(server);
		return wreck != null && inMist(wreck, pos);
	}

	private static boolean inMist(BlockPos wreck, BlockPos pos) {
		if (pos.getY() > wreck.getY() + CEILING) return false;
		double dx = pos.getX() - wreck.getX();
		double dz = pos.getZ() - wreck.getZ();
		return dx * dx + dz * dz <= (double) RADIUS * RADIUS;
	}

	public static void tick(MinecraftServer server) {
		if (!Surrogate.CONFIG.hazards || !Surrogate.CONFIG.riftMist) return;
		ServerWorld world = server.getOverworld();
		if (world.getTime() % SWEEP != 0) return;
		BlockPos wreck = wreck(server);
		if (wreck == null) return;
		// Nobody near enough for it to matter costs one distance check per player and nothing else.
		boolean anyone = false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getServerWorld() != world) continue;
			if (player.getBlockPos().isWithinDistance(wreck, INTEREST)) {
				anyone = true;
				break;
			}
		}
		if (!anyone) return;

		Box pocket = new Box(wreck).expand(RADIUS, CEILING, RADIUS);
		float chassisDamage = (float) (Surrogate.CONFIG.riftMistChassisDamagePerSecond * SWEEP_SECONDS);
		for (RobotEntity robot : world.getEntitiesByClass(RobotEntity.class, pocket, RobotEntity::isAlive)) {
			if (!inMist(wreck, robot.getBlockPos())) continue;
			// No factor for a coating. The design's line is that a hull cannot reach him, and a module that
			// quietly quadrupled the survival time would make that line untrue without saying so anywhere.
			robot.setContamination(1f);
			robot.damage(ModDamageTypes.acid(world), chassisDamage);
			ServerPlayerEntity pilot = robot.getPilotPlayer();
			if (pilot != null) warn(pilot, "message.surrogate.mist.chassis");
		}
		// Corrosion, not damage: nothing on Sallow dents a crawler and CrawlerEntity.damage says so by
		// returning false to everything, so a mist that called damage() on one did exactly nothing. What a
		// hull actually feels is the belt's own wear, and down here it comes on fast.
		double hullWear = Surrogate.CONFIG.riftMistHullWearPerSecond * SWEEP_SECONDS;
		for (CrawlerEntity hull : world.getEntitiesByClass(CrawlerEntity.class, pocket, CrawlerEntity::isAlive)) {
			if (!inMist(wreck, hull.getBlockPos())) continue;
			AcidRain.corrode(server, hull, hullWear);
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getServerWorld() != world) continue;
			if (!inMist(wreck, player.getBlockPos())) {
				WARNED.remove(player.getUuid());
				continue;
			}
			// The body is not damaged here at all. It is on Sallow's own exposure clock, which the mist
			// neither helps nor hurts, and a rebreather is worth exactly the sixty seconds it says on it.
			warn(player, "message.surrogate.mist.body");
			world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.6, player.getZ(), 24, 3.0, 1.2, 3.0, 0.01);
		}
	}

	private static void warn(ServerPlayerEntity player, String key) {
		if (!WARNED.add(player.getUuid())) return;
		player.sendMessage(Text.translatable(key).formatted(Formatting.RED), false);
		player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 0.5f);
	}
}
