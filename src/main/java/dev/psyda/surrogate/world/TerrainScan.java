package dev.psyda.surrogate.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survivor.SurvivorManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev tooling for the mesa valleys: {@code /surrogate terrain scan} walks a straight line from the pod to
 * each site through the chunk generator (no chunks are loaded) and counts the steps a crawler could not take;
 * {@code /surrogate terrain map [radius] [step]} paints the valleys, the acid, the sites and the ground the
 * pod can reach to {@code terrain_map.png} in the run directory; {@code /surrogate terrain here} says what
 * the ground under the caller is. Driven headlessly by tools/terrain_scan.py.
 */
public final class TerrainScan {
	/** A crawler treats a one-block step as a ramp and anything taller as a wall. */
	public static final int WALL = 2;

	private TerrainScan() {
	}

	/**
	 * What a crawler would meet driving a line: {@code length} columns, the steps of {@link #WALL} or more,
	 * the acid it would have to ford, the columns off the floor. {@code straight} is the crow-flight distance
	 * between the ends; {@code path} is null when there was no drive to check and the line is straight.
	 */
	public record Profile(String name, int fromX, int fromZ, int toX, int toZ, int straight, int length, int walls, int longestRun, int water, int climbs,
						  int minY, int maxY, @Nullable List<BlockPos> path) {
		/** No step a crawler could not take, no acid, and never off the floor. */
		public boolean drivable() {
			return path != null && walls == 0 && water == 0 && climbs == 0;
		}

		public String describe() {
			String route = path == null ? "no drive found, straight line" : "drive of " + length + " blocks";
			return String.format("%s: %d blocks from %d,%d to %d,%d, %s; %d walls (longest clear run %d), %d acid columns, %d off the floor, ground %d..%d: %s",
					name, straight, fromX, fromZ, toX, toZ, route, walls, longestRun, water, climbs, minY, maxY, drivable() ? "DRIVABLE" : "blocked");
		}
	}

	public record Marker(String name, int x, int z, int rgb) {
	}

