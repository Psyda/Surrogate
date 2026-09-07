package dev.psyda.surrogate.world;

import dev.psyda.surrogate.Surrogate;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

/**
 * Scores candidate seeds for the campaign and ranks them. Sallow has to be one particular map: the pod opens
 * onto a floor worth driving, the six near sites are all reachable by drives with a bend in them, and a Rift
 * cuts a large piece of floor off at the far end with one crossing narrow enough to bridge and enough acid
 * belt behind it to put the last three researchers in (docs/DESIGN-campaign.md, act five). None of that is
 * true of an arbitrary seed, so a few hundred are read with {@link SeedSampler} and the best one is written
 * down.
 *
 * <p>This is a coarse reading: one column per {@link #CELL}-block cell rather than the five the real fill
 * takes, and the sites are stand-ins picked the way the game picks them rather than the game's own picks,
 * which depend on the world's random state. A winner still wants one {@code /surrogate terrain scan} on a
 * real world before it is trusted. It stalls the server while it runs; it is dev tooling.
 */
public final class SeedSearch {
	/** Blocks a scoring cell is across. It is the real fill's cell because the Rift is only twelve wide. */
	public static final int CELL = Valleys.CELL;
	/** How far from the estimated spawn the sweep reads. Act five's far side has to fit inside it. */
	public static final int RADIUS = 2560;
	/** The box {@link Valleys#pickStart} searches for the pod, so the sweep looks in the same one. */
	private static final int POD_SEARCH = 512;
	private static final int RADIUS_CELLS = RADIUS / CELL;
	private static final int SIZE = RADIUS_CELLS * 2 + 1;

	/** Cell codes: a {@link Valleys.Kind} ordinal, or this for floor that is also dead flat. */
	private static final byte DRIVABLE = (byte) Valleys.Kind.values().length;
	private static final byte RIFT = (byte) Valleys.Kind.RIFT.ordinal();
	private static final int UNREACHED = -1;
	private static final int SEED_PARENT = -2;

	/** How many candidates the pod search will pay the full price for before it settles for what it has. */
	private static final int POD_TESTS = 64;
	/** Attempts per site, matching the run the real pickers make. */
	private static final int SITE_ATTEMPTS = 64;
	/** The far side has to be a country, not a ledge. */
	private static final int MIN_FAR_CELLS = 400;
	private static final int FAR_MIN = 900;
	private static final int FAR_MAX = 2200;
	/** Drives worth driving: not a straight line, not a maze. */
	private static final double RATIO_MIN = 1.15;
	private static final double RATIO_MAX = 1.9;
	// A bridge somebody actually builds: five wide, because that is the hull, and long enough to be a job.
	// The first pass of this sweep asked for five to fourteen blocks and every seed scored nothing, because
	// the Rift is not that narrow anywhere and cannot be: the density interpolation works in four-block
	// columns, so a slot thinner than about a dozen blocks comes out as a dip instead of a chasm. Measured,
	// the narrows run sixteen to twenty-five, and that is what a span kit should be sized for.
	private static final double SPAN_MIN = 12.0;
	private static final double SPAN_MAX = 26.0;

	private SeedSearch() {
	}

	/** One named line of a score, so the table says why a seed won and not only that it did. */
	public record Part(String name, double points, double max, String detail) {
	}

	/** What a seed is worth and what it looks like. */
	public record Candidate(long seed, double total, List<Part> parts, @Nullable BlockPos spawn, @Nullable BlockPos pod,
							@Nullable BlockPos crossing, String note) {
		public String row(int rank) {
			StringBuilder line = new StringBuilder(String.format("%2d. seed %-12d %5.1f", rank, seed, total));
			for (Part part : parts) {
				line.append(String.format("  %s %.0f (%s)", part.name(), part.points(), part.detail()));
			}
			if (!note.isEmpty()) line.append("  ").append(note);
			return line.toString();
		}
	}

	// ------------------------------------------------------------------ the sweep

