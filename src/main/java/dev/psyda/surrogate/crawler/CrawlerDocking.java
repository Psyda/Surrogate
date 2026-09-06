package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.DockDoorBlock;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.survivor.SurvivorShelter;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Coupling a hull to a collar. Every collar is the lower half of a {@link DockDoorBlock} in a west wall,
 * facing west: the starter pod, Site Two, and every survivor shelter have one. The hull backs onto it, ring
 * first, so it has to face west too. The docking console faces aft: W backs the hull up slowly, A and D
 * swing the ring the way the rear camera shows, and the readout gives how far the ring is from the door and
 * how many degrees off. Inside half a block and ten degrees the tracks creep and the console beeps steady;
 * a click then locks the ring home, snaps the hull square and marks both sides docked. A click anywhere else,
 * or sliding back out of the window, is a clunk. S pulls away, and uncouples when coupled.
 *
 * <p>Whose collar it is matters: coupling at a shelter brings its survivor aboard, coupling at home puts
 * whoever is aboard into the pod.
 */
public final class CrawlerDocking {
	public static final double COUPLE_OFFSET = 0.5;
	public static final float COUPLE_ANGLE = 10f;
	/** How far off a collar the docking console reads it. */
	public static final double COLLAR_RANGE = 16.0;
	/** The hull faces west on every collar. */
	private static final float DOCKED_YAW = 90f;

	/** A collar: its door, and whose it is. */
	public record Collar(BlockPos door, @Nullable SurvivorManager.Site site, boolean home) {
		/** The point the ring has to meet: the middle of the door's outer face, at ring height. */
		public Vec3d target() {
			return new Vec3d(door.getX(), door.getY() + 1.0, door.getZ() + 0.5);
		}
	}

	/** What the docking console remembers between ticks. */
	public static final class Console {
		public boolean wasAligned;
	}

	public record Status(boolean collar, float offset, float angle) {
		public static final Status NONE = new Status(false, 0f, 0f);

		public boolean aligned() {
			return collar && offset <= COUPLE_OFFSET && angle <= COUPLE_ANGLE;
		}
	}

	private CrawlerDocking() {
	}

	/** Where a hull sits when it is on a collar: ring on the door, square to the wall. */
	public static Vec3d dockedPose(BlockPos door, double y) {
		return new Vec3d(door.getX() - CrawlerEntity.HALF_LENGTH - 0.5, y, door.getZ() + 0.5);
	}

	/** The ring on the hull's back, half a block behind the body. */
	public static Vec3d ring(CrawlerEntity hull) {
		return hull.getPos().add(hull.local(0.0, 1.0, -(CrawlerEntity.HALF_LENGTH + 0.5)));
	}

