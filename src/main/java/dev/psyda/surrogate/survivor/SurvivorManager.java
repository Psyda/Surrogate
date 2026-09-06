package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.assay.AssayState;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.crawler.CrawlerInteriors;
import dev.psyda.surrogate.crawler.CrawlerRoom;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.hazard.Hazards;
import dev.psyda.surrogate.network.TerminalPayload;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import dev.psyda.surrogate.prologue.SiteTwo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Places the survivors' shelters around spawn, builds each one the first time its chunk loads, and runs the
 * radio: every so often one of them calls out to anyone carrying a field radio or sitting in a chassis.
 *
 * <p>The first shelter is the researcher's, within a chassis walk of the pod. Every shelter has a chassis
 * port on its outside wall: a chassis docks there and talks to whoever is inside through a terminal, and
 * the researcher hands over the crawler blueprint that way. Every shelter also has a collar: a crawler
 * that couples to it takes the survivor aboard, and coupling at home puts them into the pod. That is a
 * rescue.
 */
public class SurvivorManager extends PersistentState {
	private static final Type<SurvivorManager> TYPE = new Type<>(SurvivorManager::new, SurvivorManager::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static final class Site {
		public final int character;
		public final int x;
		public final int z;
		/** The shelter's floor level, known once it is built. */
		public int y;
		public boolean built;
		/** Brought home. */
		public boolean rescued;
		/** Riding in a crawler cabin right now. */
		public boolean aboard;
		/** The survivor has sent their plans across the port: the blueprint, a schematic, a pattern. */
		public boolean blueprint;
		public int nextLine;
		/** In the acid belt, so the shelter is built from the ceramic kit rather than bare plating. */
		public boolean belt;
		/** No shelter here at all: Novak's crawler on the floor of the Rift. */
		public boolean wreck;
		/** Scrubber output, 1 while it holds. It only ever falls, and only once the ship has gone. */
		public double scrubber = 1.0;

		Site(int character, int x, int z) {
			this.character = character;
			this.x = x;
			this.z = z;
		}

		public Survivor survivor() {
			return Survivor.byId(character);
		}

		/**
		 * Whether this site is meant to be out of the crawler's reach until act five bridges the Rift. The
		 * terrain scan checks against this rather than against "drivable", because for these three being
		 * unreachable is the design and not a fault.
		 */
		public boolean gated() {
			return farSide(survivor());
		}

		public BlockPos origin() {
			return new BlockPos(x, y, z);
		}
	}

	private final List<Site> sites = new ArrayList<>();
	private long nextRadio = -1L;
	private int radioCursor;
	/** The last day the shelters lost a little scrubber output; -1 until the ship leaves. */
	private long declineDay = -1L;

	/**
	 * Relay masts that are up with power behind them, in world coordinates. Masts add and remove themselves
	 * as they come on and go off, so the range test never has to go looking for one.
	 */
	private static final Set<BlockPos> MASTS = ConcurrentHashMap.newKeySet();

	public static SurvivorManager get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_survivors");
	}

	public static void registerEvents() {
		ServerChunkEvents.CHUNK_LOAD.register(SurvivorManager::onChunkLoad);
		// Masts are remembered in memory only, so a restart starts from nothing rather than from ghosts.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MASTS.clear());
	}

	/** A relay mast reporting in: on when it has power and its column is up, off when either goes away. */
	public static void mastPowered(BlockPos pos, boolean on) {
		if (on) MASTS.add(pos.toImmutable());
		else MASTS.remove(pos);
	}

	public List<Site> sites() {
		return sites;
	}

	/** The first shelter: the researcher who has the crawler blueprint. */
	@Nullable
	public Site researcher() {
		return sites.isEmpty() ? null : sites.get(0);
	}

	/** The shelter this block belongs to, if it is within a few blocks of one. */
	@Nullable
	public Site siteNear(BlockPos pos, int within) {
		for (Site site : sites) {
			if (Math.abs(site.x - pos.getX()) <= within && Math.abs(site.z - pos.getZ()) <= within) return site;
		}
		return null;
	}