	/**
	 * Scores {@code count} seeds from {@code from} upward, in parallel, and returns them best first. One seed
	 * is about a second of one core.
	 */
	public static List<Candidate> sweep(MinecraftServer server, long from, int count) {
		RegistryEntryLookup.RegistryLookup lookup = server.getRegistryManager().createRegistryLookup();
		ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
		// The sweep blocks the server thread for as long as it runs and says nothing over RCON until it
		// returns, so without this a driver cannot tell a long sweep from a hung one, and neither can anyone
		// tailing the log. One line per tenth of the run is enough to be a deadline rather than a guess.
		AtomicInteger done = new AtomicInteger();
		int step = Math.max(1, count / 10);
		Surrogate.LOGGER.info("Seeds: sweeping {} from {}, {}", count, from, fidelity());
		Callable<List<Candidate>> work = () -> LongStream.range(from, from + count).parallel()
				.mapToObj(seed -> {
					Candidate candidate = score(lookup, seed);
					int n = done.incrementAndGet();
					if (n % step == 0) Surrogate.LOGGER.info("Seeds: {} of {} scored", n, count);
					return candidate;
				})
				.sorted(Comparator.comparingDouble(Candidate::total).reversed())
				.toList();
		try {
			return pool.submit(work).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return List.of();
		} catch (ExecutionException e) {
			throw new IllegalStateException("seed sweep failed", e.getCause());
		} finally {
			pool.shutdown();
		}
	}

	/** What fidelity the numbers below were read at, so nobody reads them as the ground truth. */
	public static String fidelity() {
		return String.format("cells of %d blocks, one column each, out to %d blocks from spawn", CELL, RADIUS);
	}

	public static Candidate score(MinecraftServer server, long seed) {
		return score(server.getRegistryManager().createRegistryLookup(), seed);
	}

	public static Candidate score(RegistryEntryLookup.RegistryLookup lookup, long seed) {
		SeedSampler sampler = SeedSampler.of(lookup, seed);
		BlockPos spawn = sampler.spawn();
		Grid grid = new Grid(sampler.masks(), spawn.getX(), spawn.getZ());
		List<Part> parts = new ArrayList<>();
		BlockPos pod = grid.pickPod(spawn.getX(), spawn.getZ());
		if (pod == null) {
			parts.add(new Part("pod", 0.0, 15.0, "none"));
			return new Candidate(seed, 0.0, parts, spawn, null, null, "no pod site near spawn");
		}
		grid.fillFrom(pod.getX(), pod.getZ());
		int podCells = grid.reached;
		parts.add(new Part("pod", 15.0 * clamp01(podCells / 20000.0), 15.0, thousands(podCells) + " cells"));

		// The six near-side sites, placed the way the game places them, and the drives out to them.
		List<Site> sites = grid.pickSites(pod, seed);
		int found = 0;
		int walls = 0;
		double ratioPoints = 0.0;
		double hugPoints = 0.0;
		double ratioSum = 0.0;
		for (Site site : sites) {
			if (site.pos == null) continue;
			found++;
			if (site.wall) walls++;
			ratioSum += site.ratio;
			ratioPoints += ratioScore(site.ratio);
			hugPoints += hugScore(site.hug);
		}
		int slots = sites.size();
		parts.add(new Part("sites", 20.0 * found / slots, 20.0, found + "/" + slots));
		parts.add(new Part("drive", found == 0 ? 0.0 : 15.0 * ratioPoints / found, 15.0,
				found == 0 ? "none" : String.format("%.2f", ratioSum / found)));

		// Act five: a large piece of floor the pod cannot reach, at the far end, behind the Rift.
		Grid.Far far = grid.far(pod);
		if (far == null) {
			parts.add(new Part("rift", 0.0, 20.0, "no far side"));
			parts.add(new Part("cross", 0.0, 10.0, "none"));
			parts.add(new Part("belt", 0.0, 10.0, "none"));
		} else {
			double size = 12.0 * clamp01(far.cells / 4000.0);
			double band = 8.0 * bandScore(far.distance, FAR_MIN, FAR_MAX, 400.0);
			parts.add(new Part("rift", size + band, 20.0, String.format("%s cells @%dm", thousands(far.cells), Math.round(far.distance))));
			double span = far.span;
			parts.add(new Part("cross", span <= 0 ? 0.0 : 10.0 * bandScore(span, SPAN_MIN, SPAN_MAX, 8.0), 10.0,
					span <= 0 ? "none" : String.format("%.0fm @%d,%d", span, far.crossing.getX(), far.crossing.getZ())));
			parts.add(new Part("belt", 10.0 * clamp01(far.belt / 0.35), 10.0, String.format("%.0f%%", far.belt * 100.0)));
		}
		parts.add(new Part("wall", found == 0 ? 0.0 : 5.0 * walls / found, 5.0, walls + "/" + Math.max(1, found)));
		parts.add(new Part("var", found == 0 ? 0.0 : 5.0 * hugPoints / found, 5.0,
				found == 0 ? "none" : String.format("%.2f", hugPoints / found)));

		double total = 0.0;
		for (Part part : parts) total += part.points();
		String note = far == null ? "no far side" : far.span > 0 ? "" : "no crossing";
		return new Candidate(seed, total, parts, spawn, pod, far == null ? null : far.crossing, note);
	}

