package dev.psyda.surrogate.rescue;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

/**
 * Where the rescue stands: acts four and five, from the day the ship did not come down to the day the last
 * of them is off the far side.
 *
 * <p>Almost nothing in here is a step in a script. The three rescues happen in whatever order the world
 * allows, so what is saved is the state of the obstacles rather than a stage number: how much bridge is
 * down, whether the flow across Brandt's approach has been cut, whether Clinic Nine's frame has its plates
 * back. Who is home is not saved here at all; that is
 * {@link dev.psyda.surrogate.survivor.SurvivorManager}'s to know, and asking it means a player who got
 * somebody home by some route nobody planned still counts as having got them home.
 */
public class RescueState extends PersistentState {
	private static final Type<RescueState> TYPE = new Type<>(RescueState::new, RescueState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static final int STAGE_NONE = 0;
	/** The ship has gone, the shelters are falling, and Halloran wants everyone on the band at once. */
	public static final int STAGE_CALL = 1;
	/** The call has happened. The mission board is live and the far side is the work. */
	public static final int STAGE_PLAN = 2;
	/** Everyone who can be reached has been. */
	public static final int STAGE_DONE = 3;

	/** One of the {@code STAGE_*} constants. */
	public int stage;
	/** The day the conference is due, in surface days, or -1 before the ship leaves. */
	public long callDay = -1L;
	/** The conference has played, or been skipped, which counts the same. */
	public boolean called;

	// ---------------------------------------------------------------- the bridge

	/** The narrows Halloran picked out of the survey: where the anchor wants to go. Null until asked for. */
	@Nullable
	public BlockPos crossing;
	/** Which way the deck goes from there, as a unit step: away from the pod, at the far side. */
	public int crossingStepX;
	public int crossingStepZ;
	/** The survey has been run. It costs seventy thousand noise samples and can legitimately find nothing. */
	public boolean crossingSearched;
	/** The anchor a span is growing from, and how far it has to go. Both null and zero until a kit is used. */
	@Nullable
	public BlockPos anchor;
	/**
	 * Which way the anchor was pointing when it was surveyed, as a unit step.
	 *
	 * <p>The anchor is a normal block: it mines, it drops itself, and it takes its bearing from whoever puts
	 * it down. So it can be replaced in the same hole facing the other way — easily, because the obvious
	 * place to stand while doing it is on the deck already laid — and without this the kit carries on laying
	 * courses on the new line, from the old offset, to the old length, and calls it finished.
	 */
	public int spanStepX;
	public int spanStepZ;
	/** Blocks of gap the survey measured off that anchor, and how many courses of deck are down. */
	public int spanLength;
	public int spanCourses;
	/** The deck reached the far lip. Nothing else in the mod cares which side of the Rift anything is on. */
	public boolean spanDone;

	// ---------------------------------------------------------------- the flow

	/** The middle of the lava channel across the approach to Ceramic Row, once it has been laid. */
	@Nullable
	public BlockPos flow;
	/** The retaining wall upstream of it: cut this and the channel starves. */
	@Nullable
	public BlockPos flowWall;
	/** Which way the channel runs, as a unit step. Kept so a reload crusts it in the same direction. */
	public int flowStepX;
	public int flowStepZ;
	public boolean flowCut;

	// ---------------------------------------------------------------- the people

	/** Clinic Nine's outer frame has its plates back and the door will cycle. */
	public boolean airlock;
	/** Novak has been picked up off the floor of the Rift at least once. */
	public boolean novakLifted;
	/** Halloran has already refused the run once, so she says the short version the second time. */
	public boolean novakRefused;

	public static RescueState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_rescue");
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("Stage", stage);
		nbt.putLong("CallDay", callDay);
		nbt.putBoolean("Called", called);
		putPos(nbt, "Crossing", crossing);
		nbt.putBoolean("CrossingSearched", crossingSearched);
		nbt.putInt("CrossingStepX", crossingStepX);
		nbt.putInt("CrossingStepZ", crossingStepZ);
		putPos(nbt, "Anchor", anchor);
		nbt.putInt("SpanStepX", spanStepX);
		nbt.putInt("SpanStepZ", spanStepZ);
		nbt.putInt("SpanLength", spanLength);
		nbt.putInt("SpanCourses", spanCourses);
		nbt.putBoolean("SpanDone", spanDone);
		putPos(nbt, "Flow", flow);
		putPos(nbt, "FlowWall", flowWall);
		nbt.putInt("FlowStepX", flowStepX);
		nbt.putInt("FlowStepZ", flowStepZ);
		nbt.putBoolean("FlowCut", flowCut);
		nbt.putBoolean("Airlock", airlock);
		nbt.putBoolean("NovakLifted", novakLifted);
		nbt.putBoolean("NovakRefused", novakRefused);
		return nbt;
	}

	private static RescueState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		RescueState state = new RescueState();
		state.stage = nbt.getInt("Stage");
		state.callDay = nbt.contains("CallDay") ? nbt.getLong("CallDay") : -1L;
		state.called = nbt.getBoolean("Called");
		state.crossing = readPos(nbt, "Crossing");
		state.crossingSearched = nbt.getBoolean("CrossingSearched");
		state.crossingStepX = nbt.getInt("CrossingStepX");
		state.crossingStepZ = nbt.getInt("CrossingStepZ");
		state.anchor = readPos(nbt, "Anchor");
		state.spanStepX = nbt.getInt("SpanStepX");
		state.spanStepZ = nbt.getInt("SpanStepZ");
		state.spanLength = nbt.getInt("SpanLength");
		state.spanCourses = nbt.getInt("SpanCourses");
		state.spanDone = nbt.getBoolean("SpanDone");
		state.flow = readPos(nbt, "Flow");
		state.flowWall = readPos(nbt, "FlowWall");
		state.flowStepX = nbt.getInt("FlowStepX");
		state.flowStepZ = nbt.getInt("FlowStepZ");
		state.flowCut = nbt.getBoolean("FlowCut");
		state.airlock = nbt.getBoolean("Airlock");
		state.novakLifted = nbt.getBoolean("NovakLifted");
		state.novakRefused = nbt.getBoolean("NovakRefused");
		return state;
	}

	private static void putPos(NbtCompound nbt, String key, @Nullable BlockPos pos) {
		if (pos == null) return;
		nbt.putInt(key + "X", pos.getX());
		nbt.putInt(key + "Y", pos.getY());
		nbt.putInt(key + "Z", pos.getZ());
	}

	@Nullable
	private static BlockPos readPos(NbtCompound nbt, String key) {
		if (!nbt.contains(key + "X")) return null;
		return new BlockPos(nbt.getInt(key + "X"), nbt.getInt(key + "Y"), nbt.getInt(key + "Z"));
	}
}