	public static List<Collar> collars(MinecraftServer server) {
		List<Collar> collars = new ArrayList<>();
		HabitatState state = HabitatState.get(server);
		if (state.origin != null) collars.add(new Collar(state.origin.add(HabitatBuilder.DOCK_COLLAR), null, true));
		if (state.siteTwo != null && state.siteTwoBuilt) collars.add(new Collar(state.siteTwo.add(HabitatBuilder.DOCK_COLLAR), null, false));
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			// A wreck on the Rift floor has no collar; whoever is there comes up carried, not driven off.
			if (site.built && !site.wreck) collars.add(new Collar(SurvivorShelter.collar(site), site, false));
		}
		return collars;
	}

	@Nullable
	public static Collar nearest(MinecraftServer server, CrawlerEntity hull, double within) {
		Vec3d ring = ring(hull);
		Collar best = null;
		double bestDistance = within * within;
		for (Collar collar : collars(server)) {
			double d = collar.target().squaredDistanceTo(ring);
			if (d < bestDistance) {
				bestDistance = d;
				best = collar;
			}
		}
		return best;
	}

	public static Status status(MinecraftServer server, CrawlerEntity hull) {
		Collar collar = nearest(server, hull, COLLAR_RANGE);
		if (collar == null) return Status.NONE;
		Vec3d ring = ring(hull);
		Vec3d target = collar.target();
		float offset = (float) Math.hypot(ring.x - target.x, ring.z - target.z);
		float angle = Math.abs(MathHelper.wrapDegrees(hull.getYaw() - DOCKED_YAW));
		return new Status(true, offset, angle);
	}

	/**
	 * One tick of the docking console. {@code throttle} is the key as pressed: W positive, and W backs the
	 * hull up. {@code lock} is the click.
	 */
	public static void tick(MinecraftServer server, CrawlerEntity hull, float throttle, float steer, boolean lock, Console console, ServerPlayerEntity player) {
		if (hull.isDocked()) {
			console.wasAligned = false;
			if (throttle < -0.5f) {
				uncouple(server, hull);
				player.sendMessage(Text.translatable("message.surrogate.crawler.uncoupled").formatted(Formatting.AQUA), true);
			}
			return;
		}
		Status status = status(server, hull);
		boolean aligned = status.aligned();
		// The console faces aft: W drives the hull backwards and A swings the ring to the left of the rear
		// camera. Inside the window the tracks only creep, so there is time to lock.
		float creep = aligned ? 0.1f : 0.35f;
		hull.setControls(-throttle * creep, -steer * (aligned ? 0.5f : 1f));
		if (lock) {
			Collar collar = aligned ? nearest(server, hull, COLLAR_RANGE) : null;
			if (collar != null) {
				couple(server, hull, collar);
				player.sendMessage(Text.translatable("message.surrogate.crawler.coupled").formatted(Formatting.AQUA), true);
			} else {
				cabinSound(server, hull, ModSounds.DOCK_ERROR, 0.9f, 1f);
				player.sendMessage(Text.translatable("message.surrogate.crawler.lock_refused").formatted(Formatting.GRAY), true);
			}
		} else if (console.wasAligned && !aligned) {
			cabinSound(server, hull, ModSounds.DOCK_ERROR, 0.7f, 0.8f);
			player.sendMessage(Text.translatable("message.surrogate.crawler.slipped").formatted(Formatting.GRAY), true);
		}
		console.wasAligned = aligned;
	}

	/** A sound heard inside the hull: played at the docking console of its cabin, for everyone aboard. */
	public static void cabinSound(MinecraftServer server, CrawlerEntity hull, SoundEvent sound, float volume, float pitch) {
		ServerWorld cabin = CrawlerDimension.world(server);
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).forHull(hull.getUuid());
		if (cabin == null || slot == null || !slot.built) return;
		BlockPos at = CrawlerInteriors.origin(slot.index).add(CrawlerRoom.DOCK_STAND);
		cabin.playSound(null, at, sound, SoundCategory.BLOCKS, volume, pitch);
	}

	public static void couple(MinecraftServer server, CrawlerEntity hull, Collar collar) {
		ServerWorld world = server.getOverworld();
		Vec3d pose = dockedPose(collar.door, hull.getY());
		hull.refreshPositionAndAngles(pose.x, pose.y, pose.z, DOCKED_YAW, 0f);
		hull.setVelocity(Vec3d.ZERO);
		hull.setDocked(true);
		DockDoorBlock.setDocked(world, collar.door, true);
		world.playSound(null, collar.door, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.7f, 0.5f);
		cabinSound(server, hull, ModSounds.DOCK_LOCK, 1f, 1f);
		Surrogate.LOGGER.info("Crawler coupled to the collar at {}", collar.door.toShortString());
		SurvivorManager survivors = SurvivorManager.get(server);
		if (collar.site != null) survivors.board(server, hull, collar.site);
		else if (collar.home) survivors.disembark(server, hull, collar.door);
	}

	public static void uncouple(MinecraftServer server, CrawlerEntity hull) {
		ServerWorld world = server.getOverworld();
		hull.setDocked(false);
		for (Collar collar : collars(server)) {
			if (collar.target().squaredDistanceTo(ring(hull)) < 9.0) DockDoorBlock.setDocked(world, collar.door, false);
		}
		world.playSound(null, hull.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.7f, 0.6f);
		cabinSound(server, hull, SoundEvents.BLOCK_IRON_DOOR_CLOSE, 0.7f, 0.6f);
	}

	/** The hull coupled to this collar, if one is. */
	@Nullable
	public static CrawlerEntity hullAt(ServerWorld world, BlockPos door) {
		Vec3d target = new Collar(door, null, false).target();
		CrawlerEntity best = null;
		double bestDistance = 16.0;
		for (CrawlerEntity hull : world.getEntitiesByClass(CrawlerEntity.class, new net.minecraft.util.math.Box(door).expand(12.0), CrawlerEntity::isDocked)) {
			double d = ring(hull).squaredDistanceTo(target);
			if (d < bestDistance) {
				bestDistance = d;
				best = hull;
			}
		}
		return best;
	}
}