	// ------------------------------------------------------------------ the grid

	/** A site the sweep placed, and what the drive out to it looks like. */
	private static final class Site {
		final String name;
		@Nullable
		BlockPos pos;
		boolean wall;
		double ratio;
		double hug;

		Site(String name) {
			this.name = name;
		}
	}

	/**
	 * One candidate map, read once: what every cell is, what is connected to what, and the drive home from
	 * anywhere the pod can reach. About four megabytes and a second of one core.
	 */
	private static final class Grid {
		private final Valleys.Masks masks;
		private final int originCellX;
		private final int originCellZ;
		private final byte[] code = new byte[SIZE * SIZE];
		private final int[] comp = new int[SIZE * SIZE];
		private final int[] parents = new int[SIZE * SIZE];
		private final int[] queue = new int[SIZE * SIZE];
		private final List<Integer> componentSizes = new ArrayList<>();
		private int reached;
		private int podComp = -1;

		Grid(Valleys.Masks masks, int centreX, int centreZ) {
			this.masks = masks;
			this.originCellX = Math.floorDiv(centreX, CELL);
			this.originCellZ = Math.floorDiv(centreZ, CELL);
			for (int cz = 0; cz < SIZE; cz++) {
				int z = (cz + originCellZ - RADIUS_CELLS) * CELL + CELL / 2;
				for (int cx = 0; cx < SIZE; cx++) {
					int x = (cx + originCellX - RADIUS_CELLS) * CELL + CELL / 2;
					code[cz * SIZE + cx] = read(masks, x, z);
				}
			}
			label();
		}

		/**
		 * One column, one set of noise samples: {@link Valleys#classify} and {@link Valleys#flat} fused, so a
		 * cell costs one reading rather than two. The Rift is asked for before the cliff, as there.
		 */
		private static byte read(Valleys.Masks masks, int x, int z) {
			double c = masks.continents(x, z);
			if (c < Valleys.SEA) return (byte) Valleys.Kind.SEA.ordinal();
			if (c > Valleys.MESA) return (byte) Valleys.Kind.MESA.ordinal();
			if (masks.rift(x, z) > Valleys.RIFT_EDGE) return RIFT;
			if (c > Valleys.FLOOR) return (byte) Valleys.Kind.CLIFF.ordinal();
			if (masks.river(x, z) > Valleys.RIVER_EDGE) return (byte) Valleys.Kind.RIVER.ordinal();
			return c <= Valleys.FLAT ? DRIVABLE : (byte) Valleys.Kind.FLOOR.ordinal();
		}

		private int at(int blockX, int blockZ) {
			int cx = Math.floorDiv(blockX, CELL) - originCellX + RADIUS_CELLS;
			int cz = Math.floorDiv(blockZ, CELL) - originCellZ + RADIUS_CELLS;
			if (cx < 0 || cz < 0 || cx >= SIZE || cz >= SIZE) return -1;
			return cz * SIZE + cx;
		}

		private int blockX(int index) {
			return (index % SIZE + originCellX - RADIUS_CELLS) * CELL + CELL / 2;
		}

		private int blockZ(int index) {
			return (index / SIZE + originCellZ - RADIUS_CELLS) * CELL + CELL / 2;
		}

