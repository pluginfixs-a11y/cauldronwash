package cauldronwash.mixin;

import java.util.Map;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code CauldronInteraction.Dispatcher} держит карту «предмет → реакция» приватным полем, а методы
 * {@code put} пакетно-приватные. Достаём саму карту: ваниль её читает на каждый клик, поэтому
 * дописанные после bootstrap записи видны сразу — ни одного патча в логику котла.
 */
@Mixin(CauldronInteraction.Dispatcher.class)
public interface DispatcherAccessor {
	@Accessor("items")
	Map<Item, CauldronInteraction> cauldronwash$items();
}
