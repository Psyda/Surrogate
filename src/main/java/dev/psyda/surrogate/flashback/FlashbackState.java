package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the player decided their last night on Earth was, and where to put them back afterwards.
 *
 * <p>The two answers are saved because act six weighs what the player did rather than what they were told,
 * and this is the only thing in the game they authored outright. Everything else on this record is bookkeeping
 * for one night: the bed to return to, and whether it has happened at all.
 */
public class FlashbackState extends PersistentState {
	private static final Type<FlashbackState> TYPE =
			new Type<>(FlashbackState::new, FlashbackState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** Whether there is anything left to dream. True once all three places have been walked into. */
	public boolean played;

	/**
	 * Which places have been visited, as a mask of {@link DreamPlace} ordinals.
	 *
	 * <p>The dream comes back every few nights until this is full. That is not generosity: a scene with three
	 * branches that a player sees one of is two thirds of a scene nobody will ever read, and the alternative
	 * — letting them pick again — would mean the first answer never meant anything. So the door they went
	 * through last time is dark, and what is left is what is left.
	 */
	public int visited;

	/** The night of the last dream, so the next one is a few nights off rather than the next time they lie down. */
	public long lastNight = -1L;

	/** Where they said they were this time, and why. Null and -1 between dreams. */
	@Nullable
	public DreamPlace place;
	public int reason = -1;

	/**
	 * What they have said about themselves, one answer per question asked, keyed by the question's own id.
	 *
	 * <p>This is the part that outlives the scene. Everything else here is one night's bookkeeping; this is a
	 * person, assembled a sentence at a time by somebody who has had four hundred days to think about it.
	 */
	public final Map<String, String> answers = new LinkedHashMap<>();

	/** How many things came home in the suitcase, for the log line and for the test. */
	public int packed;

	/**
	 * Everything the player was carrying, put away for the night.
	 *
	 * <p>It lives on the save rather than on the running scene on purpose: a server stopped in the middle of
	 * a dream must not be a server that ate an inventory. Written when they go under, given back when they
	 * wake, and cleared the moment it has been.
	 */
	@Nullable
	public NbtList stashed;

	/** The bed, and the world it is in, so waking up puts them back where they lay down. */
	@Nullable
	public BlockPos wakePos;
	public RegistryKey<World> wakeWorld = World.OVERWORLD;
	public float wakeYaw;

	public static FlashbackState get(MinecraftServer server) {
		ServerWorld world = server.getWorld(World.OVERWORLD);
		if (world == null) throw new IllegalStateException("no overworld");
		return world.getPersistentStateManager().getOrCreate(TYPE, Surrogate.MOD_ID + "_flashback");
	}

	public void remember(BlockPos pos, RegistryKey<World> world, float yaw) {
		this.wakePos = pos.toImmutable();
		this.wakeWorld = world;
		this.wakeYaw = yaw;
		markDirty();
	}

	public Vec3d wakeAt() {
		BlockPos at = wakePos == null ? BlockPos.ORIGIN : wakePos;
		return new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	public void answered(DreamPlace where, int why) {
		this.place = where;
		this.reason = why;
		markDirty();
	}

	public boolean hasVisited(DreamPlace where) {
		return (visited & (1 << where.ordinal())) != 0;
	}

	/** Whether anywhere is left. False means the arc is finished and there is nothing more to dream. */
	public boolean anythingLeft() {
		for (DreamPlace where : DreamPlace.all()) {
			if (!hasVisited(where)) return true;
		}
		return false;
	}

	/** One of the player's answers, or {@code fallback} if they were never asked. */
	public String answerTo(String question, String fallback) {
		return answers.getOrDefault(question, fallback);
	}

	public void answer(String question, String choice) {
		answers.put(question, choice);
		markDirty();
	}

	/** One night over. The arc is done when there is nowhere left to walk into. */
	public void finish(int packed, long night) {
		if (place != null) visited |= 1 << place.ordinal();
		this.packed = packed;
		this.lastNight = night;
		this.played = !anythingLeft();
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean("Played", played);
		nbt.putInt("Visited", visited);
		nbt.putLong("LastNight", lastNight);
		NbtCompound said = new NbtCompound();
		answers.forEach(said::putString);
		nbt.put("Answers", said);
		nbt.putInt("Place", place == null ? -1 : place.ordinal());
		nbt.putInt("Reason", reason);
		nbt.putInt("Packed", packed);
		if (wakePos != null) nbt.putLong("WakePos", wakePos.asLong());
		nbt.putString("WakeWorld", wakeWorld.getValue().toString());
		nbt.putFloat("WakeYaw", wakeYaw);
		if (stashed != null) nbt.put("Stashed", stashed);
		return nbt;
	}

	private static FlashbackState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		FlashbackState state = new FlashbackState();
		state.played = nbt.getBoolean("Played");
		state.visited = nbt.getInt("Visited");
		state.lastNight = nbt.contains("LastNight") ? nbt.getLong("LastNight") : -1L;
		NbtCompound said = nbt.getCompound("Answers");
		for (String key : said.getKeys()) state.answers.put(key, said.getString(key));
		state.place = DreamPlace.byId(nbt.contains("Place") ? nbt.getInt("Place") : -1);
		state.reason = nbt.contains("Reason") ? nbt.getInt("Reason") : -1;
		state.packed = nbt.getInt("Packed");
		if (nbt.contains("WakePos")) state.wakePos = BlockPos.fromLong(nbt.getLong("WakePos"));
		if (nbt.contains("WakeWorld")) {
			Identifier id = Identifier.tryParse(nbt.getString("WakeWorld"));
			if (id != null) state.wakeWorld = RegistryKey.of(RegistryKeys.WORLD, id);
		}
		state.wakeYaw = nbt.getFloat("WakeYaw");
		if (nbt.contains("Stashed", NbtElement.LIST_TYPE)) state.stashed = nbt.getList("Stashed", NbtElement.COMPOUND_TYPE);
		return state;
	}
}