		private boolean drivable(int index) {
			return code[index] == DRIVABLE;
		}

		/**
		 * A step onto a neighbour, with the crawler's rule: a diagonal also needs both cells it cuts between,
		 * or a one-cell chasm can be jumped on the diagonal and the Rift stops separating anything.
		 */
		private boolean step(int cx, int cz, int nx, int nz) {
			if (!drivable(nz * SIZE + nx)) return false;
			if (cx == nx || cz == nz) return true;
			return drivable(cz * SIZE + nx) && drivable(nz * SIZE + cx);
		}

		/** Labels every connected patch of drivable floor, so the far side can be found without a second fill. */
		private void label() {
			java.util.Arrays.fill(comp, -1);
			for (int start = 0; start < code.length; start++) {
				if (!drivable(start) || comp[start] >= 0) continue;
				int id = componentSizes.size();
				int head = 0;
				int tail = 0;
				queue[tail++] = start;
				comp[start] = id;
				int size = 0;
				while (head < tail) {
					int index = queue[head++];
					size++;
					int cx = index % SIZE;
					int cz = index / SIZE;
					for (int i = 0; i < 8; i++) {
						int nx = cx + DX[i];
						int nz = cz + DZ[i];
						if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
						int next = nz * SIZE + nx;
						if (comp[next] >= 0 || !step(cx, cz, nx, nz)) continue;
						comp[next] = id;
						queue[tail++] = next;
					}
				}
				componentSizes.add(size);
			}
		}

		/** The drive home from every cell the pod can reach, by the same rule. */
		private void fillFrom(int blockX, int blockZ) {
			java.util.Arrays.fill(parents, UNREACHED);
			int start = at(blockX, blockZ);
			podComp = start < 0 ? -1 : comp[start];
			reached = 0;
			if (start < 0 || !drivable(start)) return;
			int head = 0;
			int tail = 0;
			parents[start] = SEED_PARENT;
			queue[tail++] = start;
			while (head < tail) {
				int index = queue[head++];
				reached++;
				int cx = index % SIZE;
				int cz = index / SIZE;
				for (int i = 0; i < 8; i++) {
					int nx = cx + DX[i];
					int nz = cz + DZ[i];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
					int next = nz * SIZE + nx;
					if (parents[next] != UNREACHED || !step(cx, cz, nx, nz)) continue;
					parents[next] = index;
					queue[tail++] = next;
				}
			}
		}

		/** The drive back to the pod, in blocks, or 0 when there is none. */
		private double driveLength(int index) {
			if (index < 0 || parents[index] == UNREACHED) return 0.0;
			double length = 0.0;
			while (parents[index] >= 0) {
				int parent = parents[index];
				boolean diagonal = index % SIZE != parent % SIZE && index / SIZE != parent / SIZE;
				length += diagonal ? CELL * Math.sqrt(2.0) : CELL;
				index = parent;
			}
			return length;
		}

		/** How much of the drive runs within a cell of something it cannot drive over: threading, not crossing. */
		private double driveHug(int index) {
			if (index < 0 || parents[index] == UNREACHED) return 0.0;
			int cells = 0;
			int hugged = 0;
			while (index >= 0) {
				cells++;
				int cx = index % SIZE;
				int cz = index / SIZE;
				for (int i = 0; i < 8; i++) {
					int nx = cx + DX[i];
					int nz = cz + DZ[i];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
					if (!drivable(nz * SIZE + nx)) {
						hugged++;
						break;
					}
				}
				index = parents[index] >= 0 ? parents[index] : -1;
			}
			return cells == 0 ? 0.0 : (double) hugged / cells;
		}

