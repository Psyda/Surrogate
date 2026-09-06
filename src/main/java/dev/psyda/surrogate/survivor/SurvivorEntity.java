package dev.psyda.surrogate.survivor;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Someone who was out here before you, holed up in a shelter, talking on the radio. Bring what they ask for
 * and they pay you back with a chassis upgrade and better company on the airwaves.
 */
public class SurvivorEntity extends PathAwareEntity {
	private static final TrackedData<Integer> CHARACTER = DataTracker.registerData(SurvivorEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> RESCUED = DataTracker.registerData(SurvivorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private int chatterCooldown;

	public SurvivorEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createSurvivorAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CHARACTER, 0);
		builder.add(RESCUED, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f, 1.0f));
		this.goalSelector.add(2, new LookAroundGoal(this));
	}

	public Survivor getCharacter() {
		return Survivor.byId(this.dataTracker.get(CHARACTER));
	}

	public void setCharacter(Survivor survivor) {
		this.dataTracker.set(CHARACTER, survivor.ordinal());
		setCustomName(survivor.displayName());
		setCustomNameVisible(true);
	}

	public boolean isRescued() {
		return this.dataTracker.get(RESCUED);
	}

	public void setRescued(boolean rescued) {
		this.dataTracker.set(RESCUED, rescued);
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		Survivor who = getCharacter();
		ItemStack stack = player.getStackInHand(hand);
		if (isRescued()) {
			say(serverPlayer, who.line(this.random.nextBoolean() ? "idle.1" : "idle.2"));
			return ActionResult.CONSUME;
		}
		if (stack.isOf(who.need()) && stack.getCount() >= who.needCount()) {
			if (!player.getAbilities().creativeMode) stack.decrement(who.needCount());
			setRescued(true);
			ItemStack reward = who.reward();
			if (!player.giveItemStack(reward)) player.dropItem(reward, false);
			say(serverPlayer, who.line("thanks"));
			player.sendMessage(Text.translatable("message.surrogate.survivor.reward", who.displayName(), who.reward().getName()).formatted(Formatting.GOLD), false);
			getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);
			getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.NEUTRAL, 0.8f, 1.4f);
			if (getServer() != null) SurvivorManager.get(getServer()).onHelped(getServer(), who);
			return ActionResult.CONSUME;
		}
		say(serverPlayer, Text.translatable("survivor.surrogate." + who.key() + ".plea", who.needCount(), new ItemStack(who.need()).getName()));
		return ActionResult.CONSUME;
	}

	private void say(ServerPlayerEntity player, Text line) {
		player.sendMessage(Text.empty().append(getCharacter().displayName().copy().formatted(Formatting.GOLD)).append(": ").append(line), false);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		// A little life in the shelter: an unrescued survivor waves the nearest player over now and then.
		if (--chatterCooldown <= 0) {
			chatterCooldown = 400 + this.random.nextInt(400);
			if (!isRescued()) {
				PlayerEntity nearest = getWorld().getClosestPlayer(this, 12.0);
				if (nearest instanceof ServerPlayerEntity player) {
					say(player, getCharacter().line("greet"));
					swingHand(Hand.MAIN_HAND);
				}
			}
		}
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Character", this.dataTracker.get(CHARACTER));
		nbt.putBoolean("Rescued", isRescued());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(CHARACTER, nbt.getInt("Character"));
		this.dataTracker.set(RESCUED, nbt.getBoolean("Rescued"));
	}
}
