package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The crawler in a crate. Put it in a vehicle fabricator and the gantry builds the hull in front of it; used
 * on level ground it is assembled there by hand, facing the way you face.
 */
public class CrawlerKitItem extends Item implements VehicleKit {
	/** Ten seconds under the gantry. */
	public static final int BUILD_TICKS = 200;

	public CrawlerKitItem(Settings settings) {
		super(settings);
	}

	@Override
	public Box footprint(Vec3d at) {
		return new Box(at.x - CrawlerEntity.WIDTH / 2.0, at.y, at.z - CrawlerEntity.WIDTH / 2.0,
				at.x + CrawlerEntity.WIDTH / 2.0, at.y + CrawlerEntity.HEIGHT, at.z + CrawlerEntity.WIDTH / 2.0);
	}

	@Override
	public int buildTicks() {
		return BUILD_TICKS;
	}

	@Override
	@Nullable
	public Entity assemble(ServerWorld world, Vec3d at, float yaw) {
		if (!world.isSpaceEmpty(null, footprint(at))) return null;
		CrawlerEntity crawler = new CrawlerEntity(ModEntities.CRAWLER, world);
		crawler.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		crawler.setEnergy(Surrogate.CONFIG.crawlerEnergyCapacity / 4);
		world.spawnEntity(crawler);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 1.0f, 0.6f);
		return crawler;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		BlockPos ground = context.getBlockPos();
		Vec3d at = new Vec3d(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
		float yaw = player == null ? 0f : player.getYaw();
		if (world.isClient) return ActionResult.SUCCESS;
		if (assemble((ServerWorld) world, at, yaw) == null) {
			if (player != null) player.sendMessage(Text.translatable("message.surrogate.crawler.no_room").formatted(Formatting.GRAY), true);
			return ActionResult.FAIL;
		}
		ItemStack stack = context.getStack();
		if (player == null || !player.isCreative()) stack.decrement(1);
		return ActionResult.CONSUME;
	}
}
