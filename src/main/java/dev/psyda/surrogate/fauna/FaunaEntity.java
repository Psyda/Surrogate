package dev.psyda.surrogate.fauna;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * What lives on Sallow. Four small animals, none of which is a threat in the way a zombie is a threat, and
 * all of which exist so the wastes are somewhere rather than nowhere.
 *
 * <p>Shared here: the things Ferreira's survey and the endgame manifest need of all of them. Each one knows
 * which {@link Specimen} it is, whether it has already been read by a bio-sampler (so a second reading of
 * the same animal is a shrug rather than a duplicate), and how to be bagged. Everything else — how it moves,
 * what wakes it, what it does when it is unhappy — belongs to the subclass, because that is the whole of
 * what makes one of these different from another.
 *
 * <p>None of them are {@code HostileEntity}, which is what keeps {@link dev.psyda.surrogate.hazard.NoMonsters}
 * from sweeping them out of the world half a second after they spawn.
 */
public abstract class FaunaEntity extends PathAwareEntity {
	/** Set once a sampler has read this individual, so a second pass on the same animal says so. */
	private static final TrackedData<Boolean> READ = DataTracker.registerData(FaunaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	protected FaunaEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	/** Which row of Ferreira's table this is. */
	public abstract Specimen specimen();

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(READ, false);
	}

	public boolean wasRead() {
		return this.dataTracker.get(READ);
	}

	public void setRead(boolean read) {
		this.dataTracker.set(READ, read);
	}

	/**
	 * Whether a bagging attachment can pick this one up right now. The default is yes for anything alive and
	 * unhurt; a slagback that is awake and swinging says no, and says why, in its own override.
	 */
	public boolean canBeBagged() {
		return isAlive();
	}

	/** Why not, when {@link #canBeBagged()} is false. A translation key, or null when it is baggable. */
	@Nullable
	public String bagRefusal() {
		return null;
	}

	/**
	 * Sallow's animals are native to it. The toxin, the storms and the acid are all somebody else's problem,
	 * and drowning in the seep is not a thing that happens to something that grew up in it.
	 */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.isOf(dev.psyda.surrogate.atmosphere.ModDamageTypes.TOXIN)) return true;
		if (source.isOf(dev.psyda.surrogate.atmosphere.ModDamageTypes.ACID)) return true;
		if (source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN)) return true;
		return super.isInvulnerableTo(source);
	}

	/**
	 * Nothing here despawns. There are few enough of them that a player who walks back to where they saw one
	 * should find it, and a survey that erased its own subjects between visits would be unplayable.
	 */
	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	protected void voice(@Nullable SoundEvent sound, float pitchSpread) {
		if (sound == null) return;
		getWorld().playSound(null, getX(), getY(), getZ(), sound, SoundCategory.NEUTRAL,
				0.7f, 1.0f + (this.random.nextFloat() - 0.5f) * pitchSpread);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Read", wasRead());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setRead(nbt.getBoolean("Read"));
	}
}
