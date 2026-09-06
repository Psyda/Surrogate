package dev.psyda.surrogate.item;

import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Picks up the survivors and Site Two while it is in your pack. Use it for a bearing on every signal it can
 * hear: who, how strong, roughly how far, and which way. Sneak and use it to raise Halloran, who answers with
 * whatever is on her mind unless she is already on the air.
 */
public class FieldRadioItem extends Item {
	private static final int HALLORAN_LINES = 6;

	public FieldRadioItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (user instanceof ServerPlayerEntity player) {
			player.getItemCooldownManager().set(this, 20);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 0.3f, 0.5f);
			Director.radioUsed(player);
			if (player.isSneaking()) callSiteTwo(player);
			else SurvivorManager.get(player.server).report(player);
		}
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
	}

	/** Site Two answers, or the channel is busy because the story is using it. */
	private static void callSiteTwo(ServerPlayerEntity player) {
		HabitatState state = HabitatState.get(player.server);
		Text line;
		if (state.siteTwo == null) {
			line = Text.translatable("message.surrogate.radio.silent");
			player.sendMessage(line.copy().formatted(Formatting.GRAY), false);
			return;
		}
		if (Prologue.isRunning()) {
			line = Crew.HALLORAN.line("radio.later");
		} else {
			int index = 1 + (int) ((player.getWorld().getTime() / 600L + player.getId()) % HALLORAN_LINES);
			line = Crew.HALLORAN.line("radio." + index);
		}
		player.sendMessage(Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(Crew.HALLORAN.displayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(line.copy().formatted(Formatting.WHITE)), false);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.4f, 1.8f);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.radio").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.radio.use").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.radio.call").formatted(Formatting.DARK_GRAY));
	}
}
