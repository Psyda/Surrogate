package dev.psyda.surrogate.research;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.assay.Assay;
import dev.psyda.surrogate.assay.AssayState;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.prologue.Beat;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorEntity;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.survivor.SurvivorShelter;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Acts one and two: the three research runs Okafor and Sorensen ask for, the errands nobody asks for, and the
 * drive out to the one man the pod's mast cannot hear. It is the arc where the survivors stop being a chat
 * channel and start being a system that wants real things and pays in hardware.
 *
 * <p>Contract Seven waits behind it. Everything here has a timeout and somebody works around it out loud, so
 * a player who ignores the whole of it still ends up holding the relay module and the damper, late and with
 * a worse opinion of them attached to it.
 */
public final class Research extends Director {
	private static final String KEY = "cinematic.surrogate.research.";
	/** The pod's own mast, which is the only thing that ever speaks Tanaka's carrier out loud. */
	private static final String MAST = KEY + "mast";
	/** How often the sweep looks at the world, and how much slower it looks at the errands. */
	private static final int SWEEP_TICKS = 20;
	private static final int ERRAND_SWEEPS = 5;
	/** Ticks between two errands being noticed on the radio, so they never talk over each other. */
	private static final int NOTICE_GAP = 200;
	/** How close counts as arriving at somebody's shelter. */
	private static final int ARRIVAL = 32;

	@Nullable
	private static Research running;

	private final ResearchState research;
	private final HabitatState habitat;
	private BlockPos origin;

	// Which way the story went. None of these is saved: they only matter inside a run of the script.
	private boolean coreForced;
	private boolean stakesForced;
	private boolean nightForced;
	private boolean sampleForced;
	private boolean seepForced;
	private boolean relayForced;
	private boolean tanakaForced;

	// What the sweep last saw, because Beat.Until reads its condition every tick and the world is expensive.
	private boolean columnHome;
	private boolean sampleInCrate;
	private boolean relaySeen;
	private boolean tanakaReached;
	private int sweepIn;
	private int errandIn;
	private int noticeIn;
	private final Deque<Notice> notices = new ArrayDeque<>();

	/** Something the player did without being asked, waiting its turn on the radio. */
	private record Notice(Crew who, String lineKey) {
	}

