package dev.psyda.surrogate.pilot;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.item.RobotTools;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatBuilder;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.world.GameRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.network.PilotStatusPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side brain of the mod: links pilots to chassis, brings them home, tracks fatigue, and redirects
 * damage from the (hidden) pilot entity to the chassis it is riding.
 *
 * <p>While linked, the player's own inventory is stashed on their pilot data and the robot's cargo is loaded
 * into their inventory slots. That way mining, placing, and every other vanilla interaction just works, and
 * whatever the robot "had" is exactly what goes into the wreck when it dies.
 */
public final class PilotManager {
	public static final AttachmentType<PilotData> PILOT_DATA = AttachmentRegistry.create(Surrogate.id("pilot"),
			builder -> builder.persistent(PilotData.CODEC).copyOnDeath().initializer(PilotData::new));

	private static final ChunkTicketType<BlockPos> LINK_TICKET = ChunkTicketType.create("surrogate_link", Vec3i::compareTo, 200);
	private static final Map<UUID, PendingDive> PENDING = new HashMap<>();

	private static final class PendingDive {
		final UUID robot;
		final RegistryKey<World> chairDimension;
		final BlockPos chairPos;
		int ticksLeft;

		PendingDive(UUID robot, RegistryKey<World> chairDimension, BlockPos chairPos, int ticksLeft) {
			this.robot = robot;
			this.chairDimension = chairDimension;
			this.chairPos = chairPos;
			this.ticksLeft = ticksLeft;
		}
	}

	private PilotManager() {
	}

	public static PilotData data(ServerPlayerEntity player) {
		return player.getAttachedOrCreate(PILOT_DATA);
	}