	/** Reads the ground along a polyline, one column per block along each leg's longer axis. */
	public static Profile profile(ServerWorld world, String name, List<BlockPos> points, @Nullable List<BlockPos> path) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig config = world.getChunkManager().getNoiseConfig();
		int previous = Integer.MIN_VALUE;
		int length = 0;
		int walls = 0;
		int water = 0;
		int climbs = 0;
		int run = 0;
		int longest = 0;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int leg = 0; leg + 1 < points.size(); leg++) {
			BlockPos a = points.get(leg);
			BlockPos b = points.get(leg + 1);
			int dx = b.getX() - a.getX();
			int dz = b.getZ() - a.getZ();
			int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
			for (int i = leg == 0 ? 0 : 1; i <= steps; i++) {
				int x = a.getX() + Math.round((float) dx * i / steps);
				int z = a.getZ() + Math.round((float) dz * i / steps);
				length++;
				int ground = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, config);
				int surface = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, config);
				if (surface > ground) water++;
				Valleys.Kind kind = Valleys.classify(world, x, z);
				if (kind == Valleys.Kind.MESA || kind == Valleys.Kind.CLIFF) climbs++;
				if (previous != Integer.MIN_VALUE && Math.abs(ground - previous) >= WALL) {
					walls++;
					longest = Math.max(longest, run);
					run = 0;
				} else {
					run++;
				}
				previous = ground;
				minY = Math.min(minY, ground);
				maxY = Math.max(maxY, ground);
			}
		}
		longest = Math.max(longest, run);
		BlockPos from = points.get(0);
		BlockPos to = points.get(points.size() - 1);
		int straight = (int) Math.round(Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()));
		return new Profile(name, from.getX(), from.getZ(), to.getX(), to.getZ(), straight, length, walls, longest, water, climbs, minY, maxY, path);
	}

	/**
	 * The drive from the pod to a site: the reachability grid's path when it has one (the site's own column
	 * spliced onto its ends), otherwise the straight line, which is then reported as no drive.
	 */
	public static Profile drive(ServerWorld world, String name, @Nullable Valleys.Reach reach, BlockPos from, int toX, int toZ) {
		List<BlockPos> path = reach == null ? null : reach.pathTo(toX, toZ);
		List<BlockPos> points = new ArrayList<>();
		points.add(new BlockPos(from.getX(), 0, from.getZ()));
		if (path != null) points.addAll(path);
		points.add(new BlockPos(toX, 0, toZ));
		return profile(world, name, points, path);
	}

	/** The pod to Site Two and to every shelter, along the drives the reachability grid finds. */
	public static List<Profile> scanSites(MinecraftServer server) {
		List<Profile> profiles = new ArrayList<>();
		ServerWorld world = server.getOverworld();
		HabitatState state = HabitatState.get(server);
		if (state.origin == null) return profiles;
		Valleys.Reach reach = Valleys.reach(world, state.origin.getX(), state.origin.getZ(), reachRadius(server, state));
		if (state.siteTwo != null) profiles.add(drive(world, "site02", reach, state.origin, state.siteTwo.getX(), state.siteTwo.getZ()));
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			profiles.add(drive(world, site.survivor().key(), reach, state.origin, site.x, site.z));
		}
		return profiles;
	}

	/** Far enough to hold every site, with room for a drive around a table. */
	private static int reachRadius(MinecraftServer server, HabitatState state) {
		int radius = 256;
		if (state.siteTwo != null) radius = Math.max(radius, Math.max(Math.abs(state.siteTwo.getX() - state.origin.getX()), Math.abs(state.siteTwo.getZ() - state.origin.getZ())));
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			radius = Math.max(radius, Math.max(Math.abs(site.x - state.origin.getX()), Math.abs(site.z - state.origin.getZ())));
		}
		return radius + 128;
	}

	public static List<Marker> markers(MinecraftServer server) {
		List<Marker> markers = new ArrayList<>();
		HabitatState state = HabitatState.get(server);
		if (state.origin != null) markers.add(new Marker("pod", state.origin.getX(), state.origin.getZ(), 0xFFFFFF));
		if (state.siteTwo != null) markers.add(new Marker("site02", state.siteTwo.getX(), state.siteTwo.getZ(), 0x00FFFF));
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			markers.add(new Marker(site.survivor().key(), site.x, site.z, 0xFF40FF));
		}
		return markers;
	}

	/**
	 * Paints the ground around a point from the height field (no caves, so it is quick): acid in green,
	 * floors in sand (grey where the pod cannot reach them), cliffs dark, tables brown and lighter with
	 * height, the drives to the sites as lines and the sites as crosses. Returns a one-line summary.
	 */
	public static String map(MinecraftServer server, int centerX, int centerZ, int radius, int step, List<Marker> markers, @Nullable Valleys.Reach reach,
							 List<Profile> drives, Path out) throws IOException {
		ServerWorld world = server.getOverworld();
		int seaLevel = world.getSeaLevel();
		int size = radius * 2 / step + 1;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		long[] counts = new long[Valleys.Kind.values().length];
		long floors = 0;
		long reachable = 0;
		for (int py = 0; py < size; py++) {
			int z = centerZ - radius + py * step;
			for (int px = 0; px < size; px++) {
				int x = centerX - radius + px * step;
				int ground = (int) Math.floor(Valleys.surfaceHeight(world, x, z));
				Valleys.Kind kind = Valleys.classify(world, x, z);
				counts[kind.ordinal()]++;
				int rgb;
				if (ground < seaLevel) {
					float depth = Math.min(1f, (seaLevel - ground) / 10f);
					rgb = shade(0x3FA070, 1f - 0.5f * depth);
				} else {
					switch (kind) {
						case MESA -> rgb = shade(0xA0683C, 0.7f + 0.3f * clamp01((ground - 80) / 30f));
						case CLIFF -> rgb = 0x5A3A22;
						case RIVER -> rgb = shade(0x8FA080, 0.8f);
						default -> rgb = shade(0xD8C890, 0.75f + 0.25f * clamp01((ground - 60) / 10f));
					}
					if (kind == Valleys.Kind.FLOOR) {
						floors++;
						if (reach == null || reach.contains(x, z)) reachable++;
						else rgb = shade(0x9A9A9A, 0.75f + 0.25f * clamp01((ground - 60) / 10f));
					}
				}
				image.setRGB(px, py, rgb);
			}
		}
		for (Profile drive : drives) {
			if (drive.path == null) continue;
			int rgb = drive.drivable() ? 0x2060FF : 0xFF2020;
			for (int i = 0; i + 1 < drive.path.size(); i++) {
				BlockPos a = drive.path.get(i);
				BlockPos b = drive.path.get(i + 1);
				int steps = Math.max(1, Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) / Math.max(1, step) + 1);
				for (int s = 0; s <= steps; s++) {
					int x = a.getX() + (b.getX() - a.getX()) * s / steps;
					int z = a.getZ() + (b.getZ() - a.getZ()) * s / steps;
					plot(image, (x - centerX + radius) / step, (z - centerZ + radius) / step, rgb);
				}
			}
		}
		for (Marker marker : markers) {
			int px = (marker.x - centerX + radius) / step;
			int py = (marker.z - centerZ + radius) / step;
			for (int d = -4; d <= 4; d++) {
				plot(image, px + d, py, marker.rgb);
				plot(image, px, py + d, marker.rgb);
			}
			for (int d = -1; d <= 1; d++) {
				for (int e = -1; e <= 1; e++) plot(image, px + d, py + e, 0x000000);
			}
			plot(image, px, py, marker.rgb);
		}
		ImageIO.write(image, "png", out.toFile());
		long total = (long) size * size;
		StringBuilder summary = new StringBuilder();
		summary.append(String.format("%dx%d blocks at step %d around %d,%d: ", radius * 2, radius * 2, step, centerX, centerZ));
		for (Valleys.Kind kind : Valleys.Kind.values()) {
			summary.append(String.format("%s %.0f%%, ", kind.name().toLowerCase(), 100.0 * counts[kind.ordinal()] / total));
		}
		summary.append(String.format("floor reachable from the pod %.0f%%; wrote %s", floors == 0 ? 0.0 : 100.0 * reachable / floors, out));
		return summary.toString();
	}

	private static float clamp01(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static int shade(int rgb, float factor) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
		return (r << 16) | (g << 8) | b;
	}

	private static void plot(BufferedImage image, int x, int y, int rgb) {
		if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) image.setRGB(x, y, rgb);
	}

	// ------------------------------------------------------------------ commands

	public static LiteralArgumentBuilder<ServerCommandSource> command() {
		return CommandManager.literal("terrain")
				.then(CommandManager.literal("scan").executes(context -> scan(context.getSource())))
				.then(CommandManager.literal("here").executes(context -> here(context.getSource())))
				.then(CommandManager.literal("map")
						.executes(context -> map(context.getSource(), 1024, 8))
						.then(CommandManager.argument("radius", IntegerArgumentType.integer(64, 8192))
								.executes(context -> map(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), 8))
								.then(CommandManager.argument("step", IntegerArgumentType.integer(1, 64))
										.executes(context -> map(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "step"))))));
	}

	private static int scan(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		if (HabitatState.get(server).origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		List<Profile> profiles = scanSites(server);
		int blocked = 0;
		for (Profile profile : profiles) {
			String line = profile.describe();
			Surrogate.LOGGER.info("Terrain {}", line);
			source.sendFeedback(() -> Text.literal(line), false);
			if (!profile.drivable()) blocked++;
		}
		String verdict = profiles.isEmpty() ? "Terrain: no sites to scan"
				: blocked == 0 ? "Terrain: every site is drivable from the pod (" + profiles.size() + " runs)"
				: "Terrain: " + blocked + " of " + profiles.size() + " runs blocked";
		Surrogate.LOGGER.info(verdict);
		source.sendFeedback(() -> Text.literal(verdict), false);
		return blocked == 0 ? 1 : 0;
	}

	private static int here(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		BlockPos pos = BlockPos.ofFloored(source.getPosition());
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig config = world.getChunkManager().getNoiseConfig();
		int ground = generator.getHeight(pos.getX(), pos.getZ(), Heightmap.Type.OCEAN_FLOOR_WG, world, config);
		String line = String.format("%s at %d,%d: continents %.2f, river %.2f, ground %d%s", Valleys.classify(world, pos.getX(), pos.getZ()),
				pos.getX(), pos.getZ(), Valleys.continents(world, pos.getX(), pos.getZ()), Valleys.river(world, pos.getX(), pos.getZ()), ground,
				Valleys.isMesaWorld(world) ? "" : " (not a mesa world)");
		source.sendFeedback(() -> Text.literal(line), false);
		return 1;
	}

	private static int map(ServerCommandSource source, int radius, int step) {
		MinecraftServer server = source.getServer();
		HabitatState state = HabitatState.get(server);
		BlockPos center = state.origin != null ? state.origin : BlockPos.ofFloored(source.getPosition());
		ServerWorld world = server.getOverworld();
		Valleys.Reach reach = state.origin == null ? null : Valleys.reach(world, center.getX(), center.getZ(), radius);
		List<Profile> drives = state.origin == null ? List.of() : scanSites(server);
		Path out = server.getRunDirectory().resolve("terrain_map.png");
		try {
			String summary = map(server, center.getX(), center.getZ(), radius, step, markers(server), reach, drives, out);
			Surrogate.LOGGER.info("Terrain map {}", summary);
			source.sendFeedback(() -> Text.literal(summary), false);
			return 1;
		} catch (IOException e) {
			source.sendError(Text.literal("Could not write the map: " + e.getMessage()));
			return 0;
		}
	}
}
