package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.crawler.CrawlerInteriors;
import dev.psyda.surrogate.crawler.CrawlerRoom;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.TerminalPayload;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import dev.psyda.surrogate.prologue.SiteTwo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
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
		/** The researcher has handed the crawler blueprint over. */
		public boolean blueprint;
		public int nextLine;

		Site(int character, int x, int z) {
			this.character = character;
			this.x = x;
			this.z = z;
		}

		public Survivor survivor() {
			return Survivor.byId(character);
		}

		public BlockPos origin() {
			return new BlockPos(x, y, z);
		}
	}

	private final List<Site> sites = new ArrayList<>();
	private long nextRadio = -1L;
	private int radioCursor;

	public static SurvivorManager get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_survivors");
	}

	public static void registerEvents() {
		ServerChunkEvents.CHUNK_LOAD.register(SurvivorManager::onChunkLoad);
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
	 * sector, each on valley floor the crawler can reach from the pod ({@link Valleys}), with a cliff behind
	 * it when one is close. The first is the researcher's and sits close enough for a chassis to walk to.
	 * A sector with no such floor gets its shelter at the old random spot.
	 */
	public void createSites(ServerWorld world, BlockPos origin) {
		if (!sites.isEmpty()) return;
		SurrogateConfig cfg = Surrogate.CONFIG;
		Random random = world.getRandom();
		int count = Math.max(0, Math.min(cfg.survivorCount, 16));
		Survivor[] roster = Survivor.values();
		Valleys.Reach reach = count == 0 ? null : Valleys.reach(world, origin.getX(), origin.getZ(), cfg.survivorMaxDistance + 64);
		Predicate<BlockPos> apart = pos -> sites.stream().noneMatch(site -> Math.abs(site.x - pos.getX()) < 120 && Math.abs(site.z - pos.getZ()) < 120);
		for (int i = 0; i < count; i++) {
			double angle = (2.0 * Math.PI * i) / Math.max(1, count);
			int min = i == 0 ? cfg.firstSurvivorMinDistance : cfg.survivorMinDistance;
			int max = i == 0 ? cfg.firstSurvivorMaxDistance : cfg.survivorMaxDistance;
			BlockPos pick = Valleys.pickSite(world, reach, origin, random, angle, 0.45, min, max, 48, apart);
			// A sector walled off by a table: anywhere on the reachable floor beats an unreachable spot.
			if (pick == null) pick = Valleys.pickSite(world, reach, origin, random, angle, Math.PI, min, max, 96, apart);
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
			sites.add(new Site(i % roster.length, x, z));
		}
		nextRadio = world.getTime() + 1200L;
		markDirty();
		for (Site site : sites) {
			Surrogate.LOGGER.info("Survivor shelter for {} at x={}, z={}", site.survivor().key(), site.x, site.z);
		}
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
				SurvivorShelter.build(world, site);
				site.built = true;
				manager.markDirty();
			}
		}
		if (manager.sites.isEmpty() || manager.nextRadio < 0) return;
		if (world.getTime() >= manager.nextRadio) {
			manager.nextRadio = world.getTime() + Math.max(200, Surrogate.CONFIG.radioIntervalTicks);
			manager.broadcastNext(server);
		}
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
		if (site == researcher() && !site.blueprint) {
			site.blueprint = true;
			markDirty();
			ItemStack blueprint = new ItemStack(ModItems.CRAWLER_BLUEPRINT);
			if (!player.giveItemStack(blueprint)) player.dropItem(blueprint, false);
			player.sendMessage(format(who, who.line("port.blueprint")), false);
			player.sendMessage(Text.translatable("message.surrogate.survivor.blueprint", who.displayName()).formatted(Formatting.GOLD), false);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
			Surrogate.LOGGER.info("{} handed the crawler blueprint to {}", who.key(), player.getName().getString());
		} else if (site.rescued) {
			player.sendMessage(format(who, who.line("port.empty")), false);
		} else {
			player.sendMessage(format(who, who.line("port.again")), false);
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
		// Unrescued survivors talk first; once everyone is safe the channel turns to small talk, less often.
		List<Site> waiting = sites.stream().filter(site -> !site.rescued && !site.aboard).toList();
		List<Site> pool = waiting.isEmpty() ? sites : waiting;
		if (waiting.isEmpty() && radioCursor % 3 != 0) {
			radioCursor++;
			return;
		}
		Site site = pool.get(Math.floorMod(radioCursor++, pool.size()));
		Survivor who = site.survivor();
		Text line;
		if (site.rescued || site.aboard) {
			line = who.line("safe");
		} else {
			int index = Math.floorMod(site.nextLine++, who.radioLines()) + 1;
			line = who.line("radio." + index);
		}
		markDirty();
		broadcast(server, who, line);
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
			String strengthKey = distance < 60 ? "very_strong" : distance < 250 ? "strong" : distance < 600 ? "weak" : "faint";
			Text strength = Text.translatable("message.surrogate.radio.strength." + strengthKey);
			Text bearing = Text.translatable("message.surrogate.radio.bearing." + bearing(dx, dz));
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
			list.add(entry);
		}
		nbt.put("Sites", list);
		nbt.putLong("NextRadio", nextRadio);
		nbt.putInt("Cursor", radioCursor);
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
			manager.sites.add(site);
		}
		manager.nextRadio = nbt.contains("NextRadio") ? nbt.getLong("NextRadio") : -1L;
		manager.radioCursor = nbt.getInt("Cursor");
		return manager;
	}
}
