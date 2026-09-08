package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.ChairBlock;
import dev.psyda.surrogate.block.HouseContainerBlock;
import dev.psyda.surrogate.block.HouseContainerBlockEntity;
import dev.psyda.surrogate.block.TelevisionBlock;
import dev.psyda.surrogate.entity.SeatEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.prologue.AnnexBuilder;
import dev.psyda.surrogate.prologue.Beat;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariants;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The night you packed.
 *
 * <p>You sleep in the pod for the first or second time and you are somewhere else: a corridor with three
 * doors at the end of it, marked HOME, THE OFFICE and THE BAR. Where were you, the night before you shipped?
 * You walk through one and that becomes true. Behind it is the whole place — a house with a kitchen and a
 * dog and an upstairs, an office floor at eleven at night, a bar at last orders — and somebody in it who asks
 * you two things about yourself. Then the evening goes on without you for a while, and when it comes back
 * the lights are off and it is late, and somewhere in the building there is a case with your name on it.
 *
 * <p>Whatever is in that case when you walk out is beside your bed when you wake. Everything in the place
 * comes off in your hands, and nobody is going to tell you that.
 */
public final class Flashback extends Director {
	private static final String KEY = "cinematic.surrogate.flashback.";

	/** How near a threshold counts as walking through it. */
	private static final double REACH = 1.8;
	/** How long each question waits before it answers itself, and how long the packing runs. */
	private static final int ASK_TIMEOUT = 4800;
	private static final int PACK_TIMEOUT = 14000;
	/** When the car outside sounds its horn, into the packing. */
	private static final int CAR_AFTER = 5200;
	/** How long the small errands wait before the evening carries on without them. */
	private static final int ERRAND_TIMEOUT = 2400;
	private static final int UPSTAIRS_TIMEOUT = 6000;
	/** How many nights on the planet before the first one, and how many between the ones after it. */
	private static final int WITHIN_DAYS = 2;
	private static final int NIGHTS_BETWEEN = 3;

	@Nullable
	private static Flashback running;
	/** Slows the "is anybody asleep" check down to twice a second. */
	private static int idle;

	private final FlashbackState state;
	private final BlockPos origin = DreamDimension.ORIGIN;

	@Nullable
	private DreamPlace place;
	private int reason = -1;
	@Nullable
	private CrewEntity figure;
	/** Whether this is a second or third night rather than the first, which changes what the dark says. */
	private boolean returning;

	// What the player has done tonight, noted by the blocks and items as they are used.
	private boolean ate;
	private boolean drank;
	private boolean dartHit;
	private final Set<String> read = new HashSet<>();
	/** Whether the evening has moved on: the figure gone, the lights off. Changes the ambience. */
	private boolean later;
	private boolean figureGone;
	private int ambientClock = 200;
	private int loopClock = 40;

