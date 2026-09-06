package dev.psyda.surrogate.pilot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player pilot body state, stored as a persistent Fabric attachment that survives death.
 */
public class PilotData {
	public static final Codec<PilotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("fatigue", 0).forGetter(data -> data.fatigue),
			Codec.INT.optionalFieldOf("unconscious", 0).forGetter(data -> data.unconsciousTicks),
			Codec.FLOAT.optionalFieldOf("toxin", 0f).forGetter(data -> data.toxin),
			Codec.BOOL.optionalFieldOf("welcomed", false).forGetter(data -> data.welcomed),
			Session.CODEC.optionalFieldOf("session").forGetter(data -> Optional.ofNullable(data.session))
	).apply(instance, PilotData::new));

	/** Ticks spent awake since the last full sleep. */
	public int fatigue;
	/** Ticks left of being passed out. */
	public int unconsciousTicks;
	/** How much of the outside air is in the body, 0 to 1. */
	public float toxin;
	/** Consecutive ticks the body has been breathing bad air; resets when it gets clean air. Not saved. */
	public int exposedTicks;
	/** Whether this player has been shown to the starter habitat. */
	public boolean welcomed;
	/** Time of day when the current sleep began. Not saved. */
	public long sleepStart;
	/** The active link, if the player is currently inside a chassis. */
	@Nullable
	public Session session;

	public PilotData() {
	}

	private PilotData(int fatigue, int unconsciousTicks, float toxin, boolean welcomed, Optional<Session> session) {
		this.fatigue = fatigue;
		this.unconsciousTicks = unconsciousTicks;
		this.toxin = toxin;
		this.welcomed = welcomed;
		this.session = session.orElse(null);
	}

	/** Everything needed to put the pilot back in their chair with their own inventory. */
	public static final class Session {
		public static final Codec<Session> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.CODEC.fieldOf("robot").forGetter(session -> session.robot),
				World.CODEC.fieldOf("chair_dimension").forGetter(session -> session.chairDimension),
				BlockPos.CODEC.fieldOf("chair_pos").forGetter(session -> session.chairPos),
				ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stash").forGetter(session -> session.stash)
		).apply(instance, Session::new));

		public final UUID robot;
		public final RegistryKey<World> chairDimension;
		public final BlockPos chairPos;
		/** The pilot's real inventory, parked while the robot's cargo occupies their slots. */
		public final List<ItemStack> stash;

		public Session(UUID robot, RegistryKey<World> chairDimension, BlockPos chairPos, List<ItemStack> stash) {
			this.robot = robot;
			this.chairDimension = chairDimension;
			this.chairPos = chairPos;
			this.stash = List.copyOf(stash);
		}
	}
}