	/**
	 * Scatter the shelters around the habitat once, on the first join of a toxic world: one per compass
	 * sector, on valley floor, with a cliff behind it when one is close. Where each one goes is the campaign
	 * in map form. Okafor and Sorensen sit on the near side within a drive; Tanaka goes past the mast's
	 * reach, so he is a carrier and no voice until a relay covers him; Brandt and Reyes go beyond the Rift,
	 * off the floor the crawler can reach from the pod and inside the acid belt where the ground allows it;
	 * Novak is not in a shelter at all but on the floor of the Rift itself. A sector with nothing that fits
	 * gets its shelter at the old random spot.
	 */
	public void createSites(ServerWorld world, BlockPos origin) {
		if (!sites.isEmpty()) return;
		SurrogateConfig cfg = Surrogate.CONFIG;
		// Seeded off the world, not off the world's own tick-to-tick random: the campaign is designed against
		// one map (docs/DESIGN-campaign.md, "The map") and a map whose shelters move between two boots of the
		// same seed is not one map. Two runs of the seed search agreed and two runs of the game did not.
		Random random = Random.create(world.getSeed() ^ 0x5348454CL);
		int count = Math.max(0, Math.min(cfg.survivorCount, 16));
		Survivor[] roster = Survivor.values();
		// The fill has to cover the far sites too, or "off the edge of the grid" reads as "behind the Rift"
		// and Brandt ends up somewhere the crawler could have driven to on day one.
		int radius = Math.max(Math.max(cfg.survivorMaxDistance, cfg.outOfRangeSurvivorMaxDistance), cfg.farSurvivorMaxDistance) + 128;
		Valleys.Reach reach = count == 0 ? null : Valleys.reach(world, origin.getX(), origin.getZ(), radius);
		Predicate<BlockPos> apart = pos -> sites.stream().noneMatch(site -> Math.abs(site.x - pos.getX()) < 120 && Math.abs(site.z - pos.getZ()) < 120);
		for (int i = 0; i < count; i++) {
			Survivor who = roster[i % roster.length];
			double angle = (2.0 * Math.PI * i) / Math.max(1, count);
			int min = minDistance(cfg, who);
			int max = maxDistance(cfg, who);
			BlockPos pick;
			if (who == Survivor.NOVAK) {
				pick = pickRift(world, origin, angle, cfg.survivorMinDistance, max);
				if (pick == null) pick = pickBeyond(world, reach, origin, random, angle, min, max, apart);
			} else if (farSide(who)) {
				pick = pickBeyond(world, reach, origin, random, angle, min, max, apart);
			} else {
				// The belt is act five's country and nobody on the near side lives in it: Sorensen four
				// hundred blocks from the pod with acid rain on his roof would hand the player the belt
				// before anyone has mentioned it, and would want cladding to visit a man in act one.
				Predicate<BlockPos> dry = apart.and(pos -> !Valleys.inBelt(world, pos.getX(), pos.getZ()));
				pick = Valleys.pickSite(world, reach, origin, random, angle, 0.45, min, max, 48, dry);
				if (pick == null) pick = Valleys.pickSite(world, reach, origin, random, angle, Math.PI, min, max, 96, dry);
				// A sector walled off by a table: anywhere on the reachable floor beats an unreachable spot.
				if (pick == null) pick = Valleys.pickSite(world, reach, origin, random, angle, Math.PI, min, max, 96, apart);
			}
			int x;
			int z;
			if (pick != null) {
				x = pick.getX();
				z = pick.getZ();
			} else {
				double jittered = angle + random.nextDouble() * 0.6 - 0.3;
				double distance = min + random.nextDouble() * Math.max(1, max - min);
				x = origin.getX() + (int) Math.round(Math.cos(jittered) * distance);
				z = origin.getZ() + (int) Math.round(Math.sin(jittered) * distance);
				Surrogate.LOGGER.info("Valleys: no drivable floor for shelter {} in its sector", i);
			}
			Site site = new Site(who.ordinal(), x, z);
			site.belt = Valleys.inBelt(world, x, z);
			site.wreck = who == Survivor.NOVAK;
			sites.add(site);
		}
		nextRadio = world.getTime() + 1200L;
		markDirty();
		for (Site site : sites) {
			Surrogate.LOGGER.info("Survivor site for {} at x={}, z={}{}{}", site.survivor().key(), site.x, site.z,
					site.belt ? " (in the belt)" : "", site.wreck ? " (a wreck, no shelter)" : "");
		}
	}