	private Research(MinecraftServer server, ResearchState research, HabitatState habitat) {
		super(server, KEY);
		this.research = research;
		this.habitat = habitat;
		this.origin = habitat.origin == null ? BlockPos.ORIGIN : habitat.origin;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (running != null) running.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> running = null);
		// The seed packet, given to the woman it was always for.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (running == null || world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!(entity instanceof SurvivorEntity survivor) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			if (!running.isProtagonist(serverPlayer)) return ActionResult.PASS;
			return running.onSurvivorUsed(serverPlayer, survivor, player.getStackInHand(hand)) ? ActionResult.SUCCESS : ActionResult.PASS;
		});
		// A chassis dipping a flask in the channel. Only during run three, and only from inside a chassis.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (running == null || world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer) || !running.isProtagonist(serverPlayer)) return ActionResult.PASS;
			if (running.research.stage != ResearchState.STAGE_SEEP) return ActionResult.PASS;
			return running.onSeepUsed(serverPlayer, hit.getBlockPos()) ? ActionResult.SUCCESS : ActionResult.PASS;
		});
	}

	@Nullable
	public static Research running() {
		return running;
	}

	public static boolean isRunning() {
		return running != null;
	}

	/**
	 * Whether the research is filed, which is what lets Contract Seven open. A world with the arc switched
	 * off never blocks the contract behind an arc it will not run.
	 */
	public static boolean done(MinecraftServer server) {
		if (!Surrogate.CONFIG.research) return true;
		return ResearchState.get(server).stage >= ResearchState.STAGE_DONE;
	}

	/** The neighbours open once the opening days are over, and only on a world that has not started the assay. */
	public static boolean shouldBegin(MinecraftServer server, HabitatState habitat) {
		if (!Surrogate.CONFIG.research) return false;
		if (habitat.prologueStage < Prologue.STAGE_DONE || habitat.protagonist == null) return false;
		if (AssayState.get(server).stage != AssayState.STAGE_NONE) return false;
		return ResearchState.get(server).stage == ResearchState.STAGE_NONE;
	}

	public static void begin(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		ResearchState research = ResearchState.get(server);
		if (habitat.origin == null || habitat.protagonist == null) return;
		if (Director.current() != null) return;
		research.stage = ResearchState.STAGE_CORE;
		research.markDirty();
		running = new Research(server, research, habitat);
		running.startDelay = 100;
		running.takeStage();
		Surrogate.LOGGER.info("Research: the neighbours want a hand");
	}

	/** A player joined: if a run is half done for them, pick it up where it stopped. */
	public static boolean onJoin(ServerPlayerEntity player, HabitatState habitat) {
		ResearchState research = ResearchState.get(player.server);
		// A world that reached Contract Seven before these acts existed never runs them. Mark them filed, so
		// everything downstream reads a finished arc rather than one that is waiting for ever.
		if (research.stage == ResearchState.STAGE_NONE && AssayState.get(player.server).stage != AssayState.STAGE_NONE) {
			research.stage = ResearchState.STAGE_DONE;
			research.markDirty();
			Surrogate.LOGGER.info("Research: the contract was already open on this world; the runs are marked filed");
			return false;
		}
		if (habitat.protagonist == null || !habitat.protagonist.equals(player.getUuid())) return false;
		if (research.stage <= ResearchState.STAGE_NONE || research.stage >= ResearchState.STAGE_DONE) return false;
		if (running == null) {
			running = new Research(player.server, research, habitat);
			running.jump(label(research.stage));
			running.startDelay = 60;
			running.takeStage();
			Surrogate.LOGGER.info("Research: resuming at stage {}", research.stage);
		}
		running.send(new CinematicPayloads.State(true, false, false));
		return true;
	}

	/** Dev: start the runs over at one of them, with everything the earlier ones produced already delivered. */
	public static void restartAt(ServerPlayerEntity player, String label) {
		MinecraftServer server = player.server;
		HabitatState habitat = HabitatState.get(server);
		ResearchState research = ResearchState.get(server);
		reset(server);
		habitat.prologueStage = Prologue.STAGE_DONE;
		if (habitat.protagonist == null) habitat.protagonist = player.getUuid();
		habitat.markDirty();
		int stage = stage(label);
		// Whatever was on stage has to come off first, or begin() refuses and the reset above has erased the
		// arc while nothing runs in its place: the world then reads as an unfinished act one until the next
		// join notices. That is what a dev command must never do.
		Director.clearStage();
		begin(server);
		if (running == null || stage == ResearchState.STAGE_CORE) return;
		// Jumping in past a run means the work that run produced has to exist already, or the lines after it
		// are about something that never happened.
		if (stage >= ResearchState.STAGE_WIND) running.finishCore();
		if (stage >= ResearchState.STAGE_SEEP) running.fileWindForJump();
		if (stage >= ResearchState.STAGE_RANGE) {
			running.finishSeep();
			running.handSchematic();
		}
		research.stage = stage;
		research.markDirty();
		running.jump(label(stage));
	}

	/** Forgets the runs. The stakes stay in the ground; only the story resets. */
	public static void reset(MinecraftServer server) {
		ResearchState research = ResearchState.get(server);
		if (running != null) {
			running.resetClient();
			running.leaveStage();
			running = null;
		}
		research.stage = ResearchState.STAGE_NONE;
		research.coreHole = null;
		research.coreDepth = 0;
		research.stakes.clear();
		research.stakeDay = -1L;
		research.seepTaken = false;
		research.seepHome = false;
		research.schematic = false;
		research.relay = false;
		research.damper = false;
		research.markDirty();
	}

	private static String label(int stage) {
		return switch (stage) {
			case ResearchState.STAGE_WIND -> "wind";
			case ResearchState.STAGE_SEEP -> "seep";
			case ResearchState.STAGE_RANGE -> "range";
			case ResearchState.STAGE_DONE -> "done";
			default -> "core";
		};
	}

	private static int stage(String label) {
		return switch (label) {
			case "wind" -> ResearchState.STAGE_WIND;
			case "seep" -> ResearchState.STAGE_SEEP;
			case "range" -> ResearchState.STAGE_RANGE;
			default -> ResearchState.STAGE_CORE;
		};
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		if (!isProtagonist(player)) return;
		Surrogate.LOGGER.info("Research: skipped at {}", currentLabel());
		resetClient();
		switch (research.stage) {
			case ResearchState.STAGE_CORE -> {
				finishCore();
				checkpoint(ResearchState.STAGE_WIND);
				jump("wind");
			}
			case ResearchState.STAGE_WIND -> {
				finishWind();
				checkpoint(ResearchState.STAGE_SEEP);
				jump("seep");
			}
			case ResearchState.STAGE_SEEP -> {
				finishSeep();
				handSchematic();
				checkpoint(ResearchState.STAGE_RANGE);
				jump("range");
			}
			// Skipping the last of it still has to deliver what act three needs: the schematic, the module
			// and the damper. Only the drive is cut.
			default -> {
				handSchematic();
				forceRelay();
				handDamper();
				jump("done");
			}
		}
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		Crew H = Crew.HALLORAN;
		Crew O = Crew.OKAFOR;
		Crew S = Crew.SORENSEN;
		Crew T = Crew.TANAKA;

		// ================================================================ Run one: the shallow core.
		// Okafor opens it and Okafor closes it. Nobody else touches this one.
		label("core");
		run(() -> {
			state(true, false, false);
			checkpoint0(ResearchState.STAGE_CORE);
		});
		chapter(KEY + "chapter", KEY + "chapter.sub", "ONE", 80);
		wait(80);
		radio(H, "open1");
		radio(O, "open2");
		radio(H, "open3");
		radio(O, "core1");
		radio(O, "core2");
		radio(H, "core3");
		objective("core");
		hint("core_nudge");
		until(this::coreDone, timeout() * 8, O, "core_nudge", 2400, () -> coreForced = true);
		branch(() -> coreForced, line(O, "core_timeout", CinematicPayloads.RADIO));
		branch(() -> !coreForced, new Beat.Run(this::objectiveDone0), line(O, "core_done", CinematicPayloads.RADIO));
		run(this::finishCore);
		mark("core_filed");
		radio(O, "core4");
		radio(O, "core5");
		radio(H, "core6");

		// ================================================================ Run two: the wind count.
		// Sorensen's, and he is embarrassed about how much he wants it.
		label("wind");
		run(() -> checkpoint0(ResearchState.STAGE_WIND));
		radio(S, "wind1");
		radio(H, "wind2");
		run(this::stockStakes);
		radio(S, "wind3");
		mark("stakes_open");
		objective("stakes");
		hint("stakes_nudge");
		until(this::stakesPlanted, timeout() * 8, S, "stakes_nudge", 2400, () -> stakesForced = true);
		branch(() -> stakesForced, line(S, "stakes_timeout", CinematicPayloads.RADIO));
		objectiveDone();
		radio(S, "wind4");
		objective("night");
		hint("night_nudge");
		run(this::startTheNight);
		until(this::nightPassed, timeout() * 10, S, "night_nudge", 3000, () -> nightForced = true);
		objectiveDone();
		run(this::finishWind);
		mark("wind_filed");
		radio(S, "wind5");
		radio(H, "wind6");
		radio(S, "wind7");

		// ================================================================ Run three: the seep.
		// Both of them want it, and neither of them can go and get it.
		label("seep");
		run(() -> checkpoint0(ResearchState.STAGE_SEEP));
		radio(O, "seep1");
		radio(S, "seep2");
		radio(O, "seep3");
		radio(S, "seep4");
		radio(H, "seep5");
		mark("seep_open");
		objective("seep");
		hint("seep_nudge");
		until(this::sampleTaken, timeout() * 8, O, "seep_nudge", 2400, () -> sampleForced = true);
		branch(() -> sampleForced, new Beat.Run(this::forceSample), line(H, "seep_timeout", CinematicPayloads.RADIO));
		objectiveDone();
		radio(H, "seep6");
		objective("home");
		hint("home_nudge");
		until(this::sampleHome, timeout() * 6, S, "home_nudge", 2400, () -> seepForced = true);
		branch(() -> seepForced, line(S, "home_timeout", CinematicPayloads.RADIO));
		branch(() -> !seepForced, new Beat.Run(this::objectiveDone0), line(O, "home_done", CinematicPayloads.RADIO));
		run(this::finishSeep);
		mark("seep_filed");
		radio(O, "seep7");
		radio(S, "seep8");
		// The thing that ends act one: a fourth carrier on the band, and a schematic to do something about it.
		radio(S, "schematic1");
		run(this::handSchematic);
		mark("schematic_handed");
		radio(H, "schematic2");
		radio(S, "schematic3");

		// ================================================================ Act two: out of range.
		label("range");
		run(() -> checkpoint0(ResearchState.STAGE_RANGE));
		chapter(KEY + "chapter", KEY + "chapter.sub", "TWO", 80);
		wait(80);
		system(MAST, "carrier1");
		radio(H, "range1");
		radio(S, "range2");
		objective("relay");
		hint("relay_nudge");
		until(this::relayFitted, timeout() * 6, S, "relay_nudge", 1800, this::forceRelay);
		branch(() -> relayForced, line(S, "relay_timeout", CinematicPayloads.RADIO));
		objectiveDone();
		run(() -> playToPlayer(ModSounds.RADIO_OPEN, 0.8f));
		mark("relay_fitted");
		system(MAST, "carrier2");
		radio(T, "tanaka1");
		radio(H, "range3");
		radio(T, "tanaka2");
		mark("tanaka_open");
		objective("tanaka");
		hint("tanaka_nudge");
		until(this::atTanaka, timeout() * 12, H, "tanaka_nudge", 3000, () -> tanakaForced = true);
		branch(() -> tanakaForced, line(H, "tanaka_timeout", CinematicPayloads.RADIO));
		branch(() -> !tanakaForced, new Beat.Run(this::objectiveDone0), line(T, "tanaka3", CinematicPayloads.RADIO));
		objectiveDone();
		radio(T, "damper1");
		radio(T, "damper2");
		run(this::handDamper);
		mark("damper_handed");
		radio(T, "damper3");
		radio(H, "damper4");
		radio(T, "damper5");
		radio(H, "close1");
		radio(O, "close2");
		radio(H, "close3");

		label("done");
		run(() -> {
			checkpoint0(ResearchState.STAGE_DONE);
			resetClient();
			leaveStage();
			running = null;
			Surrogate.LOGGER.info("Research: filed; {} of {} errands done", research.errandsDone(), ResearchState.ERRANDS);
			// The stage is free the moment leaveStage() clears it, and the company has been waiting.
			Assay.begin(server);
		});
	}

	/**
	 * A line in the log where a beat passes. Dialogue goes to one player over a payload and is never logged,
	 * so without these a headless test can only see the state a run leaves behind and not the run itself;
	 * these are what tools/smoke_test_research.py greps for, the way the prologue's labels serve its test.
	 */
	private void mark(String name) {
		run(() -> Surrogate.LOGGER.info("Research: {}", name));
	}

	// ------------------------------------------------------------------ what the script does to the world

	/** Four stakes in the pod's crate, so the objective that says to plant them is one the player can do. */
	private void stockStakes() {
		if (!(world().getBlockEntity(crate()) instanceof ChestBlockEntity chest)) return;
		if (chest.count(ModItems.SURVEY_STAKE) > 0) return;
		for (int slot = 0; slot < chest.size(); slot++) {
			if (chest.getStack(slot).isEmpty()) {
				chest.setStack(slot, new ItemStack(ModItems.SURVEY_STAKE, ResearchState.STAKES));
				chest.markDirty();
				return;
			}
		}
	}

	/** The night the stakes have to stand through. In fast mode the clock is simply moved on. */
	private void startTheNight() {
		if (research.stakeDay < 0) {
			research.stakeDay = day();
			research.markDirty();
		}
		if (fast) world().setTimeOfDay((research.stakeDay + 1) * 24000L + 1000L);
	}

	private void finishCore() {
		research.coreDepth = Math.max(research.coreDepth, ResearchState.CORE_DEPTH);
		research.markDirty();
	}

	/**
	 * The count is filed with whatever went into the ground. It is deliberately not topped up: act six weighs
	 * what the player did for whom, and a run the crew finished has to read as one the crew finished.
	 */
	private void finishWind() {
		if (research.stakeDay < 0) research.stakeDay = day();
		research.markDirty();
	}

	/**
	 * The same, for a dev jump past this run. Here the stakes are made up, because nobody walked them and the
	 * lines after the jump talk about four counts that have to exist for the world to make sense.
	 */
	private void fileWindForJump() {
		finishWind();
		while (research.stakes.size() < ResearchState.STAKES) {
			research.stakes.add(new ResearchState.Stake(origin(), "surrogate:filed_by_jump"));
		}
		research.markDirty();
	}

	private void finishSeep() {
		research.seepTaken = true;
		research.seepHome = true;
		research.markDirty();
	}

	/** Nobody went, so a flask comes back with the pod's own chassis on its next charge run. */
	private void forceSample() {
		research.seepTaken = true;
		research.markDirty();
	}

	private void handSchematic() {
		if (research.schematic) return;
		research.schematic = true;
		research.markDirty();
		// The module itself belongs to the upgrade set; hand the plans over if that set has landed a paper
		// for them, and otherwise let the line stand for it.
		giveIfRegistered("relay_schematic");
		playToPlayer(ModSounds.OBJECTIVE, 0.7f);
		Surrogate.LOGGER.info("Research: Sorensen has handed over the relay schematic");
	}

	/** Nobody built one, so Sorensen talks the pod through wiring the mast's spare head into a chassis. */
	private void forceRelay() {
		relayForced = true;
		fitModule(RobotModule.RELAY);
		research.relay = true;
		research.markDirty();
	}

	private void handDamper() {
		if (research.damper) return;
		research.damper = true;
		research.markDirty();
		if (!fitModule(RobotModule.DAMPER)) giveIfRegistered("resonance_damper");
		ServerPlayerEntity player = player();
		if (player != null) {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.7f, 0.8f);
		}
		Surrogate.LOGGER.info("Research: the resonance damper is handed over");
	}

	/** Fits a module to whatever chassis is at hand. False when there is none, which is a thing that happens. */
	private boolean fitModule(RobotModule module) {
		RobotEntity chassis = chassisAtHand();
		if (chassis == null) return false;
		chassis.setModule(module, true);
		return true;
	}

	/**
	 * Hands over an item the upgrade set may or may not have registered yet. An unknown id resolves to air and
	 * nothing happens, so this arc compiles and runs either way.
	 */
	private void giveIfRegistered(String name) {
		ServerPlayerEntity player = player();
		Item item = Registries.ITEM.get(Surrogate.id(name));
		if (player == null || item == Items.AIR) return;
		ItemStack stack = new ItemStack(item);
		if (!player.giveItemStack(stack)) player.dropItem(stack, false);
	}

	private void checkpoint(int stage) {
		research.stage = stage;
		research.markDirty();
	}

	private void checkpoint0(int stage) {
		checkpoint(stage);
	}

	// ------------------------------------------------------------------ the sweep

	/**
	 * Everything the script is waiting on is answered out of fields, because {@link Beat.Until} reads its
	 * condition every tick. The world is read here instead, on an interval, only for the run that is running,
	 * and only over ground somebody is standing on.
	 */
	@Override
	protected void tick() {
		super.tick();
		if (running != this) return;
		if (noticeIn > 0) noticeIn--;
		if (--sweepIn > 0) return;
		sweepIn = SWEEP_TICKS;
		ServerPlayerEntity player = player();
		if (player == null) return;
		switch (research.stage) {
			case ResearchState.STAGE_CORE -> sweepCore(player);
			case ResearchState.STAGE_SEEP -> sampleInCrate = crateCount(ModItems.SEALED_SAMPLE) > 0;
			case ResearchState.STAGE_RANGE -> sweepRange(player);
			default -> {
			}
		}
		if (--errandIn <= 0) {
			errandIn = ERRAND_SWEEPS;
			sweepErrands();
		}
		drainNotices();
	}

	/**
	 * How far below the ground the player has got, and whether they stayed in one place doing it. The
	 * heightmap at their own column is the floor of the hole they are standing in, so the ground is read a
	 * couple of blocks to each side instead.
	 */
	private void sweepCore(ServerPlayerEntity player) {
		columnHome = crateColumn() >= ResearchState.CORE_COLUMN;
		if (research.coreDepth >= ResearchState.CORE_DEPTH) return;
		RobotEntity driven = piloted(player);
		Entity body = driven != null ? driven : player;
		if (body.getWorld() != world()) return;
		ServerWorld world = world();
		BlockPos at = body.getBlockPos();
		if (!world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) return;
		int ground = Math.max(
				Math.max(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX() + 2, at.getZ()),
						world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX() - 2, at.getZ())),
				Math.max(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ() + 2),
						world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ() - 2)));
		int depth = ground - at.getY();
		if (depth < 3) return;
		// One shaft, not four scrapes: wander more than a few blocks and the count starts again.
		if (research.coreHole == null || horizontalSq(research.coreHole, at) > 36.0) {
			research.coreHole = at;
			research.coreDepth = depth;
		} else {
			research.coreDepth = Math.max(research.coreDepth, depth);
		}
		research.markDirty();
	}

	private void sweepRange(ServerPlayerEntity player) {
		if (!research.relay) {
			RobotEntity chassis = chassisAtHand();
			if (chassis != null && chassis.hasModule(RobotModule.RELAY)) {
				research.relay = true;
				research.markDirty();
			}
		}
		relaySeen = research.relay;
		if (tanakaReached) return;
		SurvivorManager.Site site = siteOf(Survivor.TANAKA);
		if (site == null || !site.built) return;
		RobotEntity driven = piloted(player);
		Entity body = driven != null ? driven : player;
		if (body.getWorld() == world() && horizontalSq(site.origin(), body.getBlockPos()) < (double) ARRIVAL * ARRIVAL) {
			tanakaReached = true;
			return;
		}
		// A player driving the crawler is standing in the cabin, not in the valley: the hull is what arrived.
		ServerWorld world = world();
		if (!world.isChunkLoaded(site.x >> 4, site.z >> 4)) return;
		Box around = new Box(site.origin()).expand(ARRIVAL, 16.0, ARRIVAL);
		tanakaReached = !world.getEntitiesByClass(CrawlerEntity.class, around, hull -> !hull.isRemoved()).isEmpty();
	}

	/** The errands, which were never objectives. Read rarely, and only the ones still outstanding. */
	private void sweepErrands() {
		if (research.errandsDone() >= ResearchState.ERRANDS) return;
		SurvivorManager survivors = SurvivorManager.get(server);
		if (!research.airlock) {
			SurvivorManager.Site site = siteOf(Survivor.SORENSEN);
			ServerWorld world = world();
			if (site != null && site.built && world.isChunkLoaded(site.x >> 4, site.z >> 4)
					&& world.getBlockState(site.origin().add(SurvivorShelter.BREACH)).isOf(ModBlocks.HULL_PLATING)) {
				research.airlock = true;
				research.markDirty();
				notice(Crew.SORENSEN, "errand_airlock");
			}
		}
		if (!research.powerCells && needMet(survivors, Survivor.OKAFOR)) {
			research.powerCells = true;
			research.markDirty();
			notice(Crew.OKAFOR, "errand_cells");
		}
		if (!research.repairKits && needMet(survivors, Survivor.SORENSEN)) {
			research.repairKits = true;
			research.markDirty();
			notice(Crew.SORENSEN, "errand_kits");
		}
		if (!research.sulfur && needMet(survivors, Survivor.TANAKA)) {
			research.sulfur = true;
			research.markDirty();
			notice(Crew.TANAKA, "errand_sulfur");
		}
	}

	/** Whether the survivor's standing need has been met, read off the person rather than the shelter. */
	private boolean needMet(SurvivorManager survivors, Survivor who) {
		ServerWorld world = world();
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.character != who.ordinal() || !site.built) continue;
			if (!world.isChunkLoaded(site.x >> 4, site.z >> 4)) continue;
			List<SurvivorEntity> found = world.getEntitiesByClass(SurvivorEntity.class, new Box(site.origin()).expand(8.0),
					person -> person.getCharacter() == who && person.isRescued());
			if (!found.isEmpty()) return true;
		}
		return false;
	}

	/** Queues a line about something nobody asked for. It waits its turn rather than talking over the script. */
	private void notice(Crew who, String lineKey) {
		notices.add(new Notice(who, lineKey));
	}

	private void drainNotices() {
		if (notices.isEmpty() || noticeIn > 0 || player() == null) return;
		Notice next = notices.poll();
		noticeIn = NOTICE_GAP;
		sendLine(next.who().nameKey(), key + next.lineKey(), CinematicPayloads.RADIO, -1, true, "");
	}

	// ------------------------------------------------------------------ what the world answers back

	/** A stake went into the ground: the count takes it, or says why it will not. */
	public static void stakePlanted(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		ResearchState research = ResearchState.get(world.getServer());
		if (research.stage != ResearchState.STAGE_WIND || research.stakes.size() >= ResearchState.STAKES) return;
		if (world.getRegistryKey() != world.getServer().getOverworld().getRegistryKey()) return;
		String biome = world.getBiome(pos).getKey().map(key -> key.getValue().toString()).orElse("unknown");
		String refusal = research.stakeRefusal(pos, biome);
		if (refusal != null) {
			player.sendMessage(Text.translatable(refusal).formatted(Formatting.GRAY), true);
			return;
		}
		research.stakes.add(new ResearchState.Stake(pos, biome));
		if (research.stakes.size() >= ResearchState.STAKES) {
			research.stakeDay = world.getTimeOfDay() / 24000L;
		}
		research.markDirty();
		world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.7f, 1.6f);
		player.sendMessage(Text.translatable("message.surrogate.stake.planted", research.stakes.size(), ResearchState.STAKES)
				.formatted(Formatting.AQUA), true);
		Surrogate.LOGGER.info("Research: stake {} of {} at {}", research.stakes.size(), ResearchState.STAKES, pos.toShortString());
	}

	/** A sealed sample was opened where the air is. Somebody has to go back. */
	public static void sampleSpoiled(ServerPlayerEntity player) {
		ResearchState research = ResearchState.get(player.server);
		if (!research.seepTaken || research.seepHome) return;
		research.seepTaken = false;
		research.markDirty();
		if (running != null) running.notice(Crew.OKAFOR, "seep_spoiled");
		Surrogate.LOGGER.info("Research: the seep sample was opened outdoors");
	}

	/** A chassis dips a flask in the channel. A body cannot do it and a body should not be out there. */
	private boolean onSeepUsed(ServerPlayerEntity player, BlockPos pos) {
		if (research.seepTaken || player.getStackInHand(Hand.MAIN_HAND).isOf(ModItems.SEALED_SAMPLE)) return false;
		ServerWorld world = player.getServerWorld();
		if (world.getFluidState(pos).isEmpty()) return false;
		if (Valleys.isMesaWorld(world)) {
			Valleys.Kind kind = Valleys.classify(world, pos.getX(), pos.getZ());
			if (kind != Valleys.Kind.RIVER && kind != Valleys.Kind.SEA) {
				player.sendMessage(Text.translatable("message.surrogate.sample.wrong_water").formatted(Formatting.GRAY), true);
				return false;
			}
		}
		if (piloted(player) == null) {
			player.sendMessage(Text.translatable("message.surrogate.sample.needs_chassis").formatted(Formatting.GRAY), true);
			return false;
		}
		ItemStack sample = new ItemStack(ModItems.SEALED_SAMPLE);
		if (!player.giveItemStack(sample)) player.dropItem(sample, false);
		research.seepTaken = true;
		research.markDirty();
		world.spawnParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.02);
		world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.9f, 0.7f);
		Surrogate.LOGGER.info("Research: the seep sample is taken");
		return true;
	}

	/** The seed packet, handed to a botanist with nothing green. */
	private boolean onSurvivorUsed(ServerPlayerEntity player, SurvivorEntity survivor, ItemStack stack) {
		if (research.seeds || !stack.isOf(ModItems.TOMATO_SEEDS)) return false;
		if (survivor.getCharacter() != Survivor.OKAFOR) return false;
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		research.seeds = true;
		research.markDirty();
		survivor.getWorld().playSound(null, survivor.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.1f);
		notice(Crew.OKAFOR, "errand_seeds");
		Surrogate.LOGGER.info("Research: the seeds went to Okafor");
		return true;
	}

	// ------------------------------------------------------------------ conditions

	private boolean isProtagonist(ServerPlayerEntity player) {
		return habitat.protagonist != null && habitat.protagonist.equals(player.getUuid());
	}

	private boolean coreDone() {
		return coreForced || (research.coreDepth >= ResearchState.CORE_DEPTH && columnHome);
	}

	private boolean stakesPlanted() {
		return stakesForced || research.stakes.size() >= ResearchState.STAKES;
	}

	private boolean nightPassed() {
		return nightForced || (research.stakeDay >= 0 && day() > research.stakeDay);
	}

	private boolean sampleTaken() {
		return sampleForced || research.seepTaken;
	}

	private boolean sampleHome() {
		if (seepForced || research.seepHome) return true;
		if (!sampleInCrate) return false;
		research.seepHome = true;
		research.markDirty();
		return true;
	}

	private boolean relayFitted() {
		return relayForced || relaySeen || research.relay;
	}

	private boolean atTanaka() {
		return tanakaForced || tanakaReached;
	}

	// ------------------------------------------------------------------ small readings of the world

	/** The pod's own crate by the outer door: where every run in this arc is filed. */
	private BlockPos crate() {
		return origin.add(HabitatBuilder.ASSAY_CRATE);
	}

	private int crateCount(Item item) {
		return world().getBlockEntity(crate()) instanceof ChestBlockEntity chest ? chest.count(item) : 0;
	}

	/** Blocks of the valley floor in the crate, whatever the shaft happened to be cut through. */
	private int crateColumn() {
		if (!(world().getBlockEntity(crate()) instanceof ChestBlockEntity chest)) return 0;
		return chest.count(ModItems.CAUSTIC_SAND) + chest.count(ModItems.CAUSTIC_SANDSTONE) + chest.count(ModItems.ASH)
				+ chest.count(Items.STONE) + chest.count(Items.COBBLESTONE) + chest.count(Items.TUFF)
				+ chest.count(Items.DEEPSLATE) + chest.count(Items.COBBLED_DEEPSLATE) + chest.count(Items.GRAVEL);
	}

	@Nullable
	private RobotEntity piloted(ServerPlayerEntity player) {
		return player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) ? robot : null;
	}

	/** The chassis being driven, or the nearest one standing beside the player. */
	@Nullable
	private RobotEntity chassisAtHand() {
		ServerPlayerEntity player = player();
		if (player == null) return null;
		RobotEntity driven = piloted(player);
		if (driven != null) return driven;
		Box around = new Box(player.getBlockPos()).expand(24.0, 8.0, 24.0);
		return player.getServerWorld().getEntitiesByClass(RobotEntity.class, around, robot -> !robot.isRemoved() && !robot.isScripted())
				.stream().findFirst().orElse(null);
	}

	@Nullable
	private SurvivorManager.Site siteOf(Survivor who) {
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			if (site.character == who.ordinal()) return site;
		}
		return null;
	}

	private long day() {
		return world().getTimeOfDay() / 24000L;
	}

	private static double horizontalSq(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// ------------------------------------------------------------------ what a script needs from its world

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		return habitat.protagonist == null ? null : server.getPlayerManager().getPlayer(habitat.protagonist);
	}

	@Override
	public ServerWorld world() {
		return server.getOverworld();
	}

	@Override
	public BlockPos origin() {
		if (habitat.origin != null) origin = habitat.origin;
		return origin;
	}

	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		var id = who == Crew.HALLORAN ? habitat.halloran : who == Crew.MARSH ? habitat.marsh : null;
		return id != null && world().getEntity(id) instanceof CrewEntity found ? found : null;
	}
}
