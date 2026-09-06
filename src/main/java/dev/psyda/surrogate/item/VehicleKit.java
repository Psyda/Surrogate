package dev.psyda.surrogate.item;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * An item the vehicle fabricator knows how to build: a crate that becomes a hull. The kit describes the room
 * the finished vehicle needs and puts it in the world when the gantry is done; the gantry handles the wait,
 * the light show and the hologram in between.
 */
public interface VehicleKit {
	/** The space the finished vehicle occupies, standing at {@code at} (feet on the ground). */
	Box footprint(Vec3d at);

	/** Ticks the gantry takes to build one. */
	int buildTicks();

	/** Puts the vehicle in the world at {@code at}, facing {@code yaw}. Null when it could not be placed. */
	@Nullable
	Entity assemble(ServerWorld world, Vec3d at, float yaw);
}
