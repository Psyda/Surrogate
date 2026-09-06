package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A chassis in item form. Fresh from the crafting table or picked up with a wrench (keeps all its data). */
public class RobotChassisItem extends Item {
	public RobotChassisItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) return ActionResult.SUCCESS;
		if (dev.psyda.surrogate.crawler.CrawlerDimension.isCabin(world)) {
			// A chassis is not assembled in the cabin: the bay in the wall puts it outside beside the hull.
			if (context.getPlayer() != null) context.getPlayer().sendMessage(Text.translatable("message.surrogate.crawler.use_bay").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}
		BlockPos pos = context.getBlockPos();
		if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
			pos = pos.offset(context.getSide());
		}
		ItemStack stack = context.getStack();
		PlayerEntity player = context.getPlayer();
		RobotEntity robot = createRobot(serverWorld, stack);
		if (robot == null) return ActionResult.FAIL;

		float yaw = player != null ? player.getYaw() + 180f : 0f;
		robot.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		robot.setBodyYaw(yaw);
		robot.setHeadYaw(yaw);
		if (!serverWorld.isSpaceEmpty(robot)) {
			if (player != null) player.sendMessage(Text.translatable("message.surrogate.no_room").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		if (!serverWorld.spawnEntity(robot)) {
			if (player != null) player.sendMessage(Text.translatable("message.surrogate.already_deployed").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		RobotRegistry.get(serverWorld.getServer()).update(robot);
		serverWorld.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
		if (player == null || !player.getAbilities().creativeMode) stack.decrement(1);
		return ActionResult.CONSUME;
	}

	/** Builds (but does not spawn) the robot described by {@code stack}. */
	@Nullable
	public static RobotEntity createRobot(ServerWorld world, ItemStack stack) {
		RobotEntity robot = ModEntities.ROBOT.create(world);
		if (robot == null) return null;
		NbtCompound data = stack.get(ModComponents.ROBOT_DATA);
		if (data != null) {
			robot.readNbt(data.copy());
			robot.setPilot(null);
			robot.forceOffline();
			// A chassis in a player's hand is theirs, whatever the stored data claims. Belt and braces
			// against NBT that carries a scripted flag in from somewhere it should not have.
			robot.setScripted(false);
		} else {
			robot.setEnergy(Surrogate.CONFIG.newChassisEnergy);
		}
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		if (name != null) robot.setCustomName(name);
		return robot;
	}

	public static ItemStack toStack(RobotEntity robot) {
		ItemStack stack = new ItemStack(ModItems.ROBOT_CHASSIS);
		NbtCompound nbt = robot.writeNbt(new NbtCompound());
		nbt.remove("Passengers");
		nbt.remove("Pos");
		nbt.remove("Motion");
		nbt.remove("Pilot");
		stack.set(ModComponents.ROBOT_DATA, nbt);
		if (robot.hasCustomName()) stack.set(DataComponentTypes.CUSTOM_NAME, robot.getCustomName());
		return stack;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		NbtCompound data = stack.get(ModComponents.ROBOT_DATA);
		if (data == null) {
			tooltip.add(Text.translatable("tooltip.surrogate.chassis.new").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.surrogate.chassis.place").formatted(Formatting.DARK_GRAY));
			return;
		}
		int plating = data.getInt("Plating");
		int battery = data.getInt("Battery");
		float max = Surrogate.CONFIG.maxHealthForPlatingTier(plating);
		float health = data.contains("Health") ? data.getFloat("Health") : max;
		int capacity = Surrogate.CONFIG.capacityForBatteryTier(battery);
		int percent = capacity <= 0 ? 0 : data.getInt("Energy") * 100 / capacity;
		tooltip.add(Text.translatable("tooltip.surrogate.hull", format(health), format(max)).formatted(health < max * 0.34f ? Formatting.RED : Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.power", percent).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.surrogate.tiers", plating, battery).formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.cargo", Surrogate.CONFIG.cargoSlots(data.getInt("CargoTier"))).formatted(Formatting.DARK_GRAY));
		if (data.getBoolean("Fabricator")) tooltip.add(Text.translatable("tooltip.surrogate.fabricator_fitted").formatted(Formatting.DARK_GRAY));
	}

	private static String format(float value) {
		return value == (int) value ? Integer.toString((int) value) : String.format("%.1f", value);
	}
}