	/** Brandt and Reyes: past the Rift, on floor the pod's crawler cannot reach until somebody bridges it. */
	private static boolean farSide(Survivor who) {
		return who == Survivor.BRANDT || who == Survivor.REYES || who == Survivor.NOVAK;
	}

	private static int minDistance(SurrogateConfig cfg, Survivor who) {
		return switch (who) {
			case OKAFOR -> cfg.firstSurvivorMinDistance;
			case TANAKA -> cfg.outOfRangeSurvivorMinDistance;
			case BRANDT, REYES, NOVAK -> cfg.farSurvivorMinDistance;
			default -> cfg.survivorMinDistance;
		};
	}

	private static int maxDistance(SurrogateConfig cfg, Survivor who) {
		return switch (who) {
			case OKAFOR -> cfg.firstSurvivorMaxDistance;
			case TANAKA -> cfg.outOfRangeSurvivorMaxDistance;
			case BRANDT, REYES, NOVAK -> cfg.farSurvivorMaxDistance;
			default -> cfg.survivorMaxDistance;
		};
	}

	/**
	 * Floor the pod cannot drive to, in the belt if the belt reaches out this way. Three passes: in the belt
	 * on this bearing, in the belt anywhere, then any unreachable floor at all, because a shelter the player
	 * can find beats a perfect one that does not exist.
	 */
	@Nullable
	private static BlockPos pickBeyond(ServerWorld world, @Nullable Valleys.Reach reach, BlockPos origin, Random random,
									   double angle, int min, int max, Predicate<BlockPos> apart) {
		Predicate<BlockPos> beyond = pos -> apart.test(pos)
				&& (reach == null || (reach.inRange(pos.getX(), pos.getZ()) && !reach.contains(pos.getX(), pos.getZ())));
		Predicate<BlockPos> inBelt = pos -> beyond.test(pos) && Valleys.inBelt(world, pos.getX(), pos.getZ());
		BlockPos pick = Valleys.pickSite(world, null, origin, random, angle, 0.6, min, max, 96, inBelt);
		if (pick == null) pick = Valleys.pickSite(world, null, origin, random, angle, Math.PI, min, max, 128, inBelt);
		if (pick == null) pick = Valleys.pickSite(world, null, origin, random, angle, Math.PI, min, max, 128, beyond);
		return pick;
	}

	/**
	 * The floor of the Rift on this bearing: walk out along the sector until the chasm opens under the line.
	 * Sweeps a little either side of the bearing, because the Rift wanders.
	 */
	@Nullable
	private static BlockPos pickRift(ServerWorld world, BlockPos origin, double angle, int min, int max) {
		for (double sweep : new double[]{0.0, 0.12, -0.12, 0.3, -0.3, 0.6, -0.6}) {
			for (int d = min; d <= max; d += 8) {
				int x = origin.getX() + (int) Math.round(Math.cos(angle + sweep) * d);
				int z = origin.getZ() + (int) Math.round(Math.sin(angle + sweep) * d);
				if (Valleys.inRift(world, x, z)) return new BlockPos(x, 0, z);
			}
		}
		return null;
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		SurvivorManager manager = get(world.getServer());
		if (manager.sites.isEmpty()) return;
		ChunkPos pos = chunk.getPos();
		for (Site site : manager.sites) {
			if (!site.built && site.x >> 4 == pos.x && site.z >> 4 == pos.z) {
				// Build on the next tick, once the chunk is fully in the world.
				manager.pending.add(site);
			}
		}
	}

	private final List<Site> pending = new ArrayList<>();

