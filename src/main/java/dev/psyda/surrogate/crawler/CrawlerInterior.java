package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.CrawlerBayBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.item.RobotChassisItem;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.network.CrawlerPayloads;
import dev.psyda.surrogate.pilot.PilotData;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.LightRefresh;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The crawler from the inside. Boarding a hull (or the collar door it is coupled to) puts the player in its
 * cabin in the pocket dimension; the hatch puts them back out beside the hull, or through the collar into
 * the pod when docked. While anyone is aboard, the hull's chunks stay loaded so it can be driven from the
 * helm: the client sends the driver's keys, the server hands them to the hull. The docking console does the
 * same in reverse and couples. The bay deploys a chassis beside the hull and takes one back.
 */
public final class CrawlerInterior {
	public static final int SEAT_NONE = 0;
	public static final int SEAT_HELM = 1;
	public static final int SEAT_DOCK = 2;
	private static final ChunkTicketType<Integer> HULL_TICKET = ChunkTicketType.create("surrogate_crawler", Integer::compare, 60);
	/** -Dsurrogate.debugSeats=true logs what each console reads and hands to the hull, once a second. */
	private static final boolean DEBUG_SEATS = Boolean.getBoolean("surrogate.debugSeats");

	/** A player at a console: which cabin, which console, and the keys they are holding. */
	private static final class Seat {
		final int slot;
		final int kind;
		final BlockPos console;
		final BlockPos stand;
		final CrawlerDocking.Console docking = new CrawlerDocking.Console();
		float throttle;
		float steer;
		/** A click since the last tick: the docking console locks on it. */
		boolean lock;
		int packetAge = 99;
		int blankTicks;
		boolean warned;

		Seat(int slot, int kind, BlockPos console, BlockPos stand) {
			this.slot = slot;
			this.kind = kind;
			this.console = console;
			this.stand = stand;
		}
	}

	/** Per cabin, per session: the painted ground's bookkeeping and whether its chunks are held. */
	public static final class Runtime {
		public int[] prevTop;
		public Vec3d lastPos;
		public float lastYaw;
		public long lastPainted = -100L;
		boolean forced;
	}

	private static final Map<UUID, Seat> SEATS = new HashMap<>();
	private static final Map<Integer, Runtime> RUNTIMES = new HashMap<>();
	/** Dev: players to put aboard the nearest hull a moment after they join, and the console to stand them at. */
	private static final Map<UUID, String> DEV_BOARD = new HashMap<>();
	private static final Map<UUID, Integer> DEV_BOARD_DELAY = new HashMap<>();

	/** Dev only: boards the nearest crawler two seconds after the join, so the client can be pointed at the cabin. */
	public static void devBoard(ServerPlayerEntity player, String console) {
		DEV_BOARD.put(player.getUuid(), console);
		DEV_BOARD_DELAY.put(player.getUuid(), 40);
	}

	private static void tickDevBoard(MinecraftServer server) {
		if (DEV_BOARD.isEmpty()) return;
		for (UUID id : new ArrayList<>(DEV_BOARD.keySet())) {
			int delay = DEV_BOARD_DELAY.merge(id, -1, Integer::sum);
			if (delay > 0) continue;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			String console = DEV_BOARD.get(id);
			if (player == null) {
				DEV_BOARD.remove(id);
				continue;
			}
			if (!CrawlerDimension.isCabin(player.getWorld())) {
				ServerWorld world = (ServerWorld) player.getWorld();
				CrawlerEntity nearest = null;
				double best = 128.0 * 128.0;
				for (CrawlerEntity hull : world.getEntitiesByClass(CrawlerEntity.class, player.getBoundingBox().expand(128.0), hull -> true)) {
					double d = hull.squaredDistanceTo(player);
					if (d < best) {
						best = d;
						nearest = hull;
					}
				}
				if (nearest == null) {
					Surrogate.LOGGER.info("Dev board: no crawler near {}", player.getName().getString());
					DEV_BOARD.remove(id);
					continue;
				}
				board(player, nearest);
				DEV_BOARD_DELAY.put(id, 20);
				if (console.equals("cabin")) DEV_BOARD.remove(id);
				continue;
			}
			BlockPos origin = CrawlerInteriors.origin(CrawlerInteriors.indexAt(player.getBlockPos()));
			if (console.equals("dock")) sitAt(player, origin.add(CrawlerRoom.DOCK_CONSOLE), Direction.NORTH, SEAT_DOCK);
			else sitAt(player, origin.add(CrawlerRoom.HELM), Direction.SOUTH, SEAT_HELM);
			DEV_BOARD.remove(id);
		}
	}

