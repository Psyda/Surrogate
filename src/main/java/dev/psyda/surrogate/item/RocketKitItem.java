package dev.psyda.surrogate.item;

import dev.psyda.surrogate.entity.RocketEntity;
import dev.psyda.surrogate.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The sample vehicle in a crate. Unlike the crawler kit there is no {@code useOnBlock}: a rocket goes up off
 * a pad, so the gantry is the only thing that will build one.
 */
public class RocketKitItem extends Item implements VehicleKit {
	/** Fifteen seconds under the gantry; it is a bigger thing than the crawler. */
	public static final int BUILD_TICKS = 300;

	public RocketKitItem(Settings settings) {
		super(settings);
	}

	@Override
	public Box footprint(Vec3d at) {
		return new Box(at.x - RocketEntity.WIDTH / 2.0, at.y, at.z - RocketEntity.WIDTH / 2.0,
				at.x + RocketEntity.WIDTH / 2.0, at.y + RocketEntity.HEIGHT, at.z + RocketEntity.WIDTH / 2.0);
	}

	@Override
	public int buildTicks() {
		return BUILD_TICKS;
	}

	@Override
	@Nullable
	public Entity assemble(ServerWorld world, Vec3d at, float yaw) {
		if (!world.isSpaceEmpty(null, footprint(at))) return null;
		RocketEntity rocket = new RocketEntity(ModEntities.ROCKET, world);
		rocket.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		world.spawnEntity(rocket);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 1.0f, 0.5f);
		return rocket;
	}
}
