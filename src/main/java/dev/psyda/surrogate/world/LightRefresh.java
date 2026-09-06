package dev.psyda.surrogate.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Sends a player the server's light for the chunks around a point. A room built the same tick the player
 * arrives in it can reach the client before the light engine has caught up, and the client then keeps the
 * dark it was given until something nearby changes; this hands it the finished light a moment later.
 */
public final class LightRefresh {
	private record Pending(UUID player, RegistryKey<World> dimension, BlockPos center, int radius, int ticks) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private LightRefresh() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(LightRefresh::tick);
	}

	/** Refreshes the chunks within {@code radius} of {@code center}, {@code delay} ticks from now. */
	public static void schedule(ServerPlayerEntity player, RegistryKey<World> dimension, BlockPos center, int radius, int delay) {
		PENDING.add(new Pending(player.getUuid(), dimension, center, radius, Math.max(1, delay)));
	}

	public static void send(ServerPlayerEntity player, ServerWorld world, BlockPos center, int radius) {
		LightingProvider lighting = world.getChunkManager().getLightingProvider();
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				if (!world.isChunkLoaded(x, z)) continue;
				player.networkHandler.sendPacket(new LightUpdateS2CPacket(new ChunkPos(x, z), lighting, null, null));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		Iterator<Pending> iterator = PENDING.iterator();
		List<Pending> later = new ArrayList<>();
		while (iterator.hasNext()) {
			Pending pending = iterator.next();
			iterator.remove();
			if (pending.ticks > 1) {
				later.add(new Pending(pending.player, pending.dimension, pending.center, pending.radius, pending.ticks - 1));
				continue;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.player);
			ServerWorld world = server.getWorld(pending.dimension);
			if (player == null || world == null || player.getWorld() != world) continue;
			send(player, world, pending.center, pending.radius);
		}
		PENDING.addAll(later);
	}
}