	private CrawlerInterior() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(CrawlerInterior::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEATS.remove(handler.player.getUuid()));
	}

	private static Runtime runtime(int slot) {
		return RUNTIMES.computeIfAbsent(slot, k -> new Runtime());
	}

	// ------------------------------------------------------------------ boarding

	/** Puts the player in the hull's cabin, building it first if this hull never had one. */
	public static boolean board(ServerPlayerEntity player, CrawlerEntity hull) {
		MinecraftServer server = player.server;
		ServerWorld cabin = CrawlerDimension.world(server);
		if (cabin == null) {
			player.sendMessage(Text.literal("The crawler cabin dimension is missing."), true);
			return false;
		}
		CrawlerInteriors interiors = CrawlerInteriors.get(server);
		CrawlerInteriors.Slot slot = interiors.forHull(hull.getUuid());
		if (slot == null) slot = interiors.allocate(hull.getUuid());
		hull.setInterior(slot.index);
		BlockPos origin = CrawlerInteriors.origin(slot.index);
		CrawlerRoom.forceChunks(cabin, origin, true);
		runtime(slot.index).forced = true;
		if (!slot.built) {
			CrawlerRoom.build(cabin, origin);
			slot.built = true;
		}
		rememberHull(slot, hull);
		interiors.markDirty();
		Vec3d at = Vec3d.ofBottomCenter(origin.add(CrawlerRoom.HATCH_STAND));
		ServerPlayNetworking.send(player, new CinematicPayloads.Fade(255, 0));
		bringCat(player, cabin, Vec3d.ofBottomCenter(origin.add(CrawlerRoom.CAT_SPOT)));
		player.teleport(cabin, at.x, at.y, at.z, 180f, 0f);
		ServerPlayNetworking.send(player, new CinematicPayloads.Fade(0, 40));
		LightRefresh.schedule(player, CrawlerDimension.WORLD, origin, 2, 10);
		player.sendMessage(Text.translatable("message.surrogate.crawler.aboard").formatted(Formatting.AQUA), true);
		return true;
	}

	/** The collar door: through it into whatever is coupled outside. */
	public static boolean boardFromCollar(ServerPlayerEntity player, BlockPos door) {
		CrawlerEntity hull = CrawlerDocking.hullAt(player.server.getOverworld(), door);
		if (hull == null) {
			player.sendMessage(Text.translatable("message.surrogate.dock_door.sealed").formatted(Formatting.GRAY), true);
			return false;
		}
		return board(player, hull);
	}

	/** The hatch: out beside the hull, or into the pod when the hull is on a collar. */
	public static boolean leave(ServerPlayerEntity player, BlockPos hatch) {
		MinecraftServer server = player.server;
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).byIndex(CrawlerInteriors.indexAt(hatch));
		if (slot == null) return false;
		CrawlerEntity hull = hullOf(server, slot);
		if (hull == null) {
			player.sendMessage(Text.translatable("message.surrogate.crawler.hull_far").formatted(Formatting.GRAY), true);
			return false;
		}
		ServerWorld overworld = server.getOverworld();
		Vec3d at;
		float yaw;
		String message;
		if (hull.isDocked()) {
			CrawlerDocking.Collar collar = CrawlerDocking.nearest(server, hull, 3.0);
			if (collar == null) {
				player.sendMessage(Text.translatable("message.surrogate.crawler.hull_far").formatted(Formatting.GRAY), true);
				return false;
			}
			at = Vec3d.ofBottomCenter(collar.door().east());
			yaw = -90f;
			message = "message.surrogate.crawler.into_pod";
		} else {
			at = null;
			for (Vec3d offset : new Vec3d[]{new Vec3d(0.0, 0.0, -(CrawlerEntity.HALF_LENGTH + 2.0)), new Vec3d(3.5, 0.0, 0.0), new Vec3d(-3.5, 0.0, 0.0)}) {
				Vec3d candidate = hull.getPos().add(hull.local(offset.x, offset.y, offset.z));
				Box body = player.getDimensions(player.getPose()).getBoxAt(candidate);
				if (overworld.isSpaceEmpty(player, body)) {
					at = candidate;
					break;
				}
			}
			if (at == null) at = hull.getPos().add(hull.local(0.0, CrawlerEntity.HEIGHT, 0.0));
			yaw = hull.getYaw();
			message = "message.surrogate.crawler.outside";
		}
		standUp(player);
		ServerPlayNetworking.send(player, new CinematicPayloads.Fade(255, 0));
		Vec3d catAt = at;
		bringCat(player, overworld, catAt);
		player.teleport(overworld, at.x, at.y, at.z, yaw, 0f);
		ServerPlayNetworking.send(player, new CinematicPayloads.Fade(0, 40));
		player.sendMessage(Text.translatable(message).formatted(Formatting.AQUA), true);
		return true;
	}

	/** The cat comes along when it is close and not told to stay. */
	private static void bringCat(ServerPlayerEntity player, ServerWorld to, Vec3d at) {
		HabitatState habitat = HabitatState.get(player.server);
		if (habitat.cat == null || !(player.getWorld() instanceof ServerWorld from)) return;
		Entity entity = from.getEntity(habitat.cat);
		if (!(entity instanceof CatEntity cat) || cat.isSitting() || cat.isInSittingPose()) return;
		if (cat.squaredDistanceTo(player) > 64.0) return;
		cat.teleport(to, at.x, at.y, at.z, Set.of(), cat.getYaw(), 0f);
	}

	// ------------------------------------------------------------------ consoles

	public static void sitAt(ServerPlayerEntity player, BlockPos console, Direction facing, int kind) {
		if (!CrawlerDimension.isCabin(player.getWorld())) return;
		int slot = CrawlerInteriors.indexAt(console);
		BlockPos stand = console.offset(facing);
		Vec3d at = Vec3d.ofBottomCenter(stand);
		player.teleport((ServerWorld) player.getWorld(), at.x, at.y, at.z, facing.getOpposite().asRotation(), 10f);
		SEATS.put(player.getUuid(), new Seat(slot, kind, console, stand));
		player.sendMessage(Text.translatable(kind == SEAT_HELM ? "message.surrogate.crawler.helm" : "message.surrogate.crawler.dock_seat").formatted(Formatting.AQUA), true);
		CrawlerEntity hull = hullOf(player.server, CrawlerInteriors.get(player.server).byIndex(slot));
		sendState(player, hull, SEATS.get(player.getUuid()));
	}

	public static void standUp(ServerPlayerEntity player) {
		Seat seat = SEATS.remove(player.getUuid());
		if (seat == null) return;
		ServerPlayNetworking.send(player, CrawlerPayloads.State.none());
	}

	public static boolean isSeated(ServerPlayerEntity player) {
		return SEATS.containsKey(player.getUuid());
	}

	/** True while the player stands at this console in particular. */
	public static boolean isSeatedAt(ServerPlayerEntity player, BlockPos console) {
		Seat seat = SEATS.get(player.getUuid());
		return seat != null && seat.console.equals(console);
	}

	/** A click at the console: the docking console locks the ring on it. */
	public static void lockRequested(ServerPlayerEntity player) {
		Seat seat = SEATS.get(player.getUuid());
		if (seat != null) seat.lock = true;
		if (DEBUG_SEATS) Surrogate.LOGGER.info("Lock requested by {} ({})", player.getName().getString(), seat == null ? "not seated" : "seat " + seat.kind);
	}

	/** The driver's keys, from the client, every tick they are at a console. */
	public static void control(ServerPlayerEntity player, float throttle, float steer, boolean leave, boolean lock) {
		Seat seat = SEATS.get(player.getUuid());
		if (seat == null) return;
		if (leave) {
			standUp(player);
			return;
		}
		seat.throttle = MathHelper.clamp(throttle, -1f, 1f);
		seat.steer = MathHelper.clamp(steer, -1f, 1f);
		seat.lock |= lock;
		seat.packetAge = 0;
	}

	/** Everyone standing in this hull's cabin right now. */
	public static List<ServerPlayerEntity> playersAboard(MinecraftServer server, CrawlerEntity hull) {
		List<ServerPlayerEntity> aboard = new ArrayList<>();
		ServerWorld cabin = CrawlerDimension.world(server);
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).forHull(hull.getUuid());
		if (cabin == null || slot == null || !slot.built) return aboard;
		Box bounds = CrawlerRoom.bounds(CrawlerInteriors.origin(slot.index));
		for (ServerPlayerEntity player : cabin.getPlayers()) if (bounds.contains(player.getPos())) aboard.add(player);
		return aboard;
	}

	// ------------------------------------------------------------------ the hull

	/** The cabin's hull, when its chunk is loaded; otherwise asks for the chunk and answers null this tick. */
	@Nullable
	public static CrawlerEntity hullOf(MinecraftServer server, @Nullable CrawlerInteriors.Slot slot) {
		if (slot == null || slot.hull == null) return null;
		ServerWorld overworld = server.getOverworld();
		if (overworld.getEntity(slot.hull) instanceof CrawlerEntity hull) return hull;
		overworld.getChunkManager().addTicket(HULL_TICKET, new ChunkPos(slot.hullChunkX, slot.hullChunkZ), 3, slot.index);
		return null;
	}

	/**
	 * A hull for a command that needs one whether or not anyone is aboard: the loaded one with a cabin, else
	 * any loaded hull. A hull with a cabin that is not loaded gets its last chunks held so it is there on the
	 * next try; entities come in a tick after their chunk does.
	 */
	@Nullable
	public static CrawlerEntity findHull(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		CrawlerInteriors interiors = CrawlerInteriors.get(server);
		for (CrawlerInteriors.Slot slot : interiors.slots()) {
			if (slot.hull == null) continue;
			if (overworld.getEntity(slot.hull) instanceof CrawlerEntity hull) return hull;
			forceAround(server, interiors, slot, new ChunkPos(slot.hullChunkX, slot.hullChunkZ));
		}
		for (Entity entity : overworld.iterateEntities()) if (entity instanceof CrawlerEntity hull) return hull;
		return null;
	}

	/** After a hull has been moved by hand: hold its chunks where it stands now and remember where that is. */
	public static void holdHull(MinecraftServer server, CrawlerEntity hull) {
		CrawlerInteriors interiors = CrawlerInteriors.get(server);
		CrawlerInteriors.Slot slot = interiors.forHull(hull.getUuid());
		if (slot == null) return;
		rememberHull(slot, hull);
		interiors.markDirty();
		forceAround(server, interiors, slot, hull.getChunkPos());
	}

	private static void rememberHull(CrawlerInteriors.Slot slot, CrawlerEntity hull) {
		ChunkPos chunk = hull.getChunkPos();
		slot.hullChunkX = chunk.x;
		slot.hullChunkZ = chunk.z;
	}

	/**
	 * Entities only tick in chunks a player is near or that are force-loaded; a plain ticket only keeps them
	 * in memory. So while the cabin is occupied the 3x3 chunks around the hull are force-loaded, moving with
	 * it, and released when the cabin empties. The centre is saved so a stale area is released after a crash.
	 */
	private static void forceAround(MinecraftServer server, CrawlerInteriors interiors, CrawlerInteriors.Slot slot, ChunkPos center) {
		if (slot.forcedX == center.x && slot.forcedZ == center.z) return;
		ServerWorld overworld = server.getOverworld();
		releaseForced(server, interiors, slot);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) overworld.setChunkForced(center.x + dx, center.z + dz, true);
		}
		slot.forcedX = center.x;
		slot.forcedZ = center.z;
		interiors.markDirty();
	}

	private static void releaseForced(MinecraftServer server, CrawlerInteriors interiors, CrawlerInteriors.Slot slot) {
		if (slot.forcedX == Integer.MIN_VALUE) return;
		ServerWorld overworld = server.getOverworld();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) overworld.setChunkForced(slot.forcedX + dx, slot.forcedZ + dz, false);
		}
		slot.forcedX = Integer.MIN_VALUE;
		slot.forcedZ = Integer.MIN_VALUE;
		interiors.markDirty();
	}

	@Nullable
	public static CrawlerInteriors.Slot slotAt(MinecraftServer server, BlockPos posInCabin) {
		return CrawlerInteriors.get(server).byIndex(CrawlerInteriors.indexAt(posInCabin));
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		ServerWorld cabin = CrawlerDimension.world(server);
		if (cabin == null) return;
		tickDevBoard(server);
		CrawlerInteriors interiors = CrawlerInteriors.get(server);
		if (interiors.slots().isEmpty()) return;
		List<ServerPlayerEntity> aboard = cabin.getPlayers();
		long ticks = server.getOverworld().getTime();
		for (CrawlerInteriors.Slot slot : interiors.slots()) {
			if (!slot.built) continue;
			Runtime runtime = runtime(slot.index);
			BlockPos origin = CrawlerInteriors.origin(slot.index);
			Box bounds = CrawlerRoom.bounds(origin);
			List<ServerPlayerEntity> inside = new ArrayList<>();
			for (ServerPlayerEntity player : aboard) if (bounds.contains(player.getPos())) inside.add(player);
			// A pilot out in a chassis has left their body in the cabin's chair: the cabin stays alive for it.
			boolean bodyAboard = false;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				PilotData.Session session = PilotManager.data(player).session;
				if (session != null && session.chairDimension == CrawlerDimension.WORLD && bounds.contains(Vec3d.ofCenter(session.chairPos))) bodyAboard = true;
			}
			if (inside.isEmpty() && !bodyAboard) {
				if (ticks % 200 == 0) {
					if (runtime.forced) {
						CrawlerRoom.forceChunks(cabin, origin, false);
						runtime.forced = false;
					}
					releaseForced(server, interiors, slot);
				}
				continue;
			}
			if (!runtime.forced) {
				CrawlerRoom.forceChunks(cabin, origin, true);
				runtime.forced = true;
			}
			CrawlerEntity hull = hullOf(server, slot);
			if (hull != null) {
				forceAround(server, interiors, slot, hull.getChunkPos());
				if (ticks % 20 == 0) {
					// The ground ahead of the porthole is read from chunks that only need to be in memory.
					server.getOverworld().getChunkManager().addTicket(HULL_TICKET, hull.getChunkPos(), 4, slot.index);
					if (hull.getChunkPos().x != slot.hullChunkX || hull.getChunkPos().z != slot.hullChunkZ) {
						rememberHull(slot, hull);
						interiors.markDirty();
					}
				}
				feedLifeSupport(cabin, origin, hull);
				if (Surrogate.CONFIG.crawlerPorthole) CrawlerRoom.updateDiorama(cabin, origin, server.getOverworld(), hull, runtime);
			}
			for (ServerPlayerEntity player : inside) {
				Seat seat = SEATS.get(player.getUuid());
				if (seat != null && seat.slot == slot.index) tickSeat(server, player, seat, hull);
				if (seat != null || ticks % 10 == 0) sendState(player, hull, seat);
				if (seat != null && hull != null && ticks % 10 == 0) CrawlerSonar.send(server, player, hull);
				if (hull != null && ticks % 10 == 5) CrawlerCamera.send(server, player, hull);
			}
		}
		SEATS.entrySet().removeIf(entry -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			return player == null || !CrawlerDimension.isCabin(player.getWorld());
		});
	}

	private static void tickSeat(MinecraftServer server, ServerPlayerEntity player, Seat seat, @Nullable CrawlerEntity hull) {
		// Whoever is at a console stays at it: the keys belong to the hull now. Only a real displacement
		// (a teleport, say) counts as leaving.
		Vec3d stand = Vec3d.ofBottomCenter(seat.stand);
		double drift = player.getPos().squaredDistanceTo(stand);
		if (drift > 9.0) {
			standUp(player);
			return;
		}
		// The client sends the keys; a player whose client does not (a fake one, say) is read from the
		// entity instead, before the pin below wipes them, and held across the odd blank tick.
		if (seat.packetAge++ > 3) {
			float throttle = deadzone(player.forwardSpeed);
			float steer = deadzone(player.sidewaysSpeed);
			if (throttle != 0f || steer != 0f) {
				seat.throttle = throttle;
				seat.steer = steer;
				seat.blankTicks = 0;
			} else if (++seat.blankTicks > 5) {
				seat.throttle = 0f;
				seat.steer = 0f;
			}
		}
		if (drift > 0.04) {
			player.teleport((ServerWorld) player.getWorld(), stand.x, stand.y, stand.z, player.getYaw(), player.getPitch());
			player.setVelocity(Vec3d.ZERO);
		}
		if (DEBUG_SEATS && server.getOverworld().getTime() % 20 == 0) {
			Surrogate.LOGGER.info("Seat {} of {}: keys {}/{} raw {}/{} age {} drift {} hull {} speed {} yaw {}", seat.kind, player.getName().getString(),
					seat.throttle, seat.steer, player.forwardSpeed, player.sidewaysSpeed, seat.packetAge, String.format("%.2f", Math.sqrt(drift)),
					hull == null ? "none" : hull.getPos().toString(), hull == null ? 0f : hull.getSpeed(), hull == null ? 0f : hull.getYaw());
		}
		boolean lock = seat.lock;
		seat.lock = false;
		if (hull == null) return;
		if (seat.kind == SEAT_HELM) {
			if (hull.isDocked()) {
				if ((seat.throttle != 0f || seat.steer != 0f) && !seat.warned) {
					seat.warned = true;
					player.sendMessage(Text.translatable("message.surrogate.crawler.coupled_helm").formatted(Formatting.GRAY), true);
				}
				return;
			}
			hull.setControls(seat.throttle, seat.steer);
		} else {
			CrawlerDocking.tick(server, hull, seat.throttle, seat.steer, lock, seat.docking, player);
		}
	}

	private static float deadzone(float input) {
		return Math.abs(input) < 0.2f ? 0f : input;
	}

	private static void feedLifeSupport(ServerWorld cabin, BlockPos origin, CrawlerEntity hull) {
		if (hull.getEnergy() <= 0) return;
		if (cabin.getBlockEntity(origin.add(CrawlerRoom.LIFE_SUPPORT)) instanceof LifeSupportBlockEntity scrubber) {
			int accepted = scrubber.addEnergy(Math.min(hull.getEnergy(), 8));
			if (accepted > 0) hull.drainEnergy(accepted);
		}
	}

	private static void sendState(ServerPlayerEntity player, @Nullable CrawlerEntity hull, @Nullable Seat seat) {
		int kind = seat == null ? SEAT_NONE : seat.kind;
		if (hull == null) {
			ServerPlayNetworking.send(player, new CrawlerPayloads.State(kind, true, 0f, 0f, 0, false, false, 0f, 0f, false, 0));
			return;
		}
		CrawlerDocking.Status status = CrawlerDocking.status(player.server, hull);
		ServerPlayNetworking.send(player, new CrawlerPayloads.State(kind, false, hull.getHeading(), hull.getSpeed(), Math.round(hull.getEnergyFraction() * 100f),
				hull.isDocked(), status.collar(), status.offset(), status.angle(), hull.hasCladding(),
				AcidRain.hullPercent(player.server, hull)));
	}

	// ------------------------------------------------------------------ the chassis bay

	/** A chassis deployed beside the hull from the bay; the cabin's chair is keyed to it. */
	public static boolean deploy(ServerPlayerEntity player, ServerWorld cabin, BlockPos bay, ItemStack chassis) {
		MinecraftServer server = player.server;
		CrawlerInteriors.Slot slot = slotAt(server, bay);
		CrawlerEntity hull = hullOf(server, slot);
		if (hull == null) {
			Surrogate.LOGGER.info("Crawler bay: deploy refused, hull not loaded (slot {})", slot == null ? "none" : slot.index);
			player.sendMessage(Text.translatable("message.surrogate.crawler.hull_far").formatted(Formatting.GRAY), true);
			return false;
		}
		ServerWorld overworld = server.getOverworld();
		RobotEntity robot = RobotChassisItem.createRobot(overworld, chassis);
		if (robot == null) return false;
		for (Vec3d offset : new Vec3d[]{new Vec3d(3.5, 0.0, 0.0), new Vec3d(-3.5, 0.0, 0.0), new Vec3d(0.0, 0.0, -(CrawlerEntity.HALF_LENGTH + 2.0))}) {
			Vec3d at = hull.getPos().add(hull.local(offset.x, offset.y, offset.z));
			robot.refreshPositionAndAngles(at.x, at.y, at.z, hull.getYaw(), 0f);
			robot.setBodyYaw(hull.getYaw());
			robot.setHeadYaw(hull.getYaw());
			if (!overworld.isSpaceEmpty(robot)) continue;
			if (!overworld.spawnEntity(robot)) break;
			RobotRegistry.get(server).update(robot);
			BlockPos origin = CrawlerInteriors.origin(slot.index);
			BlockPos chairPos = origin.add(CrawlerRoom.CHAIR);
			if (cabin.getBlockEntity(chairPos) instanceof DiveChairBlockEntity chair) {
				chair.setLink(robot.getUuid(), robot.hasCustomName() ? robot.getCustomName().getString() : "Chassis");
				Surrogate.LOGGER.info("Crawler bay: keyed the chair at {} to {}", chairPos.toShortString(), robot.getUuid());
			} else {
				Surrogate.LOGGER.warn("Crawler bay: no chair at {} in {} ({})", chairPos.toShortString(), cabin.getRegistryKey().getValue(), cabin.getBlockEntity(chairPos));
			}
			overworld.playSound(null, hull.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
			player.sendMessage(Text.translatable("message.surrogate.crawler.deployed").formatted(Formatting.AQUA), true);
			return true;
		}
		player.sendMessage(Text.translatable("message.surrogate.crawler.no_room_outside").formatted(Formatting.RED), true);
		return false;
	}

	/** A piloted chassis using the hull is taken back aboard: its pilot wakes in their chair, the chassis goes in the bay. */
	public static boolean recall(ServerPlayerEntity pilot, CrawlerEntity hull) {
		if (!(pilot.getVehicle() instanceof RobotEntity robot)) return false;
		MinecraftServer server = pilot.server;
		PilotManager.disconnect(pilot, false, Text.translatable("message.surrogate.crawler.recalled"), false);
		ItemStack stack = RobotChassisItem.toStack(robot);
		RobotRegistry.get(server).remove(robot.getUuid());
		robot.discard();
		ServerWorld cabin = CrawlerDimension.world(server);
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).forHull(hull.getUuid());
		if (cabin != null && slot != null && slot.built) {
			BlockPos bay = CrawlerInteriors.origin(slot.index).add(CrawlerRoom.BAY);
			if (cabin.getBlockEntity(bay) instanceof CrawlerBayBlockEntity bayEntity && bayEntity.getStack().isEmpty()) {
				bayEntity.setStack(stack);
				return true;
			}
		}
		if (!pilot.getInventory().insertStack(stack)) pilot.dropItem(stack, false);
		return true;
	}

	/** Everyone in a cabin right now; for tests and the log. */
	public static int occupants(MinecraftServer server, int slot) {
		ServerWorld cabin = CrawlerDimension.world(server);
		if (cabin == null) return 0;
		Box bounds = CrawlerRoom.bounds(CrawlerInteriors.origin(slot));
		int count = 0;
		for (ServerPlayerEntity player : cabin.getPlayers()) if (bounds.contains(player.getPos())) count++;
		return count;
	}

	static {
		Surrogate.LOGGER.debug("CrawlerInterior ready");
	}
}