	public static void tick(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		SurvivorManager manager = get(server);
		if (!manager.pending.isEmpty()) {
			List<Site> ready = new ArrayList<>(manager.pending);
			manager.pending.clear();
			for (Site site : ready) {
				if (site.built) continue;
				if (!world.isChunkLoaded(site.x >> 4, site.z >> 4)) continue;
				if (site.wreck) SurvivorShelter.buildWreck(world, site);
				else SurvivorShelter.build(world, site);
				site.built = true;
				manager.markDirty();
			}
		}
		manager.declineTick(server, world);
		if (manager.sites.isEmpty() || manager.nextRadio < 0) return;
		if (world.getTime() >= manager.nextRadio) {
			manager.nextRadio = world.getTime() + Math.max(200, Surrogate.CONFIG.radioIntervalTicks);
			manager.broadcastNext(server);
		}
	}

	// ------------------------------------------------------------------ the decline

	/**
	 * Once the ship has taken the payload and gone, every shelter still holding out loses a little scrubber
	 * output a day. It never kills anyone. It is a number going the wrong way in six places at once, and it
	 * is the reason act four happens.
	 */
	private void declineTick(MinecraftServer server, ServerWorld world) {
		if (sites.isEmpty()) return;
		if (AssayState.get(server).stage < AssayState.STAGE_DONE) return;
		long day = world.getTimeOfDay() / 24000L;
		if (declineDay < 0) {
			// The day the ship left is the day everything was still fine.
			declineDay = day;
			markDirty();
			return;
		}
		if (day <= declineDay) return;
		double loss = Math.max(0.0, Surrogate.CONFIG.shelterDeclinePerDay) * (day - declineDay);
		double floor = Math.min(1.0, Math.max(0.0, Surrogate.CONFIG.shelterDeclineFloor));
		declineDay = day;
		for (Site site : sites) {
			if (site.rescued || site.aboard || site.wreck) continue;
			site.scrubber = Math.max(floor, site.scrubber - loss);
		}
		markDirty();
	}

	/** True once the shelters have started losing output, which is what the radio and the ports talk about. */
	public boolean declining() {
		return declineDay >= 0;
	}

	/** What is left of a shelter's scrubber, 1 while it holds. The mission board reads this. */
	public double scrubber(Survivor who) {
		for (Site site : sites) {
			if (site.survivor() == who) return site.scrubber;
		}
		return 1.0;
	}

	// ------------------------------------------------------------------ the chassis port