	private Flashback(MinecraftServer server, FlashbackState state) {
		super(server, KEY);
		this.state = state;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	/**
	 * Its own every-tick clock, like the other directors. The scene wants real ticks: a script run off a
	 * sweep's every-tenth reads every line ten times too slowly and never finishes anything.
	 */
	public static void registerEvents() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Flashback::tick);
		DreamRules.registerEvents();
	}

	/** Called every tick. One condition, and it is the only one: they went to bed early on. */
	public static void tick(MinecraftServer server) {
		if (running != null) {
			running.keepInside();
			running.ambience();
			running.tick();
			return;
		}
		if (++idle < 10) return;
		idle = 0;
		if (!Surrogate.CONFIG.flashback) return;
		FlashbackState state = FlashbackState.get(server);
		if (state.played) return;
		HabitatState habitat = HabitatState.get(server);
		if (habitat.origin == null || habitat.landedDay < 0L) return;
		ServerWorld world = server.getOverworld();
		long today = world.getTimeOfDay() / 24000L;
		// The first one is the first or second night down here. The ones after it are a few nights apart,
		// and they keep coming while there is a door left: three places and one dream would be two thirds of
		// a scene nobody ever reads.
		boolean due = state.lastNight < 0L
				? today - habitat.landedDay <= WITHIN_DAYS
				: today - state.lastNight >= NIGHTS_BETWEEN;
		if (!due) return;
		if (Director.current() != null) return;
		ServerPlayerEntity player = protagonist(server);
		if (player == null || !player.isSleeping() || player.getWorld().getRegistryKey() != World.OVERWORLD) return;
		begin(server, state, player);
	}

	private static void begin(MinecraftServer server, FlashbackState state, ServerPlayerEntity player) {
		if (DreamDimension.world(server) == null) {
			Surrogate.LOGGER.warn("Flashback: no dream dimension in this world; skipping");
			state.played = true;
			state.markDirty();
			return;
		}
		state.remember(player.getBlockPos(), player.getWorld().getRegistryKey(), player.getYaw());
		running = new Flashback(server, state);
		running.takeStage();
		Surrogate.LOGGER.info("Flashback: asleep at {}", player.getBlockPos().toShortString());
	}

	/** For the dev command: run it now, wherever the player is standing. */
	public static void force(MinecraftServer server, ServerPlayerEntity player) {
		force(server, player, null);
	}

	/**
	 * For the dev command: run it now, and if {@code where} is given, skip the corridor and go straight to
	 * that place, as if the player had walked through its door. The corridor still gets built, so a client
	 * pointed at it sees it; the door is simply already answered.
	 */
	public static void force(MinecraftServer server, ServerPlayerEntity player, @Nullable DreamPlace where) {
		Director.clearStage();
		running = null;
		// The dev client's quarter-length switch, honoured here the way the ship week and the landing do.
		if (Boolean.getBoolean("surrogate.devFast")) Director.fast = true;
		FlashbackState state = FlashbackState.get(server);
		state.played = false;
		if (where != null) state.visited &= ~(1 << where.ordinal());
		state.markDirty();
		begin(server, state, player);
		if (running != null) running.forced = where;
	}

	/** A place the dev command chose in advance, taken the first time the first question is checked. */
	@Nullable
	private DreamPlace forced;

	/** What the player has done tonight, for the dev command and the smoke test. */
	public static String notes() {
		if (running == null) return "none";
		StringBuilder out = new StringBuilder();
		if (running.ate) out.append("ate ");
		if (running.drank) out.append("drank ");
		if (running.dartHit) out.append("dart ");
		for (String doc : running.read) out.append("read:").append(doc).append(" ");
		return out.length() == 0 ? "nothing" : out.toString().trim();
	}

	public static boolean isRunning() {
		return running != null;
	}

	/** Which place tonight is, for the dev command. */
	@Nullable
	public static DreamPlace placeTonight() {
		return running == null ? null : running.place;
	}

	/** Where the script is, for the dev command and the smoke test that paces itself off it. */
	public static String label() {
		return running == null ? "none" : running.currentLabel();
	}

	@Nullable
	private static ServerPlayerEntity protagonist(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		if (habitat.protagonist != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(habitat.protagonist);
			if (found != null) return found;
		}
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		return players.isEmpty() ? null : players.get(0);
	}

	// ------------------------------------------------------------------ what the room tells the script

	/** A slice of the pizza went down. */
	public static void noteAte(ServerPlayerEntity player) {
		if (running != null && DreamDimension.isDream(player.getWorld())) running.ate = true;
	}

	/** Something was held up and read: the bills, the calendar, the contract. */
	public static void noteRead(ServerPlayerEntity player, String document) {
		if (running != null && DreamDimension.isDream(player.getWorld())) running.read.add(document);
	}

	public static void noteDrank(ServerPlayerEntity player) {
		if (running != null && DreamDimension.isDream(player.getWorld())) running.drank = true;
	}

	public static void noteDart(BlockPos board) {
		if (running != null) running.dartHit = true;
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		label("asleep");
		// Going under is the slowest thing in the scene on purpose. The old cut was thirty ticks to black and
		// thirty back, which is a scene change; this is closer to the time it actually takes somebody to stop
		// being in a room.
		run(this::goingUnder);
		fade(255, 60);
		wait(70);
		run(this::enterDream);
		wait(30);
		fade(0, 70);
		wait(45);
		narrationOf(() -> KEY + (returning ? "again_1" : "open_1"));
		narrationOf(() -> KEY + (returning ? "again_2" : "open_2"));

		label("where");
		narration(KEY + "where");
		objective("where");
		hint("hint.where");
		until(this::chosePlace, ASK_TIMEOUT, null, null, 0, this::pickForThem);
		objectiveDone();

		label("room");
		// The room does not cut in. It comes up the way a memory does, which is slowly and then all at once.
		fade(255, 35);
		wait(42);
		run(this::enterRoom);
		wait(16);
		fade(0, 55);
		wait(40);
		narrationOf(() -> place == null ? "" : place.line("arrive"));
		wait(10);
		jumpIf(() -> place == DreamPlace.WORK, "work");
		jumpIf(() -> place == DreamPlace.BAR, "bar");

		// ---- Home: the living room, the box on the table, somebody in the kitchen doorway, the news.
		label("home");
		objective("eat");
		hint("hint.eat");
		until(() -> ate, scaled(ERRAND_TIMEOUT), null, null, 0, null);
		objectiveDone();
		wait(30);
		run(this::figureNotices);
		narration(KEY + "home_figure");
		say(Crew.FIGURE, "home_q1");
		ask(() -> question(0), ASK_TIMEOUT);
		wait(10);
		say(Crew.FIGURE, "home_q2");
		ask(() -> question(1), ASK_TIMEOUT);
		wait(10);
		narrationOf(this::reasonLine);
		say(Crew.FIGURE, "home_up");
		run(this::figureLeaves);
		wait(70);
		run(this::figureGone);
		objective("watch");
		hint("hint.watch");
		run(this::newsOn);
		tv("tv_1");
		tv("tv_2");
		tv("tv_3");
		tv("ad_1");
		tv("ad_2");
		objectiveDone();
		jumpIf(() -> true, "later");

		// ---- The office: the contract on the screen, and the person two desks over who comes to say goodbye.
		label("work");
		objective("read");
		hint("hint.read");
		until(() -> read.contains("contract"), scaled(ERRAND_TIMEOUT), null, null, 0, null);
		objectiveDone();
		wait(30);
		run(this::figureComesOver);
		wait(90);
		run(this::figureNotices);
		narration(KEY + "work_figure");
		say(Crew.COWORKER, "work_q1");
		ask(() -> question(0), ASK_TIMEOUT);
		wait(10);
		say(Crew.COWORKER, "work_q2");
		ask(() -> question(1), ASK_TIMEOUT);
		wait(10);
		narrationOf(this::reasonLine);
		say(Crew.COWORKER, "work_go");
		run(this::figureLeaves);
		wait(90);
		run(this::figureGone);
		jumpIf(() -> true, "later");

		// ---- The bar: a pint, three darts, and the barman calling last orders.
		label("bar");
		objective("dart");
		hint("hint.dart");
		until(() -> dartHit || drank, scaled(ERRAND_TIMEOUT), null, null, 0, null);
		objectiveDone();
		wait(30);
		run(this::figureNotices);
		narration(KEY + "bar_figure");
		say(Crew.BARMAN, "bar_q1");
		ask(() -> question(0), ASK_TIMEOUT);
		wait(10);
		say(Crew.BARMAN, "bar_q2");
		ask(() -> question(1), ASK_TIMEOUT);
		wait(10);
		narrationOf(this::reasonLine);
		say(Crew.BARMAN, "bar_go");
		run(this::figureLeaves);
		wait(80);
		run(this::figureGone);

		// ---- Later. The evening goes on without you for a while.
		label("later");
		wait(40);
		fade(255, 90);
		wait(60);
		run(this::evening);
		wait(40);
		fade(0, 90);
		wait(20);
		narrationOf(() -> place == null ? "" : place.line("later"));
		run(this::standUp);
		wait(25);
		narrationOf(() -> place == null ? "" : place.line("quiet"));
		jumpIf(() -> place != DreamPlace.HOME, "pack");
		objective("upstairs");
		hint("hint.upstairs");
		until(this::upstairs, scaled(UPSTAIRS_TIMEOUT), null, null, 0, null);
		objectiveDone();
		until(this::inBedroom, scaled(ERRAND_TIMEOUT), null, null, 0, null);
		narrationOf(() -> inBedroom() ? KEY + "home.bedroom" : "");

		label("pack");
		run(this::openTheWayOut);
		narrationOf(() -> place == null ? "" : place.line("pack"));
		objective("pack");
		hintOf(() -> place == null ? "hint.pack" : "hint.pack_" + place.key());
		until(this::atExit, PACK_TIMEOUT, CAR_AFTER, this::theCar, null);
		objectiveDone();

		label("wake");
		fade(255, 55);
		wait(65);
		run(this::wake);
		wait(30);
		fade(0, 60);
		wait(30);
		narrationOf(() -> state.anythingLeft() ? "" : KEY + "done");
		run(this::finish);
	}

	/** A line off the television: the same subtitle as a person, under the name of the set. */
	private void tv(String lineKey) {
		beats.add(new Beat.Line(null, "flashback.surrogate.television", KEY + lineKey, CinematicPayloads.SPEECH, -1, true, ""));
	}

	/** A hint whose key depends on the room, resolved when the beat starts. */
	private void hintOf(java.util.function.Supplier<String> hintKey) {
		beats.add(new Beat.Run(() -> {
			String resolved = hintKey.get();
			this.hintKey = resolved;
			send(new CinematicPayloads.Hint(key + resolved));
		}));
	}

	/** Whoever is in the dream stays on its floor. Belt and braces over the unbreakable shell. */
	private void keepInside() {
		ServerPlayerEntity player = player();
		ServerWorld dream = DreamDimension.world(server);
		if (player == null || dream == null) return;
		Vec3d back = place == null ? Vec3d.of(origin).add(DreamBuilder.WAKES) : DreamBuilder.floorBesideSeat(origin, place);
		DreamRules.keepInside(dream, player, back);
	}

	// ------------------------------------------------------------------ going under

	/** The last thing they hear on the right side of it. */
	private void goingUnder() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		returning = state.lastNight >= 0L;
		player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.35f, 0.55f);
	}

	/** Out of the bed and into the corridor, with everything they own put away for the night. */
	private void enterDream() {
		ServerPlayerEntity player = player();
		ServerWorld dream = DreamDimension.world(server);
		if (player == null || dream == null) return;
		if (player.isSleeping()) player.wakeUp(true, false);
		stash(player);
		DreamRules.clearEntities(dream);
		DreamBuilder.hall(dream, origin, where -> !state.hasVisited(where));
		Vec3d at = Vec3d.of(origin).add(DreamBuilder.WAKES);
		player.teleport(dream, at.x, at.y, at.z, java.util.Set.of(), 180f, 0f);
		player.setVelocity(Vec3d.ZERO);
		player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 0.4f, 0.6f);
	}

	/**
	 * Everything the player is carrying, put away in the save.
	 *
	 * <p>In the state rather than on this object, because a server that stops in the middle of a dream must
	 * not be a server that ate somebody's inventory. Written once, given back once, cleared when it is.
	 */
	private void stash(ServerPlayerEntity player) {
		NbtList list = new NbtList();
		player.getInventory().writeNbt(list);
		state.stashed = list;
		state.markDirty();
		player.getInventory().clear();
	}

	// ------------------------------------------------------------------ the first question

	/** Whichever door they walked through, if they have. */
	private boolean chosePlace() {
		ServerPlayerEntity player = player();
		if (player == null || player.getWorld().getRegistryKey() != DreamDimension.WORLD) return false;
		if (forced != null) {
			place = forced;
			forced = null;
			state.answered(place, -1);
			Surrogate.LOGGER.info("Flashback: the dev command says it was the {}", place.key());
			return true;
		}
		for (DreamPlace candidate : DreamPlace.all()) {
			if (state.hasVisited(candidate)) continue;
			if (player.getPos().squaredDistanceTo(DreamBuilder.threshold(origin, candidate)) < REACH * REACH) {
				place = candidate;
				state.answered(candidate, -1);
				Surrogate.LOGGER.info("Flashback: they say it was the {}", candidate.key());
				return true;
			}
		}
		return false;
	}

	/** Nobody moved. The dream picks, because a question you refuse to answer still gets answered. */
	private void pickForThem() {
		List<DreamPlace> left = new ArrayList<>();
		for (DreamPlace candidate : DreamPlace.all()) {
			if (!state.hasVisited(candidate)) left.add(candidate);
		}
		if (left.isEmpty()) return;
		place = left.get(world().random.nextInt(left.size()));
		state.answered(place, -1);
		Surrogate.LOGGER.info("Flashback: nobody chose, so it was the {}", place.key());
	}

	// ------------------------------------------------------------------ the room

	/** The whole place, built around them while the screen is black, with them sat down in the middle of it. */
	private void enterRoom() {
		ServerPlayerEntity player = player();
		ServerWorld dream = DreamDimension.world(server);
		if (player == null || dream == null || place == null) return;
		DreamRules.clearEntities(dream);
		DreamBuilder.build(dream, origin, place);
		spawnCompany(dream, player);
		seat(dream, player);
		if (place == DreamPlace.BAR) player.giveItemStack(new ItemStack(ModItems.DART, 3));
		if (place == DreamPlace.HOME || place == DreamPlace.BAR) {
			TelevisionBlock.quiet(dream, DreamBuilder.television(origin, place), 400);
		}
		ambientClock = 200;
		loopClock = 40;
	}

	/** Whoever was there: the figure in the kitchen, the coworker at their desk, the barman. And the dog. */
	private void spawnCompany(ServerWorld dream, ServerPlayerEntity player) {
		if (place == null) return;
		Vec3d at = DreamBuilder.figureAt(origin, place);
		figure = AnnexBuilder.spawnPerson(dream, place.who(), at, DreamBuilder.figureYaw(place));
		if (figure != null && place == DreamPlace.WORK) figure.sitAt(at, DreamBuilder.figureYaw(place));
		if (place == DreamPlace.HOME) spawnDog(dream, player);
	}

	/** The dog. Yours, with a collar on, wandering the kitchen, and not going to be told it cannot come. */
	private void spawnDog(ServerWorld dream, ServerPlayerEntity player) {
		WolfEntity dog = EntityType.WOLF.create(dream);
		if (dog == null) return;
		Vec3d at = Vec3d.of(DreamBuilder.site(origin, DreamPlace.HOME)).add(DreamBuilder.HOME_DOG);
		dog.refreshPositionAndAngles(at.x, at.y, at.z, 90f, 0f);
		dog.setTamed(true, true);
		dog.setOwner(player);
		dog.setCustomName(Text.translatable("flashback.surrogate.dog"));
		dog.setCustomNameVisible(false);
		dog.setPersistent();
		dream.getRegistryManager().get(RegistryKeys.WOLF_VARIANT).getEntry(WolfVariants.CHESTNUT).ifPresent(dog::setVariant);
		dream.spawnEntity(dog);
	}

	/** On the couch, in the chair, on the stool: facing whatever the room is about. */
	private void seat(ServerWorld dream, ServerPlayerEntity player) {
		if (place == null) return;
		Vec3d floor = DreamBuilder.floorBesideSeat(origin, place);
		Direction facing = DreamBuilder.seatFacing(place);
		player.teleport(dream, floor.x, floor.y, floor.z, java.util.Set.of(), facing.asRotation(), 0f);
		player.setVelocity(Vec3d.ZERO);
		ChairBlock.sit(dream, DreamBuilder.seat(origin, place), facing, DreamBuilder.seatHeight(place), player);
	}

	// ------------------------------------------------------------------ the figure, and what they ask

	private void figureNotices() {
		if (figure != null && figure.isAlive()) figure.setLookTarget(player());
	}

	/** The coworker gets up and comes over, which is the only walking anybody in the dream does towards you. */
	private void figureComesOver() {
		if (figure == null || !figure.isAlive() || place == null) return;
		figure.stand();
		Vec3d to = Vec3d.of(DreamBuilder.site(origin, place)).add(DreamBuilder.WORK_COWORKER_STANDS);
		figure.walkTo(to, null);
	}

	/** They go: up the stairs, to the lift, through to the back. */
	private void figureLeaves() {
		if (figure == null || !figure.isAlive() || place == null) return;
		figure.setLookTarget(null);
		figure.stand();
		figure.walkTo(DreamBuilder.figureLeavesTo(origin, place), null);
	}

	/** And are gone, with the sound of wherever they went to. */
	private void figureGone() {
		hideFigure();
		figureGone = true;
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return;
		switch (place) {
			case HOME -> {
				soundIn(dream, DreamBuilder.HOME_UPSTAIRS_NOISE, ModSounds.FLASHBACK_FOOTSTEPS_UPSTAIRS, 0.7f, 1.0f);
				schedule(60, () -> soundIn(dream, DreamBuilder.HOME_UPSTAIRS_NOISE, ModSounds.FLASHBACK_DOOR_UPSTAIRS, 0.6f, 1.0f));
			}
			case WORK -> soundIn(dream, DreamBuilder.WORK_LIFT, ModSounds.FLASHBACK_LIFT_DING, 0.7f, 1.0f);
			case BAR -> soundIn(dream, DreamBuilder.BAR_BARMAN_LEAVES, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 0.5f, 0.9f);
		}
	}

	private void hideFigure() {
		if (figure != null && figure.isAlive()) figure.discard();
		figure = null;
	}

	/** One of the two questions this room asks, resolved when the beat starts rather than when it was written. */
	private Question question(int which) {
		DreamPlace where = place == null ? DreamPlace.HOME : place;
		return new Question(where.askId(which), where.askPrompt(which), where.who().nameKey(), where.askOptions(which));
	}

	@Override
	protected void onAnswer(Question question, int index) {
		DreamPlace where = place == null ? DreamPlace.HOME : place;
		int which = question.id().endsWith(".1") ? 1 : 0;
		state.answer(question.id(), where.askOption(which, index));
		// The second answer is also why you were there, which is what the rest of the game reads back.
		if (which == 1) answerReason(index);
		Surrogate.LOGGER.info("Flashback: {} -> {}", question.id(), index);
	}

	private void answerReason(int which) {
		reason = which;
		if (place != null) {
			state.answered(place, which);
			Surrogate.LOGGER.info("Flashback: {} because of {}", place.key(), place.reasonKey(which));
		}
	}

	private String reasonLine() {
		return place == null || reason < 0 ? "" : place.line("reason." + place.reasonKey(reason));
	}

	// ------------------------------------------------------------------ the television

	/** The set does its own murmuring; while the anchor talks it keeps quiet, and the news has a sting. */
	private void newsOn() {
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return;
		BlockPos set = DreamBuilder.television(origin, place);
		BlockState state = dream.getBlockState(set);
		if (state.getBlock() instanceof TelevisionBlock) {
			// Quiet first: tuning a set plays its noise at once, and the anchor is about to talk over it.
			TelevisionBlock.quiet(dream, set, 2400);
			TelevisionBlock.tune(dream, set, state, TelevisionBlock.Channel.NEWS, false);
		}
		dream.playSound(null, set, ModSounds.FLASHBACK_NEWS_STING, SoundCategory.RECORDS, 0.6f, 1.0f);
	}

	// ------------------------------------------------------------------ later

	/** The evening moves on without you: lights, the set, the clock, whoever was here. */
	private void evening() {
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return;
		hideFigure();
		figureGone = true;
		later = true;
		switch (place) {
			case HOME -> DreamBuilder.houseLater(dream, origin);
			case WORK -> DreamBuilder.officeLater(dream, origin);
			case BAR -> DreamBuilder.barLater(dream, origin);
		}
	}

	/** Up off the couch, with the noise the couch makes about it. */
	private void standUp() {
		ServerPlayerEntity player = player();
		ServerWorld dream = DreamDimension.world(server);
		if (player == null || dream == null) return;
		SeatEntity.standUp(player);
		dream.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FLASHBACK_CREAK, SoundCategory.AMBIENT, 0.8f, 1.0f);
	}

	private boolean upstairs() {
		ServerPlayerEntity player = player();
		return player != null && DreamBuilder.isUpstairs(origin, player.getPos());
	}

	private boolean inBedroom() {
		ServerPlayerEntity player = player();
		return player != null && DreamBuilder.isInBedroom(origin, player.getPos());
	}

	/** The door out becomes a door out: the porch beyond it, or the lift, is there now. */
	private void openTheWayOut() {
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return;
		switch (place) {
			case HOME -> DreamBuilder.openHouse(dream, origin);
			case WORK -> {
				DreamBuilder.openOffice(dream, origin);
				soundIn(dream, DreamBuilder.WORK_LIFT, ModSounds.FLASHBACK_LIFT_DING, 0.6f, 1.0f);
			}
			case BAR -> DreamBuilder.openBar(dream, origin);
		}
	}

	/** The car is here. Said once, into the packing, and then the packing goes on. */
	private void theCar() {
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return;
		Vec3d outside = switch (place) {
			case HOME -> DreamBuilder.HOME_STREET;
			case WORK -> DreamBuilder.WORK_LIFT;
			case BAR -> DreamBuilder.BAR_EXIT;
		};
		soundIn(dream, outside, place == DreamPlace.WORK ? ModSounds.FLASHBACK_LIFT_DING : ModSounds.FLASHBACK_CAR_HORN, 0.8f, 1.0f);
		sendLine("", KEY + (place == DreamPlace.WORK ? "car_work" : "car"), CinematicPayloads.NARRATION, -1, false, "");
	}

	private boolean atExit() {
		ServerPlayerEntity player = player();
		if (player == null || place == null || player.getWorld().getRegistryKey() != DreamDimension.WORLD) return false;
		return player.getPos().squaredDistanceTo(DreamBuilder.exit(origin, place)) < REACH * REACH;
	}

	// ------------------------------------------------------------------ the sound of the place

	/**
	 * A house at night has cars going past it and somebody moving about upstairs; an office has its lights
	 * humming; a bar has other people in it. None of it is a beat, so it runs off its own clocks here.
	 */
	private void ambience() {
		ServerWorld dream = DreamDimension.world(server);
		ServerPlayerEntity player = player();
		if (dream == null || player == null || place == null || player.getWorld() != dream) return;
		if (--loopClock <= 0) {
			switch (place) {
				case WORK -> {
					if (!later) soundAt(dream, player.getPos(), ModSounds.FLASHBACK_FLUORESCENT_HUM, 0.22f, 1.0f);
					loopClock = 118;
				}
				case BAR -> {
					if (!later) soundIn(dream, DreamBuilder.BAR_BARMAN, ModSounds.FLASHBACK_PUB_MURMUR, 0.35f, 1.0f);
					loopClock = 158;
				}
				default -> loopClock = 200;
			}
		}
		if (--ambientClock > 0) return;
		ambientClock = 320 + dream.random.nextInt(480);
		switch (place) {
			case HOME -> {
				int roll = dream.random.nextInt(figureGone ? 3 : 2);
				if (roll == 0) soundIn(dream, DreamBuilder.HOME_STREET, ModSounds.FLASHBACK_CAR_PASS, 0.5f, 0.95f + dream.random.nextFloat() * 0.1f);
				else if (roll == 1) soundIn(dream, DreamBuilder.HOME_STREET, ModSounds.FLASHBACK_CAR_PASS, 0.35f, 0.9f);
				else soundIn(dream, DreamBuilder.HOME_UPSTAIRS_NOISE, ModSounds.FLASHBACK_FOOTSTEPS_UPSTAIRS, 0.5f, 1.0f);
			}
			case WORK -> {
				if (later) soundIn(dream, Vec3d.ofCenter(DreamBuilder.WORK_PRINTER), ModSounds.FLASHBACK_PRINTER, 0.4f, 1.0f);
				else soundIn(dream, DreamBuilder.WORK_LIFT, ModSounds.FLASHBACK_LIFT_DING, 0.25f, 0.9f);
			}
			case BAR -> {
				if (!later) soundIn(dream, DreamBuilder.BAR_BARMAN, ModSounds.FLASHBACK_GLASS_CLINK, 0.5f, 1.0f);
				else soundIn(dream, DreamBuilder.BAR_EXIT, ModSounds.FLASHBACK_CAR_PASS, 0.3f, 0.9f);
			}
		}
	}

	/** A sound somewhere in the world. */
	private void soundAt(ServerWorld dream, Vec3d at, SoundEvent sound, float volume, float pitch) {
		dream.playSound(null, at.x, at.y, at.z, sound, SoundCategory.AMBIENT, volume, pitch);
	}

	/** A sound at a spot the builder names, which is relative to the corner of tonight's place. */
	private void soundIn(ServerWorld dream, Vec3d rel, SoundEvent sound, float volume, float pitch) {
		if (place == null) return;
		soundAt(dream, Vec3d.of(DreamBuilder.site(origin, place)).add(rel), sound, volume, pitch);
	}

	/** Something to do a little later, without a beat: the door after the footsteps. */
	private final List<int[]> pendingTicks = new ArrayList<>();
	private final List<Runnable> pending = new ArrayList<>();

	private void schedule(int ticks, Runnable what) {
		pendingTicks.add(new int[]{ticks});
		pending.add(what);
	}

	@Override
	protected void tick() {
		for (int i = pending.size() - 1; i >= 0; i--) {
			if (--pendingTicks.get(i)[0] <= 0) {
				Runnable what = pending.remove(i);
				pendingTicks.remove(i);
				what.run();
			}
		}
		super.tick();
	}

	// ------------------------------------------------------------------ waking

	/**
	 * Back to the bed. Their own things returned, the dream's things left in the dream, and whatever was in
	 * the case standing beside them in the same case.
	 */
	private void wake() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		SeatEntity.standUp(player);
		List<ItemStack> packed = emptyCases();
		ServerWorld home = server.getWorld(state.wakeWorld);
		if (home == null) home = server.getOverworld();
		Vec3d at = state.wakeAt();
		player.teleport(home, at.x, at.y, at.z, java.util.Set.of(), state.wakeYaw, 0f);
		player.setVelocity(Vec3d.ZERO);
		restore(player);
		deliver(home, BlockPos.ofFloored(at), packed);
		player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 0.3f, 0.7f);
		state.finish(packed.size(), server.getOverworld().getTimeOfDay() / 24000L);
		player.sendMessage(Text.translatable(KEY + (packed.isEmpty() ? "woke" : "woke_packed"))
				.formatted(Formatting.GRAY), false);
	}

	/**
	 * What they packed, taken out of every case and box in the place so nothing is duplicated by leaving it
	 * behind. Every one of them, not just the one the builder put down: a player who moved the case has
	 * still packed it.
	 */
	private List<ItemStack> emptyCases() {
		List<ItemStack> packed = new ArrayList<>();
		ServerWorld dream = DreamDimension.world(server);
		if (dream == null || place == null) return packed;
		BlockPos site = DreamBuilder.site(origin, place);
		for (BlockPos pos : BlockPos.iterate(site.add(-14, -1, -12), site.add(14, 10, 14))) {
			BlockState state = dream.getBlockState(pos);
			if (!state.isOf(ModBlocks.SUITCASE) && !state.isOf(ModBlocks.CARDBOARD_BOX)) continue;
			if (!(dream.getBlockEntity(pos) instanceof Inventory inventory)) continue;
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.removeStack(slot);
				if (!stack.isEmpty()) packed.add(stack);
			}
			inventory.markDirty();
		}
		return packed;
	}

	private void restore(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		inventory.clear();
		if (state.stashed != null) inventory.readNbt(state.stashed);
		state.stashed = null;
		state.markDirty();
	}

	/**
	 * The case, on the floor beside the bed. The same case, or the box from under the desk, rather than a
	 * handful of items in the player's pockets, because the point of the scene is that the thing followed
	 * them home.
	 */
	private void deliver(ServerWorld world, BlockPos bed, List<ItemStack> packed) {
		if (packed.isEmpty()) return;
		BlockPos spot = null;
		Direction toward = Direction.NORTH;
		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos candidate = bed.offset(side);
			if (world.isAir(candidate) && world.getBlockState(candidate.down()).isSolidBlock(world, candidate.down())) {
				spot = candidate;
				toward = side.getOpposite();
				break;
			}
		}
		if (spot == null) {
			for (ItemStack stack : packed) {
				ItemScatterer.spawn(world, bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5, stack);
			}
			return;
		}
		Block container = place == DreamPlace.WORK ? ModBlocks.CARDBOARD_BOX : ModBlocks.SUITCASE;
		world.setBlockState(spot, container.getDefaultState().with(HouseContainerBlock.FACING, toward), Block.NOTIFY_ALL);
		if (!(world.getBlockEntity(spot) instanceof HouseContainerBlockEntity box)) {
			for (ItemStack stack : packed) ItemScatterer.spawn(world, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, stack);
			return;
		}
		int slot = 0;
		for (ItemStack stack : packed) {
			if (slot >= box.size()) {
				ItemScatterer.spawn(world, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, stack);
				continue;
			}
			box.setStack(slot++, stack);
		}
		box.markDirty();
	}

	private void finish() {
		hideFigure();
		ServerWorld dream = DreamDimension.world(server);
		if (dream != null) DreamRules.clearEntities(dream);
		resetClient();
		leaveStage();
		running = null;
		Surrogate.LOGGER.info("Flashback: awake, {} thing(s) in the case", state.packed);
	}

	// ------------------------------------------------------------------ Director's plumbing

	/**
	 * No. The whole scene is its narration, and sneak is a key people lean on without meaning anything by
	 * it — half a dream can go past under a thumb resting on shift. Holding the skip key still works, and
	 * still ends the night properly.
	 */
	@Override
	protected boolean allowAdvance() {
		return false;
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		// A skipped dream still has to end with the player in their own bed holding their own things: the
		// stash is the dangerous part, and leaving it in the save would be losing an inventory.
		//
		// It does not decide anything, though. Skipping before a door is chosen leaves all three of them open
		// for next time, because the player did not see that room and marking it visited would charge them
		// for a scene they never got. Skipping after one is chosen keeps it: they were in there.
		if (place != null && reason < 0) answerReason(0);
		Surrogate.LOGGER.info("Flashback: skipped at {}", currentLabel());
		wake();
		finish();
	}

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		return protagonist(server);
	}

	@Override
	public ServerWorld world() {
		ServerWorld dream = DreamDimension.world(server);
		return dream == null ? server.getOverworld() : dream;
	}

	@Override
	public BlockPos origin() {
		return origin;
	}

	/**
	 * Only ever one person, and only while they are standing there.
	 *
	 * <p>The rest of the dream is empty on purpose. The figure is the exception, and they are only in it for
	 * as long as it takes to ask two questions.
	 */
	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		return place != null && who == place.who() && figure != null && figure.isAlive() ? figure : null;
	}
}