	public static boolean isPiloting(ServerPlayerEntity player) {
		return data(player).session != null;
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(PilotManager::tickServer);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onLogin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register(PilotManager::onRespawn);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PilotManager::onDamage);
		EntitySleepEvents.STOP_SLEEPING.register(PilotManager::onStopSleeping);
		UseItemCallback.EVENT.register(PilotManager::onUseItem);
		AttackEntityCallback.EVENT.register(PilotManager::onAttack);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Atmosphere.clear());
		// Sleep whenever you like; the nap logic in onStopSleeping decides how much time passes.
		EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) -> ActionResult.SUCCESS);
		EntitySleepEvents.START_SLEEPING.register(PilotManager::onStartSleeping);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PilotManager::onAllowDeath);
	}

	// ------------------------------------------------------------------ ticking

	private static void tickServer(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tickPlayer(player);
		}
		tickPending(server);
		SurvivorManager.tick(server);
	}

	private static void tickPlayer(ServerPlayerEntity player) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		PilotData data = data(player);
		boolean exempt = player.isCreative() || player.isSpectator();
		boolean changed = false;

		if (data.unconsciousTicks > 0) {
			data.unconsciousTicks--;
			applyUnconsciousEffects(player);
			if (data.unconsciousTicks == 0) {
				data.fatigue = (int) (cfg.maxFatigueTicks * cfg.wakeFatigueFraction);
				player.sendMessage(Text.translatable("message.surrogate.came_to"), false);
				changed = true;
			}
		} else if (!exempt && player.isAlive() && !player.isSleeping()) {
			data.fatigue++;
			int max = cfg.maxFatigueTicks;
			if (data.fatigue == (int) (max * 0.75)) {
				player.sendMessage(Text.translatable("message.surrogate.tired").formatted(Formatting.YELLOW), false);
			} else if (data.fatigue == (int) (max * 0.9)) {
				player.sendMessage(Text.translatable("message.surrogate.exhausted").formatted(Formatting.RED), false);
			}
			if (data.fatigue >= max) {
				passOut(player, data);
				changed = true;
			}
		}

		PilotData.Session session = data.session;
		if (session != null && player.isAlive()) {
			if (player.getVehicle() instanceof RobotEntity robot && robot.getUuid().equals(session.robot)) {
				player.getHungerManager().addExhaustion(cfg.pilotExhaustionPerTick);
				enforceNoArmor(player);
				if (player.age % 20 == 0) applyCargoLocks(player, robot);
			} else {
				disconnect(player, false, Text.translatable("message.surrogate.link_lost"), true);
			}
		}

		// The body breathes wherever it is: in the chair while linked, or wherever the player stands.
		Exposure.tick(player, data);

		if (changed || player.age % 20 == 0) sendStatus(player, data);
	}

	/** Armor is a body thing. The chassis has plating instead. */
	private static void enforceNoArmor(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 36; slot < 40; slot++) {
			ItemStack armor = inventory.getStack(slot);
			if (armor.isEmpty()) continue;
			inventory.setStack(slot, ItemStack.EMPTY);
			if (!inventory.insertStack(armor)) player.dropItem(armor, false);
		}
	}

	private static void passOut(ServerPlayerEntity player, PilotData data) {
		if (data.session != null) {
			disconnect(player, false, null, false);
		}
		data.unconsciousTicks = Surrogate.CONFIG.unconsciousTicks;
		applyUnconsciousEffects(player);
		sendTitle(player, Text.translatable("title.surrogate.passed_out"), Text.translatable("title.surrogate.passed_out.sub"));
	}

	private static void applyUnconsciousEffects(ServerPlayerEntity player) {
		int duration = 60;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, duration, 0, false, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 6, false, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, duration, 3, false, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, 3, false, false, false));
	}

	private static void sendTitle(ServerPlayerEntity player, Text title, @Nullable Text subtitle) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
		player.networkHandler.sendPacket(new TitleS2CPacket(title));
		if (subtitle != null) player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
	}

	public static void sendStatus(ServerPlayerEntity player, PilotData data) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		Exposure.Report air = Exposure.inspect(Exposure.bodyWorld(player, data), Exposure.bodyPos(player, data));
		ServerPlayNetworking.send(player, new PilotStatusPayload(data.fatigue, cfg.maxFatigueTicks, data.unconsciousTicks,
				cfg.bootTicks, cfg.shutdownTicks, cfg.idleDrainPerTick,
				Math.round(data.toxin * 1000f), air.state(), Exposure.percent(air.quality())));
	}

	private static void fail(ServerPlayerEntity player, String key) {
		Surrogate.LOGGER.info("Dive refused for {}: {}", player.getName().getString(), key);
		player.sendMessage(Text.translatable("message.surrogate." + key).formatted(Formatting.RED), true);
	}

	// ------------------------------------------------------------------ diving

	/** Called when a player uses a dive chair. Finds the linked chassis (loading its chunk if needed) and links. */
	public static void requestDive(ServerPlayerEntity player, DiveChairBlockEntity chair) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		PilotData data = data(player);
		if (data.session != null) {
			fail(player, "already_linked");
			return;
		}
		if (data.unconsciousTicks > 0) return;
		if (data.fatigue >= cfg.maxFatigueTicks * 0.98) {
			fail(player, "too_tired");
			return;
		}
		UUID robot = chair.getLinkedRobot();
		if (robot == null) {
			fail(player, "chair_unlinked");
			return;
		}
		UUID occupant = chair.getOccupant();
		if (occupant != null && !occupant.equals(player.getUuid())) {
			fail(player, "chair_occupied");
			return;
		}
		if (PENDING.containsKey(player.getUuid())) return;
		if (chair.getWorld() == null) return;

		Optional<RobotRegistry.Record> record = RobotRegistry.get(player.server).get(robot);
		if (record.isEmpty()) {
			fail(player, "unknown_chassis");
			return;
		}
		if (record.get().wrecked()) {
			fail(player, "chassis_wrecked");
			return;
		}
		if (player.hasVehicle()) player.stopRiding();

		PendingDive pending = new PendingDive(robot, chair.getWorld().getRegistryKey(), chair.getPos(), cfg.linkSearchTicks);
		player.sendMessage(Text.translatable("message.surrogate.linking"), true);
		if (!tryResolvePending(player, pending)) {
			PENDING.put(player.getUuid(), pending);
		}
	}

	private static void tickPending(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		Iterator<Map.Entry<UUID, PendingDive>> iterator = PENDING.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PendingDive> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			PendingDive pending = entry.getValue();
			if (player == null) {
				iterator.remove();
				continue;
			}
			if (tryResolvePending(player, pending)) {
				iterator.remove();
				continue;
			}
			if (--pending.ticksLeft <= 0) {
				iterator.remove();
				fail(player, "no_response");
			}
		}
	}

	/** @return true when the attempt is finished, either linked or failed for good; false to keep waiting */
	private static boolean tryResolvePending(ServerPlayerEntity player, PendingDive pending) {
		MinecraftServer server = player.server;
		RobotRegistry registry = RobotRegistry.get(server);
		RobotRegistry.Record record = registry.get(pending.robot).orElse(null);
		if (record == null || record.wrecked()) {
			fail(player, record == null ? "unknown_chassis" : "chassis_wrecked");
			return true;
		}
		ServerWorld world = server.getWorld(record.dimension());
		if (world == null) {
			fail(player, "unknown_chassis");
			return true;
		}

		// Entities only load in ticking chunks, so a plain chunk load is not enough: hold a ticking ticket.
		ChunkPos chunkPos = new ChunkPos(record.pos());
		world.getChunkManager().addTicket(LINK_TICKET, chunkPos, 2, record.pos());
		world.getChunk(chunkPos.x, chunkPos.z);

		RobotEntity robot = null;
		if (record.docked()) {
			if (world.getBlockEntity(record.pos()) instanceof ChargingDockBlockEntity dock) {
				if (!pending.robot.equals(dock.getRobotUuid())) {
					fail(player, "unknown_chassis");
					return true;
				}
				robot = dock.undock();
				if (robot == null) {
					fail(player, "dock_blocked");
					return true;
				}
			} else {
				fail(player, "unknown_chassis");
				return true;
			}
		} else if (world.getEntity(pending.robot) instanceof RobotEntity found) {
			robot = found;
		}
		if (robot == null) return false;

		// The backstop for every route into a body: a chair keyed to an NPC chassis before these guards
		// existed, or one edited in by hand, still cannot dive into it.
		if (!robot.isClaimable()) {
			fail(player, "chassis_not_yours");
			return true;
		}
		if (robot.isPiloted() && !robot.isPilot(player)) {
			fail(player, "chassis_busy");
			return true;
		}
		if (robot.getState() == RobotState.SHUTTING_DOWN) {
			fail(player, "chassis_shutting_down");
			return true;
		}
		if (robot.getState() == RobotState.OFFLINE && !robot.canBoot()) {
			fail(player, "chassis_no_power");
			return true;
		}
		beginSession(player, robot, pending.chairDimension, pending.chairPos);
		return true;
	}

	private static void beginSession(ServerPlayerEntity player, RobotEntity robot, RegistryKey<World> chairDimension, BlockPos chairPos) {
		MinecraftServer server = player.server;
		PilotData data = data(player);

		// Swap inventories: the pilot's body keeps its things in the chair, the robot's cargo goes on the hotbar.
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> stash = new ArrayList<>(RobotEntity.CARGO_SIZE);
		for (int i = 0; i < RobotEntity.CARGO_SIZE; i++) {
			stash.add(inventory.getStack(i).copy());
			inventory.setStack(i, robot.getCargo(i).copy());
		}
		robot.clearCargo();
		data.session = new PilotData.Session(robot.getUuid(), chairDimension, chairPos, stash);

		ServerWorld chairWorld = server.getWorld(chairDimension);
		if (chairWorld != null && chairWorld.getBlockEntity(chairPos) instanceof DiveChairBlockEntity chair) {
			chair.setOccupant(player.getUuid(), player.getGameProfile().getName());
		}

		robot.setPilot(player.getUuid());
		ServerWorld robotWorld = (ServerWorld) robot.getWorld();
		player.teleport(robotWorld, robot.getX(), robot.getY(), robot.getZ(), robot.getYaw(), 0.0f);
		if (!player.startRiding(robot, true)) {
			Surrogate.LOGGER.warn("{} could not mount chassis {}", player.getName().getString(), robot.getUuid());
			disconnect(player, false, Text.translatable("message.surrogate.link_failed"), true);
			return;
		}
		player.calculateDimensions();
		applyCargoLocks(player, robot);
		if (robot.getState() == RobotState.OFFLINE) robot.beginBoot();
		RobotRegistry.get(server).update(robot);
		player.sendMessage(Text.translatable(robot.getState() == RobotState.ONLINE
				? "message.surrogate.link_established" : "message.surrogate.booting"), true);
		sendStatus(player, data);
	}

	/**
	 * Ends the link: cargo goes back into the chassis, the pilot's own inventory is restored, and the player
	 * is put back next to their chair. The chassis keeps running unless {@code shutdown} is set.
	 */
	public static void disconnect(ServerPlayerEntity player, boolean shutdown, @Nullable Text message, boolean alarm) {
		PilotData data = data(player);
		PilotData.Session session = data.session;
		if (session == null) return;
		MinecraftServer server = player.server;
		data.session = null;

		RobotEntity robot = player.getVehicle() instanceof RobotEntity riding && riding.getUuid().equals(session.robot)
				? riding : findLoadedRobot(server, session.robot);

		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < RobotEntity.CARGO_SIZE; i++) {
			ItemStack stack = inventory.getStack(i);
			if (isLock(stack)) stack = ItemStack.EMPTY;
			if (robot != null) {
				robot.setCargo(i, stack.copy());
			} else if (!stack.isEmpty()) {
				player.dropItem(stack.copy(), false);
			}
			inventory.setStack(i, i < session.stash.size() ? session.stash.get(i).copy() : ItemStack.EMPTY);
		}
		if (robot != null) {
			if (robot.isPilot(player)) robot.setPilot(null);
			if (shutdown) robot.beginShutdown();
			RobotRegistry.get(server).update(robot);
		}

		player.stopRiding();
		ServerWorld chairWorld = server.getWorld(session.chairDimension);
		if (chairWorld != null) {
			chairWorld.getChunk(session.chairPos.getX() >> 4, session.chairPos.getZ() >> 4);
			Vec3d exit = exitPosition(chairWorld, session.chairPos);
			player.teleport(chairWorld, exit.x, exit.y, exit.z, exitYaw(chairWorld, session.chairPos), 0.0f);
			if (chairWorld.getBlockEntity(session.chairPos) instanceof DiveChairBlockEntity chair) {
				chair.clearOccupant(player.getUuid());
			}
		}
		player.calculateDimensions();

		if (message != null) {
			if (alarm) sendTitle(player, message, null);
			player.sendMessage(message, !alarm);
		}
		sendStatus(player, data);
	}

	/** Empties the pilot's slots (the robot's cargo) and returns the contents. */
	public static List<ItemStack> takeCargoFromPilot(ServerPlayerEntity pilot) {
		PlayerInventory inventory = pilot.getInventory();
		List<ItemStack> cargo = new ArrayList<>(RobotEntity.CARGO_SIZE);
		for (int i = 0; i < RobotEntity.CARGO_SIZE; i++) {
			ItemStack stack = inventory.getStack(i);
			cargo.add(isLock(stack) ? ItemStack.EMPTY : stack.copy());
			inventory.setStack(i, ItemStack.EMPTY);
		}
		return cargo;
	}

	// ------------------------------------------------------------------ cargo bays and the fabricator

	static boolean isLock(ItemStack stack) {
		return stack.isOf(ModItems.LOCKED_BAY);
	}

	/**
	 * Fills every main inventory slot the chassis has no cargo bay for with a sealed-bay placeholder, and
	 * frees any slot a newly fitted bay has opened. Runs on link and every second after.
	 */
	public static void applyCargoLocks(ServerPlayerEntity player, RobotEntity robot) {
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			boolean open = robot.isCargoSlotOpen(i);
			ItemStack stack = inventory.getStack(i);
			if (!open && stack.isEmpty()) inventory.setStack(i, new ItemStack(ModItems.LOCKED_BAY));
			else if (open && isLock(stack)) inventory.setStack(i, ItemStack.EMPTY);
		}
	}

	private static void stripLocks(PlayerInventory inventory) {
		for (int i = 0; i < inventory.size(); i++) {
			if (isLock(inventory.getStack(i))) inventory.setStack(i, ItemStack.EMPTY);
		}
	}

	/** Placeholders must never hit the ground as drops. */
	private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayerEntity player) stripLocks(player.getInventory());
		return true;
	}

	public static void openFabricator(ServerPlayerEntity player) {
		RobotEntity robot = RobotTools.chassisOf(player);
		if (robot == null) return;
		if (!robot.hasFabricator()) {
			player.sendMessage(Text.translatable("message.surrogate.no_fabricator").formatted(Formatting.RED), true);
			return;
		}
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, p) -> new RobotCraftingScreenHandler(syncId, inventory, robot),
				Text.translatable("screen.surrogate.fabricator")));
	}

	@Nullable
	public static RobotEntity findLoadedRobot(MinecraftServer server, UUID uuid) {
		for (ServerWorld world : server.getWorlds()) {
			if (world.getEntity(uuid) instanceof RobotEntity robot) return robot;
		}
		return null;
	}

	private static Vec3d exitPosition(ServerWorld world, BlockPos chairPos) {
		BlockState state = world.getBlockState(chairPos);
		List<BlockPos> candidates = new ArrayList<>();
		if (state.getBlock() instanceof DiveChairBlock) candidates.add(chairPos.offset(state.get(DiveChairBlock.FACING)));
		for (Direction direction : Direction.Type.HORIZONTAL) candidates.add(chairPos.offset(direction));
		for (BlockPos candidate : candidates) {
			if (isStandable(world, candidate)) return Vec3d.ofBottomCenter(candidate);
		}
		return Vec3d.ofBottomCenter(chairPos).add(0.0, 0.45, 0.0);
	}

	private static float exitYaw(ServerWorld world, BlockPos chairPos) {
		BlockState state = world.getBlockState(chairPos);
		return state.getBlock() instanceof DiveChairBlock ? state.get(DiveChairBlock.FACING).getOpposite().asRotation() : 0.0f;
	}

	private static boolean isStandable(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
				&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
				&& !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
	}

	// ------------------------------------------------------------------ connection and death events

	private static void onLogin(ServerPlayerEntity player) {
		PilotData data = data(player);
		PilotData.Session session = data.session;
		if (session != null) {
			if (player.getVehicle() instanceof RobotEntity robot && robot.getUuid().equals(session.robot)) {
				// The server went down mid-dive and vanilla restored the vehicle with the player: resume.
				robot.setPilot(player.getUuid());
				ServerWorld chairWorld = player.server.getWorld(session.chairDimension);
				if (chairWorld != null && chairWorld.getBlockEntity(session.chairPos) instanceof DiveChairBlockEntity chair) {
					chair.setOccupant(player.getUuid(), player.getGameProfile().getName());
				}
				player.calculateDimensions();
			} else {
				disconnect(player, false, Text.translatable("message.surrogate.link_lost"), true);
			}
		}
		HabitatBuilder.onJoin(player);
		sendStatus(player, data);
	}

	private static void onLogout(ServerPlayerEntity player) {
		PENDING.remove(player.getUuid());
		if (data(player).session != null) {
			disconnect(player, false, null, false);
		}
	}

	private static void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
		PilotData data = data(newPlayer);
		PilotData.Session session = data.session;
		if (!alive) {
			// The toxin died with the old body; carrying it over killed the new one on the spot.
			data.toxin = 0f;
			data.exposedTicks = 0;
			data.unconsciousTicks = 0;
		}
		if (session == null || alive) {
			sendStatus(newPlayer, data);
			return;
		}
		data.session = null;
		MinecraftServer server = newPlayer.server;
		RobotEntity robot = findLoadedRobot(server, session.robot);

		// With keepInventory the cargo is still on the new player; hand it back to the chassis.
		PlayerInventory inventory = newPlayer.getInventory();
		for (int i = 0; i < RobotEntity.CARGO_SIZE; i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && !isLock(stack)) {
				if (robot != null) robot.setCargo(i, stack.copy());
				else newPlayer.dropItem(stack.copy(), false);
			}
			inventory.setStack(i, i < session.stash.size() ? session.stash.get(i).copy() : ItemStack.EMPTY);
		}
		if (robot != null && robot.isPilot(newPlayer)) robot.setPilot(null);

		ServerWorld chairWorld = server.getWorld(session.chairDimension);
		if (chairWorld != null && chairWorld.getBlockEntity(session.chairPos) instanceof DiveChairBlockEntity chair) {
			chair.clearOccupant(newPlayer.getUuid());
		}
		newPlayer.sendMessage(Text.translatable("message.surrogate.link_severed").formatted(Formatting.RED), false);
		sendStatus(newPlayer, data);
	}

	/**
	 * The pilot entity rides inside the chassis and is what mobs, arrows and other players actually hit.
	 * Forward those hits to the chassis. Environmental damage already hits the chassis directly, so it is
	 * simply swallowed here rather than applied twice.
	 */
	private static boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity player)) return true;
		if (!(player.getVehicle() instanceof RobotEntity robot) || !robot.isPilot(player)) return true;
		// The body starves, or chokes, in its chair like anyone else; kill commands and the void also reach it directly.
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.STARVE)
				|| source.isOf(ModDamageTypes.TOXIN)) return true;
		if (source.getSource() != null && !source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			robot.damage(source, amount);
		}
		return false;
	}

	private static void onStartSleeping(LivingEntity entity, BlockPos sleepingPos) {
		if (entity instanceof ServerPlayerEntity player) {
			data(player).sleepStart = player.getServerWorld().getTimeOfDay();
		}
	}

	/**
	 * Sleep is allowed at any hour. Vanilla still only lets the world skip ahead when enough players are in
	 * bed, and it always jumps to the next dawn; for a daytime nap that is too much, so the clock is pulled
	 * back to a nap length that grows with fatigue, never past dusk. A night's sleep still ends at dawn.
	 */
	private static void onStopSleeping(LivingEntity entity, BlockPos sleepingPos) {
		if (!(entity instanceof ServerPlayerEntity player) || player.getSleepTimer() < 100) return;
		SurrogateConfig cfg = Surrogate.CONFIG;
		PilotData data = data(player);
		ServerWorld world = player.getServerWorld();
		long start = data.sleepStart;
		long now = world.getTimeOfDay();
		boolean startedInDaylight = start % 24000L < 12000L;
		boolean worldSkipped = now > start && now % 24000L == 0L;
		if (startedInDaylight && worldSkipped && world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
			float tired = MathHelper.clamp((float) data.fatigue / Math.max(1, cfg.maxFatigueTicks), 0f, 1f);
			long nap = cfg.napMinTicks + (long) (cfg.napFatigueTicks * tired);
			long dusk = start - start % 24000L + 12000L;
			world.setTimeOfDay(Math.min(start + nap, dusk));
		}
		data.fatigue = 0;
		data.unconsciousTicks = 0;
		player.sendMessage(Text.translatable("message.surrogate.rested").formatted(Formatting.GREEN), false);
		sendStatus(player, data);
	}

	private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (RobotEntity.isPiloting(player) && isConsumable(stack)) {
			if (!world.isClient) player.sendMessage(Text.translatable("message.surrogate.cannot_eat"), true);
			return TypedActionResult.fail(stack);
		}
		return TypedActionResult.pass(stack);
	}

	/** The arc cutter only swings when it is coupled to a running chassis with charge to spare. */
	private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target, @Nullable EntityHitResult hit) {
		if (player.getStackInHand(hand).isOf(ModItems.ARC_CUTTER) && !RobotTools.check(player, Surrogate.CONFIG.cutterEnergyPerHit)) {
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static boolean isConsumable(ItemStack stack) {
		return stack.get(DataComponentTypes.FOOD) != null
				|| stack.getItem() instanceof PotionItem
				|| stack.getItem() instanceof MilkBucketItem;
	}
}
