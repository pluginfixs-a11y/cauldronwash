package cauldronwash;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Одна реакция на все предметы из {@link WashTable}.
 *
 * <p>Стирается **вся пачка за один клик** (64 шерсти → 64 белой), вода убывает на один уровень за клик.
 * Остальные компоненты предмета (имя, содержимое сумки, прочность) переезжают на новый предмет через
 * {@link ItemStack#transmuteCopy}.
 *
 * <p>Возвращаем {@link InteractionResult#SUCCESS}, а не PASS: сервер на SUCCESS не пытается поставить
 * блок из руки (см. {@code ServerPlayerGameMode#useItemOn} — при {@code consumesAction()} он выходит
 * сразу). Блок, который видно на миг, ставит клиент своей предсказательной логикой и тут же откатывает
 * по ответу сервера.
 */
public final class WashInteraction implements CauldronInteraction {
	public static final WashInteraction INSTANCE = new WashInteraction();

	private WashInteraction() {
	}

	@Override
	public InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			ItemStack stack) {
		Item target = WashTable.targetOf(stack);
		if (target == null || stack.isEmpty()) {
			// предмет в диспетчер попал, а в таблице его нет — не мешаем ванили
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		player.setItemInHand(hand, stack.transmuteCopy(target, stack.getCount()));
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		return InteractionResult.SUCCESS;
	}
}