		/**
		 * The pod, the way {@link Valleys#pickStart} finds it: the nearest candidate to spawn with room for the
		 * pod and its apron that opens onto a lot of connected floor, a wall behind it for preference. The
		 * reachable count comes off the labelling rather than a fill per candidate, which is the whole saving.
		 */
		@Nullable
		private BlockPos pickPod(int spawnX, int spawnZ) {
			List<BlockPos> candidates = new ArrayList<>();
			for (int dz = -POD_SEARCH; dz <= POD_SEARCH; dz += CELL) {
				for (int dx = -POD_SEARCH; dx <= POD_SEARCH; dx += CELL) {
					candidates.add(new BlockPos(spawnX + dx, 0, spawnZ + dz));
				}
			}
			candidates.sort(Comparator.comparingInt(pos -> Math.abs(pos.getX() - spawnX) + Math.abs(pos.getZ() - spawnZ)));
			BlockPos plain = null;
			int tests = 0;
			for (BlockPos pos : candidates) {
				int index = at(pos.getX(), pos.getZ());
				if (index < 0 || !drivable(index) || componentSizes.get(comp[index]) < Valleys.OPEN_FLOOR_CELLS) continue;
				// Bounded: the sweep will not pay pickStart's price of ringing every flat cell around spawn.
				if (++tests > POD_TESTS * 8) break;
				if (!Valleys.clear(masks, pos.getX(), pos.getZ(), 16) || !Valleys.approachClear(masks, pos.getX(), pos.getZ())) continue;
				if (Valleys.nearWall(masks, pos.getX(), pos.getZ(), 24, 64)) return pos;
				if (plain == null) plain = pos;
			}
			return plain;
		}

		/**
		 * The six sites of the near side, in the order the game places them and under the same keep-outs: four
		 * shelters one per sector, then Halloran's station, then the company pad away from it.
		 */
		private List<Site> pickSites(BlockPos pod, long seed) {
			Random random = new Random(seed * 31L + 17L);
			List<Site> sites = new ArrayList<>();
			List<BlockPos> placed = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int min = i == 0 ? 160 : 300;
				int max = i == 0 ? 280 : 900;
				sites.add(place(new Site("shelter" + i), pod, random, 2.0 * Math.PI * i / 4.0, 0.45, min, max, placed, 120));
			}
			Site two = place(new Site("site02"), pod, random, random.nextDouble() * Math.PI * 2.0, Math.PI, 460, 620, placed, 80);
			sites.add(two);
			double away = two.pos == null ? random.nextDouble() * Math.PI * 2.0
					: Math.atan2(two.pos.getZ() - pod.getZ(), two.pos.getX() - pod.getX()) + Math.PI;
			sites.add(place(new Site("pad"), pod, random, away, Math.PI * 0.6, 620, 900, placed, 120));
			return sites;
		}