	/**
	 * A chassis at a shelter's port. The survivor inside greets it on the terminal; the researcher sends the
	 * crawler blueprint over the first time. The terminal opens with their pages on it.
	 */
	public void portUsed(ServerPlayerEntity player, Site site) {
		Survivor who = site.survivor();
		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.5f, 1.9f);
		player.sendMessage(format(who, who.line("port.greet")), false);
		ItemStack handover = who.handover();
		if (!site.blueprint && !handover.isEmpty()) {
			site.blueprint = true;
			markDirty();
			// giveItemStack empties the stack it inserts, so what it was has to be read off it first: the
			// player was being told the researcher had sent them Air, and the log said the same.
			Text what = handover.getName();
			Item sent = handover.getItem();
			if (!player.giveItemStack(handover)) player.dropItem(handover, false);
			player.sendMessage(format(who, who.line("port.blueprint")), false);
			player.sendMessage(Text.translatable("message.surrogate.survivor.blueprint", who.displayName(), what).formatted(Formatting.GOLD), false);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
			Surrogate.LOGGER.info("{} handed {} to {}", who.key(), sent, player.getName().getString());
		} else if (site.rescued) {
			player.sendMessage(format(who, who.line("port.empty")), false);
		} else {
			player.sendMessage(format(who, who.line("port.again")), false);
		}
		// The port is the terminal, so the shelter's own falling number goes across it with everything else.
		if (declining() && !site.rescued) {
			int percent = (int) Math.round(site.scrubber * 100.0);
			player.sendMessage(Text.translatable("message.surrogate.shelter.scrubber", percent)
					.formatted(percent < 60 ? Formatting.RED : Formatting.GOLD), false);
		}
		ServerPlayNetworking.send(player, new TerminalPayload("shelter_" + who.key()));
	}

	// ------------------------------------------------------------------ the collar

	/** A crawler has coupled to this shelter: the survivor comes aboard and rides in the cabin. */
	public void board(MinecraftServer server, CrawlerEntity hull, Site site) {
		if (site.rescued || site.aboard) return;
		ServerWorld overworld = server.getOverworld();
		ServerWorld cabin = CrawlerDimension.world(server);
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).forHull(hull.getUuid());
		if (cabin == null || slot == null || !slot.built) return;
		Survivor who = site.survivor();
		SurvivorEntity survivor = null;
		for (SurvivorEntity candidate : overworld.getEntitiesByClass(SurvivorEntity.class, new Box(site.origin()).expand(12.0), e -> e.getCharacter() == who)) {
			survivor = candidate;
			break;
		}
		if (survivor == null) {
			Surrogate.LOGGER.warn("Crawler coupled at {}'s shelter but nobody is home", who.key());
			return;
		}
		Vec3d at = Vec3d.ofBottomCenter(CrawlerInteriors.origin(slot.index).add(CrawlerRoom.PASSENGER));
		survivor.teleport(cabin, at.x, at.y, at.z, Set.of(), 90f, 0f);
		site.aboard = true;
		markDirty();
		Text message = format(who, who.line("aboard"));
		for (ServerPlayerEntity player : CrawlerInterior.playersAboard(server, hull)) {
			player.sendMessage(message, false);
			player.sendMessage(Text.translatable("message.surrogate.survivor.aboard", who.displayName()).formatted(Formatting.GOLD), true);
		}
		Surrogate.LOGGER.info("{} boarded the crawler", who.key());
	}

	/** The crawler has coupled at home: everyone riding in its cabin steps through into the pod. Rescued. */
	public void disembark(MinecraftServer server, CrawlerEntity hull, BlockPos door) {
		ServerWorld overworld = server.getOverworld();
		ServerWorld cabin = CrawlerDimension.world(server);
		CrawlerInteriors.Slot slot = CrawlerInteriors.get(server).forHull(hull.getUuid());
		if (cabin == null || slot == null || !slot.built) return;
		Box room = CrawlerRoom.room(CrawlerInteriors.origin(slot.index));
		int n = 0;
		for (SurvivorEntity survivor : cabin.getEntitiesByClass(SurvivorEntity.class, room, e -> true)) {
			Survivor who = survivor.getCharacter();
			BlockPos spot = door.east().south(n++);
			Vec3d at = Vec3d.ofBottomCenter(spot);
			survivor.teleport(overworld, at.x, at.y, at.z, Set.of(), -90f, 0f);
			for (Site site : sites) {
				if (site.survivor() == who) {
					site.aboard = false;
					site.rescued = true;
				}
			}
			markDirty();
			Text message = format(who, who.line("home"));
			for (ServerPlayerEntity player : CrawlerInterior.playersAboard(server, hull)) {
				player.sendMessage(message, false);
				player.sendMessage(Text.translatable("message.surrogate.survivor.home", who.displayName()).formatted(Formatting.GOLD), true);
			}
			broadcast(server, who, who.line("home_radio"));
			Surrogate.LOGGER.info("{} rescued: home at {}", who.key(), spot.toShortString());
		}
	}

	// ------------------------------------------------------------------ the radio

	/**
	 * Puts one survivor on the air for one player right now, then restarts the regular schedule after it.
	 * Used by the opening sequence for the first voice the player ever hears. @return the lang key used
	 */
	public String call(MinecraftServer server, ServerPlayerEntity player, Survivor who) {
		Site site = sites.stream().filter(s -> s.survivor() == who).findFirst().orElse(null);
		String key;
		if (site == null || site.rescued) {
			key = "survivor.surrogate." + who.key() + ".radio.1";
		} else {
			int index = Math.floorMod(site.nextLine++, who.radioLines()) + 1;
			key = "survivor.surrogate." + who.key() + ".radio." + index;
		}
		nextRadio = server.getOverworld().getTime() + Math.max(200, Surrogate.CONFIG.radioIntervalTicks);
		markDirty();
		deliver(player, format(who, Text.translatable(key)));
		return key;
	}

	private void broadcastNext(MinecraftServer server) {
		if (sites.isEmpty()) return;
		// The opening has its own script for the radio; the survivors wait their turn.
		if (Prologue.isRunning()) return;
		// A magnetic storm takes the band with it, and whatever they were going to say is simply lost.
		if (Hazards.radioNoise(server.getOverworld())) return;
		// Unrescued survivors talk first; once everyone is safe the channel turns to small talk, less often.
		List<Site> waiting = sites.stream().filter(site -> !site.rescued && !site.aboard).toList();
		List<Site> pool = waiting.isEmpty() ? sites : waiting;
		if (waiting.isEmpty() && radioCursor % 3 != 0) {
			radioCursor++;
			return;
		}
		Site site = pool.get(Math.floorMod(radioCursor++, pool.size()));
		Survivor who = site.survivor();
		if (site.rescued || site.aboard) {
			markDirty();
			broadcast(server, who, who.line("safe"));
			return;
		}
		int index = Math.floorMod(site.nextLine++, who.radioLines()) + 1;
		// Once the scrubbers start going, the small talk stops and they talk about the air. There are as many
		// decline lines as radio lines, so the round robin does not change.
		Text line = declining() ? who.line("decline." + index) : who.line("radio." + index);
		markDirty();
		broadcastFrom(server, site, line);
	}

	/**
	 * A call from one shelter, to everyone who can hear it. A player out past the band gets the carrier and
	 * no words: something is transmitting on a bearing, and that is all anyone can say about it.
	 */
	private void broadcastFrom(MinecraftServer server, Site site, Text line) {
		Survivor who = site.survivor();
		Text message = format(who, line);
		Text carrier = Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(Text.translatable("message.surrogate.radio.carrier_line").formatted(Formatting.DARK_GRAY));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!hasRadio(player)) continue;
			deliver(player, heard(player, site.x, site.z) ? message : carrier);
		}
	}

	public void broadcast(MinecraftServer server, Survivor who, Text line) {
		Text message = format(who, line);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (hasRadio(player)) deliver(player, message);
		}
	}

	private static Text format(Survivor who, Text line) {
		return Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(who.displayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(line.copy().formatted(Formatting.WHITE));
	}

	private static void deliver(ServerPlayerEntity player, Text message) {
		player.sendMessage(message, false);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 0.25f, 0.5f);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.4f, 1.8f);
	}

	/** A field radio in the pack, the one built into every chassis, or a crawler cabin. */
	public static boolean hasRadio(PlayerEntity player) {
		if (RobotEntity.isPiloting(player)) return true;
		if (CrawlerDimension.isCabin(player.getWorld())) return true;
		return player.getInventory().containsAny(stack -> stack.isOf(ModItems.FIELD_RADIO));
	}

	/** How far the band reaches from where this player is: the pod's own range, and a relay module on top. */
	public static int radioRange(PlayerEntity player) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		int range = cfg.radioRange;
		if (player.getVehicle() instanceof RobotEntity chassis && chassis.isPilot(player) && chassis.hasModule(RobotModule.RELAY)) {
			range += cfg.relayModuleRangeBonus;
		}
		return range;
	}

	/**
	 * Whether a signal from this column reaches the listener with words on it rather than only a bearing:
	 * inside the band's range, or with a powered relay mast standing near either end of the hop.
	 */
	public static boolean heard(PlayerEntity listener, int x, int z) {
		double dx = x + 0.5 - listener.getX();
		double dz = z + 0.5 - listener.getZ();
		double range = radioRange(listener);
		if (dx * dx + dz * dz <= range * range) return true;
		BlockPos here = listener.getBlockPos();
		return relayed(here, new BlockPos(x, here.getY(), z));
	}

	/** A mast within {@code relayMastRange} of either end carries the band the rest of the way. */
	public static boolean relayed(BlockPos from, BlockPos to) {
		if (MASTS.isEmpty()) return false;
		long range = Surrogate.CONFIG.relayMastRange;
		long squared = range * range;
		for (BlockPos mast : MASTS) {
			if (horizontalSquared(mast, from) <= squared || horizontalSquared(mast, to) <= squared) return true;
		}
		return false;
	}

	private static long horizontalSquared(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** A survivor has been given what they asked for: the shelter is on its feet again, and says so. */
	public void onHelped(MinecraftServer server, Survivor who) {
		broadcast(server, who, who.line("rescued_radio"));
	}

	/** What the field radio hears from where the player stands. */
	public void report(ServerPlayerEntity player) {
		Text siteTwo = SiteTwo.signal(player, HabitatState.get(player.server));
		if (sites.isEmpty() && siteTwo == null) {
			player.sendMessage(Text.translatable("message.surrogate.radio.silent").formatted(Formatting.GRAY), false);
			return;
		}
		player.sendMessage(Text.translatable("message.surrogate.radio.header").formatted(Formatting.DARK_AQUA), false);
		if (siteTwo != null) player.sendMessage(siteTwo, false);
		for (Site site : sites) {
			double dx = site.x + 0.5 - player.getX();
			double dz = site.z + 0.5 - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			Text bearing = Text.translatable("message.surrogate.radio.bearing." + bearing(dx, dz));
			// Past the band there is no fix and no voice, only the direction the needle points.
			if (!heard(player, site.x, site.z)) {
				player.sendMessage(Text.translatable("message.surrogate.radio.carrier", site.survivor().displayName(), bearing)
						.formatted(Formatting.DARK_GRAY), false);
				continue;
			}
			String strengthKey = distance < 60 ? "very_strong" : distance < 250 ? "strong" : distance < 600 ? "weak" : "faint";
			Text strength = Text.translatable("message.surrogate.radio.strength." + strengthKey);
			int rounded = (int) (Math.round(distance / 50.0) * 50);
			Text status = site.rescued ? Text.translatable("message.surrogate.radio.safe") : Text.translatable("message.surrogate.radio.distress");
			player.sendMessage(Text.translatable("message.surrogate.radio.signal", site.survivor().displayName(), strength, rounded, bearing, status)
					.formatted(site.rescued ? Formatting.GREEN : distance < 250 ? Formatting.YELLOW : Formatting.GRAY), false);
		}
	}

	public static String bearing(double dx, double dz) {
		// Minecraft: +z is south, +x is east.
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		if (angle < 0) angle += 360.0;
		String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
		return names[(int) Math.round(angle / 45.0) % 8];
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (Site site : sites) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("Character", site.character);
			entry.putInt("X", site.x);
			entry.putInt("Y", site.y);
			entry.putInt("Z", site.z);
			entry.putBoolean("Built", site.built);
			entry.putBoolean("Rescued", site.rescued);
			entry.putBoolean("Aboard", site.aboard);
			entry.putBoolean("Blueprint", site.blueprint);
			entry.putInt("NextLine", site.nextLine);
			entry.putBoolean("Belt", site.belt);
			entry.putBoolean("Wreck", site.wreck);
			entry.putDouble("Scrubber", site.scrubber);
			list.add(entry);
		}
		nbt.put("Sites", list);
		nbt.putLong("NextRadio", nextRadio);
		nbt.putInt("Cursor", radioCursor);
		nbt.putLong("DeclineDay", declineDay);
		return nbt;
	}

	private static SurvivorManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		SurvivorManager manager = new SurvivorManager();
		NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			Site site = new Site(entry.getInt("Character"), entry.getInt("X"), entry.getInt("Z"));
			site.y = entry.getInt("Y");
			site.built = entry.getBoolean("Built");
			site.rescued = entry.getBoolean("Rescued");
			site.aboard = entry.getBoolean("Aboard");
			site.blueprint = entry.getBoolean("Blueprint");
			site.nextLine = entry.getInt("NextLine");
			site.belt = entry.getBoolean("Belt");
			site.wreck = entry.getBoolean("Wreck");
			// A missing double reads 0, which would mean a dead scrubber on every old save.
			site.scrubber = entry.contains("Scrubber") ? entry.getDouble("Scrubber") : 1.0;
			manager.sites.add(site);
		}
		manager.nextRadio = nbt.contains("NextRadio") ? nbt.getLong("NextRadio") : -1L;
		manager.radioCursor = nbt.getInt("Cursor");
		manager.declineDay = nbt.contains("DeclineDay") ? nbt.getLong("DeclineDay") : -1L;
		return manager;
	}
}
