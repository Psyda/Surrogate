package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.AirlockDoorBlock;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.item.RobotChassisItem;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The first three days on the ground. The player wakes in Habitat Seven alone, with a cat and a radio; the
 * two people on Sallow are six hundred metres away at Site Two and, for now, only voices. Day one, Halloran
 * talks them through the pod, the chassis and the chair. Day two, her chassis walks over with a crate and Marsh
 * reads out what the company wants. Day three, Marsh walks over himself to fix the dock, and leaves his
 * clipboard on the table. It runs once per world, survives restarts, can be skipped, and every objective the
 * player ignores is eventually worked around.
 *
 * <p>The script is a flat list of {@link Beat}s built in {@link #buildScript()}; it reads top to bottom.
 */
public final class Prologue extends Director {
	public static final int STAGE_NONE = 0;
	public static final int STAGE_DAY1 = 1;
	public static final int STAGE_DAY2 = 2;
	public static final int STAGE_DAY3 = 3;
	public static final int STAGE_DONE = 4;

	@Nullable
	private static Prologue running;

	// Where things are, relative to the habitat origin. Floor level is y = 0, so standing height is y = 1.
	private static final Vec3d PLAYER_START = new Vec3d(2.5, 1, 3.5);
	private static final BlockPos POD_CHEST = new BlockPos(3, 1, -3);
	private static final BlockPos POD_CHAIR = new BlockPos(-3, 1, 2);
	private static final BlockPos POD_DOCK = new BlockPos(-1, 1, -3);
	private static final BlockPos INNER_DOOR = new BlockPos(0, 1, 4);
	private static final BlockPos OUTER_DOOR = new BlockPos(0, 1, 7);
	private static final Vec3d POD_CHASSIS_SPOT = new Vec3d(0.5, 1, 0.5);
	private static final Vec3d INSIDE_DOOR = new Vec3d(0.5, 1, 2.5);
	private static final Vec3d CHAMBER = new Vec3d(0.5, 1, 5.5);
	private static final Vec3d OUTER_APPROACH = new Vec3d(0.5, 1, 8.5);
	private static final Vec3d DOCK_FRONT = new Vec3d(-0.5, 1, -2.5);
	private static final Vec3d ROOM_MIDDLE = new Vec3d(0.5, 1, 1.5);
	private static final Vec3d TABLE_SPOT = new Vec3d(2.5, 1, -1.5);
	private static final Vec3d TABLE_TOP = new Vec3d(3.5, 2.05, -1.5);
	private static final Vec3d ROBOT_FAR = new Vec3d(1.5, 0, 38.5);
	private static final Vec3d MARSH_FAR = new Vec3d(-5.5, 0, 34.5);
	private static final BlockPos CRATE = new BlockPos(-2, 1, 10);
	private static final double GONE = 30.0;
	private static final String KEY = "cinematic.surrogate.prologue.";
	private static final String POD = KEY + "pod";

	private final HabitatState state;
	private final BlockPos origin;

	// Which way the story went.
	private boolean radioAnswered;
	private boolean deployedByPod;
	private boolean boundByPod;
	private boolean skippedDive;
	private boolean skippedOutside;
	private boolean skippedSample;
	private boolean doorByMarsh;
	private boolean awaitingWrench;
	private boolean wrenchGiven;
	private boolean marshOwnWrench;
	private long dayTarget;

	private Prologue(MinecraftServer server, HabitatState state) {
		super(server, KEY);
		this.state = state;
		this.origin = state.origin == null ? BlockPos.ORIGIN : state.origin;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (running != null) running.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> running = null);
	}

	@Nullable
	public static Prologue running() {
		return running;
	}

	public static boolean isRunning() {
		return running != null;
	}

	public static boolean shouldBegin(net.minecraft.server.MinecraftServer server, HabitatState state) {
		return Surrogate.CONFIG.prologue && dev.psyda.surrogate.registry.ModGameRules.intro(server) && state.prologueStage == STAGE_NONE && state.origin != null;
	}

	/** Puts {@code player} in the pod and starts telling the story to them. */
	public static void begin(ServerPlayerEntity player, HabitatState state) {
		if (state.origin == null) return;
		ServerWorld world = player.server.getOverworld();
		AnnexBuilder.clearCrew(world, state.origin, state);
		Vec3d start = Vec3d.of(state.origin).add(PLAYER_START);
		player.teleport(world, start.x, start.y, start.z, 90f, 20f);
		player.setSpawnPoint(world.getRegistryKey(), state.origin.up(), 180f, true, false);
		state.protagonist = player.getUuid();
		state.prologueStage = STAGE_DAY1;
		state.landedDay = world.getTimeOfDay() / 24000L;
		state.marshVisit = 0;
		state.markDirty();
		running = new Prologue(player.server, state);
		running.startDelay = 30;
		running.takeStage();
		Surrogate.LOGGER.info("Prologue: begins for {}", player.getName().getString());
	}

	/** A player joined: if the story is half told to them, pick it up again. @return true when it was. */
	public static boolean onJoin(ServerPlayerEntity player, HabitatState state) {
		if (state.protagonist == null || !state.protagonist.equals(player.getUuid())) return false;
		if (state.prologueStage <= STAGE_NONE || state.prologueStage >= STAGE_DONE) return false;
		if (running == null) {
			running = new Prologue(player.server, state);
			String label = switch (state.prologueStage) {
				case STAGE_DAY2 -> "day2";
				case STAGE_DAY3 -> state.marshVisit == 2 ? "finale" : "day3";
				default -> "day1";
			};
			running.jump(label);
			running.startDelay = 40;
			running.takeStage();
			Surrogate.LOGGER.info("Prologue: resuming at {}", label);
		}
		running.send(new CinematicPayloads.State(true, false, false));
		return true;
	}

	public static void skip(ServerPlayerEntity player) {
		if (running != null && running.isProtagonist(player)) running.skipDay();
	}

	/** Forgets the opening: the next landing tells it again. Used when the week aboard is replayed. */
	public static void reset(MinecraftServer server) {
		HabitatState state = HabitatState.get(server);
		if (running != null) {
			running.resetClient();
			running.leaveStage();
			running = null;
		}
		if (state.origin != null) AnnexBuilder.clearCrew(server.getOverworld(), state.origin, state);
		state.prologueStage = STAGE_NONE;
		state.protagonist = null;
		state.marshVisit = 0;
		state.landedDay = -1L;
		state.markDirty();
	}

	/** Dev: start over for this player. */
	public static void restart(ServerPlayerEntity player) {
		restartAt(player, "day1");
	}

	/** Dev: start over at a day: {@code day1}, {@code day2} or {@code day3}. */
	public static void restartAt(ServerPlayerEntity player, String label) {
		HabitatState state = HabitatState.get(player.server);
		if (state.origin == null) return;
		reset(player.server);
		PilotManager.data(player).welcomed = true;
		if (PilotManager.isPiloting(player)) PilotManager.disconnect(player, true, null, false);
		begin(player, state);
		if (running == null) return;
		int day = label.equals("day3") ? 3 : label.equals("day2") ? 2 : 1;
		if (day > 1) {
			state.prologueStage = day == 3 ? STAGE_DAY3 : STAGE_DAY2;
			state.landedDay -= day - 1;
			state.markDirty();
			running.ensureCrate();
			running.jump("day" + day);
		}
	}

	public boolean isProtagonist(ServerPlayerEntity player) {
		return state.protagonist != null && state.protagonist.equals(player.getUuid());
	}

	public int stage() {
		return state.prologueStage;
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		skip(player);
	}

	@Override
	protected void onRadioLine(Crew who) {
		if (who == Crew.HALLORAN) robotLookAtPlayer();
	}

	@Override
	protected void onRadioUsed(ServerPlayerEntity player) {
		if (!isProtagonist(player) || radioAnswered) return;
		radioAnswered = true;
		Surrogate.LOGGER.info("Prologue: radio answered");
	}

	/** Marsh takes the wrench when he has asked for one. */
	@Override
	public boolean onCrewUsed(Crew who, ServerPlayerEntity player, ItemStack stack) {
		if (who != Crew.MARSH || !awaitingWrench || wrenchGiven || !stack.isOf(ModItems.WRENCH)) return false;
		CrewEntity marsh = crew(Crew.MARSH);
		if (marsh == null) return false;
		ItemStack taken = stack.split(1);
		marsh.equipStack(EquipmentSlot.MAINHAND, taken);
		marsh.swingHand(Hand.MAIN_HAND);
		wrenchGiven = true;
		world().playSound(null, marsh.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8f, 1.0f);
		return true;
	}

	/** The skip key: the rest of today is worked around, and tomorrow starts. */
	private void skipDay() {
		String label = currentLabel();
		Surrogate.LOGGER.info("Prologue: skipped at {}", label);
		resetClient();
		int stage = state.prologueStage;
		if (stage <= STAGE_DAY1) {
			ensureChassisAndChair();
			checkpoint0(STAGE_DAY2);
			jump("day2");
		} else if (stage == STAGE_DAY2) {
			ensureCrate();
			retireRobot();
			checkpoint0(STAGE_DAY3);
			jump("day3");
		} else if (state.marshVisit < 2 && index < indexOf("finale")) {
			fixDock();
			marshGone();
			jump("finale");
		} else {
			jump("done");
		}
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		Crew H = Crew.HALLORAN;
		Crew M = Crew.MARSH;

		// ================================================================ Day one: down, and a voice.
		// Black screen, a title, and the pod fading in around one person and one cat.
		label("wake");
		run(() -> {
			state(true, true, true);
			sendFade(255, 0);
			setMorning();
			cullExtraCats();
		});
		shot(cam(2.5, 2.62, 3.05, 2.5, 1.0, 1.2, 120, CinematicPayloads.EASE_SMOOTH),
				cam(2.5, 2.62, 3.05, -0.5, 2.3, 0.5, 0, 0));
		wait(10);
		run(() -> playToPlayer(ModSounds.TITLE, 1.0f));
		title("cinematic.surrogate.title", "cinematic.surrogate.title.sub", 110);
		wait(120);
		system(POD, "pod1");
		fade(0, 80);
		wait(40);
		shot(cam(0.3, 2.55, 0.3, 2.5, 1.2, 2.0, 0, 0));
		system(POD, "pod2");
		wait(30);
		run(() -> playToPlayer(ModSounds.STATIC, 0.6f, 0.8f));
		wait(20);
		radio(H, "call1");

		// ---- Answer the radio. Nothing else happens until they have found it, or until she gives up waiting.
		label("day1");
		checkpoint(STAGE_DAY1);
		run(() -> {
			release();
			state(true, false, false);
			sendFade(0, 20);
		});
		objective("answer");
		hint("answer_nudge");
		until(() -> radioAnswered, timeout() * 2, H, "answer_nudge", 600, null);
		branch(() -> !radioAnswered, line(H, "answer_timeout", CinematicPayloads.RADIO));
		objectiveDone();
		wait(10);
		radio(H, "call2");
		radio(M, "call3");
		radio(H, "call4");
		radio(H, "call5");
		radio(H, "call6");
		radio(M, "call7");

		// ---- Your kit. The pod has a cargo arm for anyone who will not use their hands.
		label("kit");
		radio(H, "kit1");
		objective("deploy");
		hint("kit_nudge");
		until(this::chassisDeployed, timeout(), M, "kit_nudge", 500, () -> deployedByPod = true);
		branch(() -> deployedByPod, line(H, "kit_timeout1", CinematicPayloads.RADIO), line(M, "kit_timeout2", CinematicPayloads.RADIO),
				new Beat.Run(this::podDeploys), new Beat.Wait(20));
		objectiveDone();
		wait(20);

		// ---- Key the card, bind the chair.
		label("bind");
		radio(H, "key1");
		objective("bind");
		hint("key_nudge");
		until(this::chairBound, timeout(), M, "key_nudge", 400, () -> boundByPod = true);
		branch(() -> boundByPod, line(M, "key_timeout", CinematicPayloads.RADIO), new Beat.Run(this::podBinds), new Beat.Wait(20));
		objectiveDone();
		wait(20);

		// ---- First dive.
		label("dive");
		radio(H, "dive1");
		radio(M, "dive2");
		objective("dive");
		hint("dive_nudge");
		until(this::playerOnline, timeout() * 3 / 2, H, "dive_nudge", 600, () -> skippedDive = true);
		branch(() -> skippedDive, new Beat.Run(this::objectiveClear), line(H, "dive_timeout", CinematicPayloads.RADIO));
		branch(() -> !skippedDive, new Beat.Run(this::objectiveDone0), new Beat.Wait(30), line(M, "online1", CinematicPayloads.RADIO), line(H, "online2", CinematicPayloads.RADIO));

		// ---- Outside, alone, with a voice in the lens.
		label("outside");
		jumpIf(() -> skippedDive, "night1");
		objective("outside");
		hint("out_nudge");
		until(this::playerOutside, timeout() * 3, H, "out_nudge", 900, () -> skippedOutside = true);
		jumpIf(() -> skippedOutside, "night1");
		objectiveDone();
		wait(10);
		radio(H, "out1");
		radio(H, "out2");
		radio(H, "out3");
		run(this::objectiveClear);
		wait(30);

		// ---- Night one.
		label("night1");
		run(() -> {
			hintKey = null;
			objectiveClear();
		});
		radio(H, "night1");
		radio(M, "night2");
		untilMorning(2, "rest_nudge");

		// ================================================================ Day two: something on the pad.
		label("day2");
		checkpoint(STAGE_DAY2);
		run(() -> {
			state(true, true, true);
			sendFade(255, 0);
			setMorning();
			ensureChassisAndChair();
			robotFar();
		});
		wait(10);
		chapter(KEY + "chapter", KEY + "chapter.sub", "TWO", 80);
		wait(70);
		shot(cam(-1.0, 2.6, 3.4, 0.5, 1.4, 9.0, 0, 0));
		fade(0, 60);
		wait(40);
		radio(H, "morning1");
		run(() -> {
			release();
			state(true, false, false);
		});
		robotWalk(HabitatBuilder.ROBOT_PAD);
		objective("look");
		hint("look_nudge");
		robotArrive(HabitatBuilder.ROBOT_PAD, 900);
		run(() -> snapRobot(HabitatBuilder.ROBOT_PAD, 0f));
		until(this::playerSeesPad, timeout() * 2, H, "look_nudge", 700, null);
		objectiveDone();
		radio(H, "morning2");
		radio(M, "morning3");
		run(this::unloadCrate);
		radio(H, "morning4");

		// ---- What the company wants.
		label("assay");
		radio(M, "assay1");
		radio(H, "assay2");
		radio(M, "assay3");
		radio(H, "assay4");
		objective("sample");
		hint("sample_nudge");
		until(this::sampleDelivered, timeout() * 4, M, "sample_nudge", 1200, () -> skippedSample = true);
		branch(() -> skippedSample, new Beat.Run(this::objectiveClear), line(H, "sample_timeout", CinematicPayloads.RADIO));
		branch(() -> !skippedSample, new Beat.Run(this::objectiveDone0), new Beat.Run(this::logSample), line(M, "sample_done", CinematicPayloads.RADIO), line(H, "sample_done2", CinematicPayloads.RADIO));

		// ---- The walk to the heap, and the room she wants.
		label("walk");
		jumpIf(() -> !playerLinked(), "colony");
		radio(H, "follow1");
		objective("follow");
		robotWalk(HabitatBuilder.SCRAP_HEAP.add(-1.5, 0, -1.0));
		robotArrive(HabitatBuilder.SCRAP_HEAP.add(-1.5, 0, -1.0), 600);
		radio(H, "out4");
		run(this::objectiveClear);
		label("colony");
		radio(H, "colony1");
		radio(M, "colony2");
		radio(H, "colony3");
		radio(M, "colony4");
		radio(H, "colony5");
		robotWalk(ROBOT_FAR);
		untilRobotGone(900);
		run(this::retireRobot);
		untilMorning(3, "rest_nudge");

		// ================================================================ Day three: a person at the door.
		label("day3");
		checkpoint(STAGE_DAY3);
		run(() -> {
			state(true, true, true);
			sendFade(255, 0);
			setMorning();
			ensureChassisAndChair();
			ensureCrate();
		});
		wait(10);
		chapter(KEY + "chapter", KEY + "chapter.sub", "THREE", 80);
		wait(70);
		shot(cam(2.5, 2.62, 3.05, -0.5, 2.3, 0.5, 0, 0));
		fade(0, 60);
		wait(40);
		radio(M, "walk1");
		radio(H, "walk2");
		run(() -> {
			release();
			state(true, false, false);
			marshArrives();
		});
		objective("marsh");
		hint("marsh_nudge");
		walk(M, OUTER_APPROACH, null);
		arrive(M, OUTER_APPROACH, 1200);
		run(() -> faceDoor(Crew.MARSH));
		radio(M, "walk3");
		objectiveNow0("door");
		hint("door_nudge");
		until(this::outerOpen, timeout(), M, "door_nudge", 500, () -> doorByMarsh = true);
		branch(() -> doorByMarsh, line(M, "door_timeout", CinematicPayloads.RADIO), new Beat.Run(() -> setDoor(OUTER_DOOR, true)));
		objectiveDone();
		walk(M, CHAMBER, OUTER_DOOR);
		arrive(M, CHAMBER, 300);
		run(() -> setDoor(OUTER_DOOR, false));
		radio(M, "walk4");
		wait(50);
		run(() -> {
			state(true, true, true);
			send(new CinematicPayloads.Camera(List.of(cam(2.4, 2.5, 1.2, 0.5, 1.5, 4.5, 0, 0))));
		});
		walk(M, INSIDE_DOOR, INNER_DOOR);
		arrive(M, INSIDE_DOOR, 300);
		run(() -> {
			setDoor(INNER_DOOR, false);
			CrewEntity marsh = crew(Crew.MARSH);
			if (marsh != null) marsh.setLookTarget(player());
		});
		say(M, "meet1");
		say(M, "meet2");
		run(() -> {
			CrewEntity marsh = crew(Crew.MARSH);
			CatEntity cat = cat();
			if (marsh != null && cat != null) marsh.setLookTarget(cat);
		});
		say(M, "meet3");
		say(M, "meet4");
		run(() -> {
			release();
			state(true, false, false);
			CrewEntity marsh = crew(Crew.MARSH);
			if (marsh != null) marsh.setLookTarget(player());
		});

		// ---- The dock, and a wrench.
		label("dock");
		say(M, "dock1");
		run(() -> awaitingWrench = true);
		objective("wrench");
		hint("wrench_nudge");
		until(() -> wrenchGiven, timeout(), M, "wrench_nudge", 400, () -> marshOwnWrench = true);
		run(() -> awaitingWrench = false);
		branch(() -> marshOwnWrench, line(M, "wrench_timeout", CinematicPayloads.SPEECH), new Beat.Run(this::marshOwnWrench));
		objectiveDone();
		walk(M, DOCK_FRONT, null);
		arrive(M, DOCK_FRONT, 300);
		run(() -> {
			CrewEntity marsh = crew(Crew.MARSH);
			if (marsh != null) {
				marsh.setLookTarget(null);
				marsh.face(0f);
			}
		});
		wait(10);
		run(this::marshWorks);
		wait(40);
		run(this::marshWorks);
		wait(40);
		run(this::marshWorks);
		wait(30);
		run(this::fixDock);
		say(M, "dock2");
		run(this::returnWrench);

		// ---- Seeds, the slab, and the way home.
		label("visit");
		walk(M, TABLE_SPOT, null);
		arrive(M, TABLE_SPOT, 200);
		run(() -> {
			CrewEntity marsh = crew(Crew.MARSH);
			if (marsh != null) marsh.setLookTarget(player());
		});
		say(M, "seeds1");
		branch(this::playerHasSeeds, line(M, "seeds2", CinematicPayloads.SPEECH));
		branch(() -> !playerHasSeeds(), line(M, "seeds3", CinematicPayloads.SPEECH));
		say(M, "colony6");
		say(M, "colony7");
		say(M, "leave1");
		run(() -> {
			CrewEntity marsh = crew(Crew.MARSH);
			if (marsh != null) marsh.setLookTarget(null);
		});
		walk(M, CHAMBER, INNER_DOOR);
		arrive(M, CHAMBER, 300);
		run(() -> setDoor(INNER_DOOR, false));
		wait(70);
		walk(M, OUTER_APPROACH, OUTER_DOOR);
		arrive(M, OUTER_APPROACH, 300);
		run(() -> setDoor(OUTER_DOOR, false));
		walk(M, MARSH_FAR, null);
		untilMarshGone(900);
		run(this::marshGone);
		wait(30);
		radio(M, "leave2");
		radio(H, "end1");
		radio(H, "end2");
		wait(20);

		// ---- Finale. The pod from the pad at dusk, the first voice that is not theirs, and the title again.
		label("finale");
		run(() -> {
			state(true, true, true);
			setDusk();
			shotFromEyes(at(0.5, 1.6, 8.0), at(-3.5, 3.2, 14.5), 150);
		});
		wait(110);
		run(this::radioCall);
		beats.add(new Beat.WaitLastLine());
		wait(25);
		narration(KEY + "end");
		title("cinematic.surrogate.title", "cinematic.surrogate.title.day3", 90);
		wait(80);
		fade(255, 30);
		wait(35);
		label("done");
		run(() -> {
			resetClient();
			sendFade(0, 40);
			checkpoint0(STAGE_DONE);
			finish();
		});
	}

	private void objectiveNow0(String objectiveKey) {
		beats.add(new Beat.Run(() -> objectiveNow(objectiveKey)));
	}

	/** Waits for the surface day {@code day} (counted from the landing) to dawn; fast mode just sets the clock. */
	private void untilMorning(int day, String nudgeKey) {
		objective("rest");
		hint("rest_nudge");
		run(() -> {
			dayTarget = state.landedDay + day - 1;
			if (fast) world().setTimeOfDay(dayTarget * 24000L + 1000L);
		});
		until(() -> dayIndex() >= dayTarget, 0, Crew.HALLORAN, nudgeKey, 3000, null);
		objectiveDone();
	}

	private void untilRobotGone(int timeout) {
		until(() -> {
			RobotEntity robot = halloranRobot();
			return robot == null || robot.squaredDistanceTo(at(HabitatBuilder.ROBOT_PAD)) > GONE * GONE;
		}, timeout, null, null, 0, null);
	}

	private void untilMarshGone(int timeout) {
		until(() -> {
			CrewEntity marsh = crew(Crew.MARSH);
			return marsh == null || marsh.squaredDistanceTo(at(OUTER_APPROACH)) > GONE * GONE;
		}, timeout, null, null, 0, null);
	}

	private void finish() {
		ServerPlayerEntity player = player();
		if (player != null) {
			player.sendMessage(Text.translatable("message.surrogate.welcome.hint").formatted(Formatting.GRAY), false);
		}
		leaveStage();
		running = null;
		Surrogate.LOGGER.info("Prologue: complete");
	}

	private void checkpoint(int stage) {
		beats.add(new Beat.Run(() -> checkpoint0(stage)));
	}

	private void checkpoint0(int stage) {
		if (state.prologueStage == stage) return;
		state.prologueStage = stage;
		state.markDirty();
		Surrogate.LOGGER.info("Prologue: stage {}", stage);
	}

	// ------------------------------------------------------------------ world and actors

	@Override
	public ServerWorld world() {
		return server.getOverworld();
	}

	@Override
	public BlockPos origin() {
		return origin;
	}

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		return state.protagonist == null ? null : server.getPlayerManager().getPlayer(state.protagonist);
	}

	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		var id = who == Crew.HALLORAN ? state.halloran : state.marsh;
		return id != null && world().getEntity(id) instanceof CrewEntity crew ? crew : null;
	}

	@Nullable
	RobotEntity halloranRobot() {
		return state.halloranRobot != null && world().getEntity(state.halloranRobot) instanceof RobotEntity robot ? robot : null;
	}

	@Override
	@Nullable
	public RobotEntity scriptedRobot() {
		return halloranRobot();
	}

	@Nullable
	private CatEntity cat() {
		return state.cat != null && world().getEntity(state.cat) instanceof CatEntity cat ? cat : null;
	}

	@Nullable
	private RobotEntity playerRobot() {
		ServerPlayerEntity player = player();
		return player != null && player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) ? robot : null;
	}

	private Box podBox() {
		return new Box(origin.getX() - 3, origin.getY() + 1, origin.getZ() - 3, origin.getX() + 4, origin.getY() + 5, origin.getZ() + 4);
	}

	void robotLookAtPlayer() {
		RobotEntity robot = halloranRobot();
		ServerPlayerEntity player = player();
		if (robot == null || player == null || !robot.getNavigation().isIdle()) return;
		Entity target = player.getVehicle() instanceof RobotEntity vehicle ? vehicle : player;
		robot.lookAt(target);
	}

	private long dayIndex() {
		return world().getTimeOfDay() / 24000L;
	}

	// ------------------------------------------------------------------ conditions

	@Nullable
	private RobotEntity findPlayerChassis() {
		List<RobotEntity> robots = world().getEntitiesByClass(RobotEntity.class, podBox(), robot -> !robot.isScripted());
		return robots.isEmpty() ? null : robots.get(0);
	}

	private boolean chassisDeployed() {
		return findPlayerChassis() != null;
	}

	private boolean chairBound() {
		return world().getBlockEntity(origin.add(POD_CHAIR)) instanceof DiveChairBlockEntity chair && chair.getLinkedRobot() != null;
	}

	private boolean playerLinked() {
		ServerPlayerEntity player = player();
		return player != null && PilotManager.isPiloting(player);
	}

	private boolean playerOnline() {
		RobotEntity robot = playerRobot();
		return robot != null && robot.getState() == RobotState.ONLINE;
	}

	private boolean playerOutside() {
		RobotEntity robot = playerRobot();
		return robot != null && robot.getZ() - origin.getZ() > 7.5;
	}

	/** The chassis on the pad, or a face at the airlock glass: within a few blocks of the pad, either way. */
	private boolean playerSeesPad() {
		Vec3d pad = at(HabitatBuilder.ROBOT_PAD);
		RobotEntity robot = playerRobot();
		if (robot != null && robot.squaredDistanceTo(pad) < 49.0) return true;
		ServerPlayerEntity player = player();
		return player != null && player.squaredDistanceTo(pad) < 64.0;
	}

	private boolean sampleDelivered() {
		return world().getBlockEntity(origin.add(HabitatBuilder.ASSAY_CRATE)) instanceof ChestBlockEntity chest && chest.count(ModItems.SULFUR) > 0;
	}

	private boolean outerOpen() {
		BlockState state = world().getBlockState(origin.add(OUTER_DOOR));
		return state.getBlock() instanceof DoorBlock && state.get(DoorBlock.OPEN);
	}

	private boolean playerHasSeeds() {
		ServerPlayerEntity player = player();
		return player != null && player.getInventory().containsAny(stack -> stack.isOf(ModItems.TOMATO_SEEDS));
	}

	// ------------------------------------------------------------------ things that happen

	private void setMorning() {
		ServerWorld world = world();
		if (world.getTimeOfDay() % 24000L < 300L || world.getTimeOfDay() % 24000L > 12500L) {
			world.setTimeOfDay(dayIndex() * 24000L + 1000L);
		}
	}

	private void setDusk() {
		ServerWorld world = world();
		if (world.getTimeOfDay() % 24000L < 12300L) world.setTimeOfDay(dayIndex() * 24000L + 12300L);
	}

	/** One Ballast per pod: a replayed landing must not leave the last one behind. */
	private void cullExtraCats() {
		Box around = new Box(origin).expand(40.0);
		CatEntity keep = cat();
		for (CatEntity cat : world().getEntitiesByClass(CatEntity.class, around, c -> c.hasCustomName())) {
			if (cat == keep) continue;
			if (!cat.getName().getString().equals(Text.translatable("crew.surrogate.cat").getString())) continue;
			if (keep == null) {
				keep = cat;
				state.cat = cat.getUuid();
				state.markDirty();
				continue;
			}
			cat.discard();
		}
	}

	/** The pod's cargo arm puts the chassis on the floor when the player will not. */
	private void podDeploys() {
		podDeploys(true);
	}

	private void podDeploys(boolean announce) {
		ServerWorld world = world();
		ItemStack chassis = takeItem(ModItems.ROBOT_CHASSIS);
		if (chassis == null) chassis = new ItemStack(ModItems.ROBOT_CHASSIS);
		RobotEntity robot = RobotChassisItem.createRobot(world, chassis);
		if (robot == null) return;
		Vec3d at = at(POD_CHASSIS_SPOT);
		robot.refreshPositionAndAngles(at.x, at.y, at.z, 180f, 0f);
		robot.setBodyYaw(180f);
		robot.setHeadYaw(180f);
		if (!world.isSpaceEmpty(robot)) robot.refreshPositionAndAngles(at.x - 1.0, at.y, at.z - 1.0, 180f, 0f);
		if (announce) sendLine(POD, key + "pod3", CinematicPayloads.SYSTEM, -1, true, "");
		world.spawnEntity(robot);
		RobotRegistry.get(server).update(robot);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
		world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 12, 0.4, 0.3, 0.4, 0.02);
	}

	/** Marsh keys the chair from Site Two, which he is not supposed to be able to do. */
	private void podBinds() {
		RobotEntity robot = findPlayerChassis();
		if (robot == null) return;
		if (world().getBlockEntity(origin.add(POD_CHAIR)) instanceof DiveChairBlockEntity chair) {
			chair.setLink(robot.getUuid(), robot.getName().getString());
			Vec3d at = at(POD_CHAIR.getX() + 0.5, POD_CHAIR.getY() + 0.5, POD_CHAIR.getZ() + 0.5);
			world().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.6f, 1.8f);
		}
	}

	/** Whatever a skipped day one would have produced: a chassis on the floor keyed to the chair. */
	private void ensureChassisAndChair() {
		if (!chassisDeployed()) podDeploys(false);
		if (!chairBound()) podBinds();
	}

	/** Takes one of {@code item} out of the pod chest, or failing that out of the player's inventory. */
	@Nullable
	private ItemStack takeItem(net.minecraft.item.Item item) {
		if (world().getBlockEntity(origin.add(POD_CHEST)) instanceof ChestBlockEntity chest) {
			for (int i = 0; i < chest.size(); i++) {
				ItemStack stack = chest.getStack(i);
				if (stack.isOf(item)) {
					ItemStack taken = stack.split(1);
					chest.markDirty();
					return taken;
				}
			}
		}
		ServerPlayerEntity player = player();
		if (player != null) {
			PlayerInventory inventory = player.getInventory();
			for (int i = 0; i < inventory.size(); i++) {
				ItemStack stack = inventory.getStack(i);
				if (stack.isOf(item)) return stack.split(1);
			}
		}
		return null;
	}

	/** Halloran's chassis appears a way south of the pad, ready to walk in. */
	private void robotFar() {
		retireRobot();
		RobotEntity robot = AnnexBuilder.spawnHalloranChassis(world(), atSurface(ROBOT_FAR), 0f, true);
		if (robot != null) {
			state.halloranRobot = robot.getUuid();
			state.markDirty();
		}
	}

	private void snapRobot(Vec3d rel, float yaw) {
		RobotEntity robot = halloranRobot();
		if (robot == null) return;
		Vec3d at = at(rel);
		if (robot.squaredDistanceTo(at) > 9.0) {
			robot.stopDriving();
			robot.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		}
		robot.setBodyYaw(yaw);
		robot.setHeadYaw(yaw);
		Surrogate.LOGGER.info("Prologue: chassis on the pad");
	}

	/** Her chassis has walked out of sight: it is home now, as far as this pod is concerned. */
	private void retireRobot() {
		RobotEntity robot = halloranRobot();
		if (robot != null) {
			RobotRegistry.get(server).remove(robot.getUuid());
			robot.discard();
		}
		state.halloranRobot = null;
		state.markDirty();
	}

	/** The crate comes off her chassis onto the pad: plates, a cell, and a book that is not company reading. */
	private void unloadCrate() {
		ensureCrate();
		Vec3d at = Vec3d.ofCenter(origin.add(CRATE));
		world().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.7f, 0.9f);
		world().spawnParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 16, 0.5, 0.3, 0.5, 0.02);
		RobotEntity robot = halloranRobot();
		if (robot != null) robot.swingHand(Hand.MAIN_HAND);
	}

	private void ensureCrate() {
		BlockPos pos = origin.add(CRATE);
		if (world().getBlockState(pos).isOf(Blocks.CHEST)) return;
		HabitatBuilder.set(world(), pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
		if (world().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.setStack(0, HabitatBuilder.book("common_room", 3));
			chest.setStack(1, new ItemStack(ModItems.HULL_PLATING, 24));
			chest.setStack(2, new ItemStack(ModItems.REINFORCED_GLASS, 8));
			chest.setStack(3, new ItemStack(ModItems.POWER_CELL, 2));
			chest.setStack(4, new ItemStack(ModItems.REPAIR_KIT, 1));
			chest.markDirty();
		}
	}

	private void logSample() {
		if (world().getBlockEntity(origin.add(HabitatBuilder.ASSAY_CRATE)) instanceof ChestBlockEntity chest) {
			state.assaySamples = Math.max(state.assaySamples, chest.count(ModItems.SULFUR));
			state.markDirty();
		}
		Surrogate.LOGGER.info("Prologue: sample logged");
	}

	/** Marsh, on foot, a way south of the pad. Any Marsh the state already knows is gone first. */
	private void marshArrives() {
		CrewEntity old = crew(Crew.MARSH);
		if (old != null) old.discard();
		CrewEntity marsh = AnnexBuilder.spawnPerson(world(), Crew.MARSH, atSurface(MARSH_FAR), 0f);
		state.marsh = marsh == null ? null : marsh.getUuid();
		state.marshVisit = 1;
		state.markDirty();
		Surrogate.LOGGER.info("Prologue: Marsh is walking");
	}

	private void faceDoor(Crew who) {
		CrewEntity crew = crew(who);
		if (crew != null) {
			crew.stopWalking();
			crew.face(180f);
		}
	}

	private void setDoor(BlockPos rel, boolean open) {
		BlockPos pos = origin.add(rel);
		BlockState doorState = world().getBlockState(pos);
		if (doorState.getBlock() instanceof AirlockDoorBlock airlock && doorState.get(DoorBlock.OPEN) != open) airlock.setOpenScripted(world(), pos, open);
	}

	private void marshWorks() {
		CrewEntity marsh = crew(Crew.MARSH);
		if (marsh == null) return;
		marsh.swingHand(Hand.MAIN_HAND);
		Vec3d at = Vec3d.ofCenter(origin.add(POD_DOCK));
		world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z + 0.4, 10, 0.2, 0.2, 0.1, 0.1);
		world().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.4f, 1.6f);
	}

	/** The dock charges honestly from now on: full, and a note in the log. */
	private void fixDock() {
		if (world().getBlockEntity(origin.add(POD_DOCK)) instanceof ChargingDockBlockEntity dock) {
			dock.addEnergy(Surrogate.CONFIG.dockEnergyCapacity);
			Vec3d at = Vec3d.ofCenter(origin.add(POD_DOCK));
			world().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.6f, 1.4f);
		}
		Surrogate.LOGGER.info("Prologue: dock fixed");
	}

	/** He said he always brings his own: one appears in his hand for the job and goes back in his pocket after. */
	private void marshOwnWrench() {
		CrewEntity marsh = crew(Crew.MARSH);
		if (marsh != null) marsh.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WRENCH));
	}

	private void returnWrench() {
		CrewEntity marsh = crew(Crew.MARSH);
		ServerPlayerEntity player = player();
		if (marsh == null) return;
		ItemStack held = marsh.getEquippedStack(EquipmentSlot.MAINHAND);
		marsh.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		if (wrenchGiven && player != null && !held.isEmpty()) player.giveItemStack(held);
	}

	/** Marsh is over the ridge. His clipboard is not. */
	private void marshGone() {
		CrewEntity marsh = crew(Crew.MARSH);
		if (marsh != null) marsh.discard();
		state.marsh = null;
		state.marshVisit = 2;
		state.markDirty();
		Vec3d at = at(TABLE_TOP);
		ItemEntity clipboard = new ItemEntity(world(), at.x, at.y, at.z, HabitatBuilder.book("forms", 4));
		clipboard.setVelocity(Vec3d.ZERO);
		clipboard.setPickupDelay(10);
		world().spawnEntity(clipboard);
		Surrogate.LOGGER.info("Prologue: Marsh has gone home");
	}

	/** The first survivor's voice, through the pod set. */
	private void radioCall() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		String lineKey = SurvivorManager.get(server).call(server, player, Survivor.OKAFOR);
		sendLine(Survivor.OKAFOR.nameKey(), lineKey, CinematicPayloads.RADIO, -1, false, "");
		Surrogate.LOGGER.info("Prologue: radio call {}", lineKey);
	}
}