		private Site place(Site site, BlockPos pod, Random random, double angle, double jitter, int min, int max,
						   List<BlockPos> placed, int keepOut) {
			BlockPos plain = null;
			for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
				double a = angle + (random.nextDouble() * 2.0 - 1.0) * jitter;
				double distance = min + random.nextDouble() * Math.max(1, max - min);
				int x = pod.getX() + (int) Math.round(Math.cos(a) * distance);
				int z = pod.getZ() + (int) Math.round(Math.sin(a) * distance);
				boolean apart = true;
				for (BlockPos other : placed) {
					if (Math.abs(other.getX() - x) < keepOut && Math.abs(other.getZ() - z) < keepOut) apart = false;
				}
				if (!apart) continue;
				int index = at(x, z);
				if (index < 0 || parents[index] == UNREACHED) continue;
				if (!Valleys.clear(masks, x, z, 12) || !Valleys.approachClear(masks, x, z)) continue;
				BlockPos candidate = new BlockPos(x, 0, z);
				if (Valleys.nearWall(masks, x, z, 20, 60)) {
					site.wall = true;
					plain = candidate;
					break;
				}
				if (plain == null) plain = candidate;
			}
			site.pos = plain;
			if (plain != null) {
				placed.add(plain);
				int index = at(plain.getX(), plain.getZ());
				double crow = Math.max(1.0, Math.hypot(plain.getX() - pod.getX(), plain.getZ() - pod.getZ()));
				site.ratio = driveLength(index) / crow;
				site.hug = driveHug(index);
			}
			return site;
		}

		/** The far side: how big, how far, how much of it is belt, and the narrowest chasm on the way in. */
		private static final class Far {
			int cells;
			double distance;
			double belt;
			double span;
			BlockPos crossing = BlockPos.ORIGIN;
		}

		/**
		 * The largest floor the pod cannot reach that lies the right distance out and is shut off by the Rift
		 * rather than by a table. The chasm's thickness comes from a fill through Rift cells only, seeded off
		 * the pod's own region, so the answer is the narrowest place the two sides come together.
		 */
		@Nullable
		private Far far(BlockPos pod) {
			if (podComp < 0) return null;
			// Sum the cells and the centroid of every other component in one pass.
			int components = componentSizes.size();
			long[] sumX = new long[components];
			long[] sumZ = new long[components];
			for (int index = 0; index < code.length; index++) {
				int id = comp[index];
				if (id < 0 || id == podComp) continue;
				sumX[id] += blockX(index);
				sumZ[id] += blockZ(index);
			}
			// Thickness of the chasm, in Rift cells, out from the pod's own shore.
			int[] depth = riftDepth();
			int[] shore = new int[components];
			int[] shoreAt = new int[components];
			java.util.Arrays.fill(shore, Integer.MAX_VALUE);
			for (int index = 0; index < code.length; index++) {
				if (code[index] != RIFT || depth[index] <= 0) continue;
				int cx = index % SIZE;
				int cz = index / SIZE;
				for (int i = 0; i < 8; i++) {
					int nx = cx + DX[i];
					int nz = cz + DZ[i];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
					int id = comp[nz * SIZE + nx];
					if (id < 0 || id == podComp || depth[index] >= shore[id]) continue;
					shore[id] = depth[index];
					shoreAt[id] = index;
				}
			}
			Far best = null;
			for (int id = 0; id < components; id++) {
				int cells = componentSizes.get(id);
				if (id == podComp || cells < MIN_FAR_CELLS || shore[id] == Integer.MAX_VALUE) continue;
				double distance = Math.hypot((double) sumX[id] / cells - pod.getX(), (double) sumZ[id] / cells - pod.getZ());
				if (distance < FAR_MIN * 0.5 || distance > FAR_MAX * 1.5) continue;
				if (best != null && cells <= best.cells) continue;
				Far far = new Far();
				far.cells = cells;
				far.distance = distance;
				far.crossing = new BlockPos(blockX(shoreAt[id]), 0, blockZ(shoreAt[id]));
				far.span = span(far.crossing.getX(), far.crossing.getZ());
				far.belt = beltShare(id, cells);
				best = far;
			}
			return best;
		}

		/** Rift cells numbered by how many of them lie between here and the pod's shore: 1 is the near bank. */
		private int[] riftDepth() {
			int[] depth = new int[SIZE * SIZE];
			int head = 0;
			int tail = 0;
			for (int index = 0; index < code.length; index++) {
				if (code[index] != RIFT) continue;
				int cx = index % SIZE;
				int cz = index / SIZE;
				for (int i = 0; i < 8; i++) {
					int nx = cx + DX[i];
					int nz = cz + DZ[i];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
					if (comp[nz * SIZE + nx] != podComp) continue;
					depth[index] = 1;
					queue[tail++] = index;
					break;
				}
			}
			while (head < tail) {
				int index = queue[head++];
				int cx = index % SIZE;
				int cz = index / SIZE;
				for (int i = 0; i < 8; i++) {
					int nx = cx + DX[i];
					int nz = cz + DZ[i];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
					int next = nz * SIZE + nx;
					if (code[next] != RIFT || depth[next] != 0) continue;
					depth[next] = depth[index] + 1;
					queue[tail++] = next;
				}
			}
			return depth;
		}

		/**
		 * The chasm's width at one point, at block resolution: the shortest of the four straight lines through
		 * it. The cell grid only ever answers in eights, and the difference between eight and thirteen is the
		 * difference between a span kit and an afternoon.
		 */
		private double span(int x, int z) {
			double best = Double.MAX_VALUE;
			for (int axis = 0; axis < 4; axis++) {
				int dx = DX[axis * 2];
				int dz = DZ[axis * 2];
				double step = dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0;
				double width = step;
				for (int sign = -1; sign <= 1; sign += 2) {
					for (int d = 1; d <= 48; d++) {
						if (!Valleys.inRift(masks, x + dx * d * sign, z + dz * d * sign)) break;
						width += step;
					}
				}
				best = Math.min(best, width);
			}
			return best == Double.MAX_VALUE ? 0.0 : best;
		}

		/** How much of a far component is downwind of the vent field, sampled every fourth cell. */
		private double beltShare(int id, int cells) {
			int sampled = 0;
			int inside = 0;
			int stride = Math.max(1, cells / 250);
			int seen = 0;
			for (int index = 0; index < code.length; index++) {
				if (comp[index] != id) continue;
				if (seen++ % stride != 0) continue;
				sampled++;
				if (Valleys.inBelt(masks, blockX(index), blockZ(index))) inside++;
			}
			return sampled == 0 ? 0.0 : (double) inside / sampled;
		}
	}

	/** The eight neighbours: four orthogonal first, so {@code DX[axis * 2]} walks the four axes. */
	private static final int[] DX = {1, -1, 0, 0, 1, -1, 1, -1};
	private static final int[] DZ = {0, 0, 1, -1, 1, -1, -1, 1};

	// ------------------------------------------------------------------ scoring shapes

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	/** 1 inside the band, falling off linearly over {@code slack} on either side of it. */
	private static double bandScore(double value, double min, double max, double slack) {
		if (value >= min && value <= max) return 1.0;
		double miss = value < min ? min - value : value - max;
		return clamp01(1.0 - miss / slack);
	}

	private static double ratioScore(double ratio) {
		return ratio <= 0 ? 0.0 : bandScore(ratio, RATIO_MIN, RATIO_MAX, 0.7);
	}

	/** A drive that never passes anything is a car park; one that is always in a corridor is a maze. */
	private static double hugScore(double hug) {
		return bandScore(hug, 0.25, 0.75, 0.25);
	}

	private static String thousands(int value) {
		return value >= 10000 ? (value / 1000) + "k" : Integer.toString(value);
	}

	// ------------------------------------------------------------------ output

	/** The ranked table, one seed a line, best first. */
	public static List<String> table(List<Candidate> ranked, int rows) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < Math.min(rows, ranked.size()); i++) {
			lines.add(ranked.get(i).row(i + 1));
		}
		return lines;
	}

	/** The line to paste, once a seed has won. */
	public static String paste(Candidate winner) {
		return "Winner: seed " + winner.seed() + " -- server.properties: level-seed=" + winner.seed()
				+ " -- config/surrogate.json: \"lockedSeed\": \"" + winner.seed() + "\"";
	}

	/** The whole sweep, for tools/seed_search.py to read back. */
	public static void write(Path out, long from, int count, double seconds, List<Candidate> ranked) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("from", from);
		root.addProperty("count", count);
		root.addProperty("seconds", Math.round(seconds * 10.0) / 10.0);
		root.addProperty("fidelity", fidelity());
		root.addProperty("cell", CELL);
		root.addProperty("radius", RADIUS);
		if (!ranked.isEmpty()) {
			root.add("winner", json(ranked.get(0)));
			root.addProperty("paste", paste(ranked.get(0)));
		}
		JsonArray all = new JsonArray();
		for (int i = 0; i < Math.min(50, ranked.size()); i++) all.add(json(ranked.get(i)));
		root.add("ranked", all);
		Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
	}

	private static JsonObject json(Candidate candidate) {
		JsonObject object = new JsonObject();
		object.addProperty("seed", candidate.seed());
		object.addProperty("total", Math.round(candidate.total() * 10.0) / 10.0);
		if (candidate.spawn() != null) object.add("spawn", point(candidate.spawn()));
		if (candidate.pod() != null) object.add("pod", point(candidate.pod()));
		if (candidate.crossing() != null) object.add("crossing", point(candidate.crossing()));
		if (!candidate.note().isEmpty()) object.addProperty("note", candidate.note());
		JsonObject parts = new JsonObject();
		for (Part part : candidate.parts()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("points", Math.round(part.points() * 10.0) / 10.0);
			entry.addProperty("max", part.max());
			entry.addProperty("detail", part.detail());
			parts.add(part.name(), entry);
		}
		object.add("parts", parts);
		return object;
	}

	private static JsonObject point(BlockPos pos) {
		JsonObject object = new JsonObject();
		object.addProperty("x", pos.getX());
		object.addProperty("z", pos.getZ());
		return object;
	}
}
