package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.block.DartboardBlock;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A thrown dart. Sticks in a dartboard if it hits one on its face; anywhere else it falls to the floor as
 * the item it was, because a dart is a thing you can pick up again and the bar only has three.
 */
public class DartEntity extends ThrownItemEntity {
	public DartEntity(EntityType<? extends DartEntity> type, World world) {
		super(type, world);
	}

	public DartEntity(World world, LivingEntity thrower) {
		super(ModEntities.DART, thrower, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.DART;
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (getWorld().isClient) return;
		BlockPos pos = hit.getBlockPos();
		BlockState state = getWorld().getBlockState(pos);
		if (state.getBlock() instanceof DartboardBlock board && hit.getSide() == state.get(DartboardBlock.FACING)
				&& board.stick(getWorld(), pos, state)) {
			getWorld().playSound(null, pos, ModSounds.FLASHBACK_DART_HIT, SoundCategory.BLOCKS, 0.8f, 0.95f + random.nextFloat() * 0.1f);
			discard();
			return;
		}
		drop(hit.getPos());
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		if (getWorld().isClient) return;
		Entity target = hit.getEntity();
		Entity owner = getOwner();
		target.damage(getDamageSources().thrown(this, owner), 1.0f);
		drop(hit.getPos());
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (!getWorld().isClient && !isRemoved()) drop(hit.getPos());
	}

	/** Back on the floor as an item, with a small clatter. */
	private void drop(Vec3d at) {
		if (isRemoved()) return;
		ItemEntity item = new ItemEntity(getWorld(), at.x, at.y, at.z, new ItemStack(ModItems.DART));
		item.setPickupDelay(10);
		getWorld().spawnEntity(item);
		getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL, 0.4f, 1.6f);
		discard();
	}
}
