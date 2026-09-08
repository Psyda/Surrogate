package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Something to sit on: an invisible, weightless entity parked at cushion height that a player rides.
 *
 * <p>The couch, the stools and the office chair in the flashback all want a player to be able to sit down,
 * and vanilla only knows how to sit on things that are entities. So this is one: it has no body, no
 * gravity and no opinions, it lives exactly as long as somebody is on it, and when they get up (or the
 * chair under it is taken away) it removes itself.
 */
public class SeatEntity extends Entity {
	/** The player's hips sit this far below the entity, which is where vanilla puts a rider's seat. */
	private static final double RIDE_DROP = 0.0;

	public SeatEntity(EntityType<? extends SeatEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
		setInvisible(true);
	}

	/**
	 * Sits {@code player} at {@code pos}, facing {@code yaw}. Any seat already at that spot is reused so two
	 * clicks do not stack two of them, and the player is turned to face the right way before they are put
	 * down, because turning them afterwards is the client's business and it will not be told.
	 */
	@Nullable
	public static SeatEntity sit(ServerWorld world, Vec3d pos, float yaw, ServerPlayerEntity player) {
		SeatEntity seat = null;
		List<SeatEntity> here = world.getEntitiesByClass(SeatEntity.class, new Box(pos, pos).expand(0.3), s -> true);
		if (!here.isEmpty()) seat = here.get(0);
		if (seat != null && seat.hasPassengers()) return null;
		if (seat == null) {
			seat = ModEntities.SEAT.create(world);
			if (seat == null) return null;
			seat.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
			world.spawnEntity(seat);
		}
		if (player.hasVehicle()) player.stopRiding();
		player.teleport(world, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, 0f);
		player.setYaw(yaw);
		player.setHeadYaw(yaw);
		player.setBodyYaw(yaw);
		if (!player.startRiding(seat, true)) {
			seat.discard();
			return null;
		}
		return seat;
	}

	/** Whether {@code player} is sitting on one of these. */
	public static boolean isSeated(ServerPlayerEntity player) {
		return player.getVehicle() instanceof SeatEntity;
	}

	/** Gets {@code player} up, if they were sitting on one of these. */
	public static void standUp(ServerPlayerEntity player) {
		if (player.getVehicle() instanceof SeatEntity seat) {
			player.stopRiding();
			seat.discard();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		// Nobody on it, or nothing under it: gone. The block check is what stops a seat hanging in the air
		// after the chair it belonged to has been packed.
		if (!hasPassengers() || getWorld().isAir(BlockPos.ofFloored(getX(), getY() + 0.1, getZ()))
				&& getWorld().isAir(BlockPos.ofFloored(getX(), getY() - 0.4, getZ()))) {
			removeAllPassengers();
			discard();
		}
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3d(0.0, RIDE_DROP, 0.0);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	/** Never saved: a seat with nobody on it is not a thing, and one with somebody on it is rebuilt by a click. */
	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return false;
	}
}
