package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CrawlerPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The hull's cameras, server side: a colour heightmap of the ground around the hull, read the way the map
 * item reads it, sent to everyone in the cabin a couple of times a second. The client turns it into the
 * grainy view out of the porthole and the rear camera on the docking console.
 */
public final class CrawlerCamera {
	/** Cells each side, one block each: the square is biased ahead of the hull so the porthole sees far. */
	public static final int SIZE = 72;
	private static final int AHEAD = 20;
	private static final int MAX_ENTITIES = 12;

	private CrawlerCamera() {
	}

	public static void send(MinecraftServer server, ServerPlayerEntity player, CrawlerEntity hull) {
		ServerWorld world = server.getOverworld();
		Vec3d pos = hull.getPos();
		Vec3d ahead = hull.local(0.0, 0.0, AHEAD);
		int originX = MathHelper.floor(pos.x + ahead.x) - SIZE / 2;
		int originZ = MathHelper.floor(pos.z + ahead.z) - SIZE / 2;
		int hullY = MathHelper.floor(pos.y);
		byte[] heights = new byte[SIZE * SIZE];
		byte[] colors = new byte[SIZE * SIZE];
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int wx = originX + x;
				int wz = originZ + z;
				int index = z * SIZE + x;
				if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
					heights[index] = Byte.MIN_VALUE;
					continue;
				}
				int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
				cursor.set(wx, surface - 1, wz);
				BlockState state = world.getBlockState(cursor);
				heights[index] = (byte) MathHelper.clamp(surface - hullY, -100, 100);
				colors[index] = (byte) state.getMapColor(world, cursor).id;
			}
		}
		List<Float> entities = new ArrayList<>();
		Box around = new Box(pos.x - SIZE / 2.0, pos.y - 16, pos.z - SIZE / 2.0, pos.x + SIZE / 2.0, pos.y + 16, pos.z + SIZE / 2.0);
		for (Entity entity : world.getOtherEntities(hull, around, e -> e.isAlive() && !(e instanceof ItemEntity))) {
			if (entities.size() >= MAX_ENTITIES * 4) break;
			entities.add((float) (entity.getX() - pos.x));
			entities.add((float) (entity.getY() - pos.y));
			entities.add((float) (entity.getZ() - pos.z));
			entities.add(entity instanceof PlayerEntity ? 1f : entity instanceof RobotEntity ? 2f : 3f);
		}
		// The nearest collar is drawn too, as the frame the rear camera backs into.
		CrawlerDocking.Collar collar = CrawlerDocking.nearest(server, hull, 64.0);
		if (collar != null) {
			Vec3d target = collar.target();
			entities.add((float) (target.x - pos.x));
			entities.add((float) (target.y - pos.y));
			entities.add((float) (target.z - pos.z));
			entities.add(4f);
		}
		float[] packed = new float[entities.size()];
		for (int i = 0; i < packed.length; i++) packed[i] = entities.get(i);
		ServerPlayNetworking.send(player, new CrawlerPayloads.Camera(originX, originZ, SIZE, pos.x, pos.y, pos.z, hull.getYaw(), heights, colors, packed));
	}
}
