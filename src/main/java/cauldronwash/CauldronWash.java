package cauldronwash;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cauldronwash.mixin.DispatcherAccessor;

/**
 * Водяной котёл снимает краситель с любой цветной вещи. Серверный мод: клиенту ничего не нужно, а
 * ванильная механика котла не переписывается — в диспетчер воды дописываются записи «предмет → реакция»,
 * ровно тем же способом, каким ваниль регистрирует стирку знамён и шалкеров.
 */
public class CauldronWash implements ModInitializer {
	public static final String MOD_ID = "cauldronwash";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Наши записи в диспетчере: предмет → реакция, бывшая там до нас ({@code null} = записи не было). */
	private static final Map<Item, CauldronInteraction> OWNED = new HashMap<>();

	@Override
	public void onInitialize() {
		reload();

		// конфиг читается на старте и по команде; правка не требует перезапуска
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("cauldronwash")
						.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
						.then(Commands.literal("reload").executes(context -> {
							int active = reload();
							if (CauldronWashConfig.lastLoadFailed()) {
								context.getSource().sendFailure(Component.literal(
										"CauldronWash: конфиг не прочитан (см. лог), работает умолчания"));
							} else {
								context.getSource().sendSuccess(() -> Component.literal(
										"CauldronWash: конфиг перечитан, стирается предметов: " + active), false);
							}
							return 1;
						}))));

		if (System.getProperty("cauldronwash.selftest") != null) {
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::onTick);
			LOGGER.info("CauldronWash: включена самопроверка (-Dcauldronwash.selftest)");
		}
	}

	/**
	 * Перечитать конфиг и обновить диспетчер воды. Чужую запись на том же предмете не перетираем, а
	 * снимая свою — возвращаем прежнюю реакцию (или удаляем запись, если её не было).
	 */
	public static int reload() {
		CauldronWashConfig config = CauldronWashConfig.load();
		WashTable.build(config.disabledFamilySet());
		config.applyExtraItems();

		Map<Item, CauldronInteraction> water =
				((DispatcherAccessor) CauldronInteractions.WATER).cauldronwash$items();
		Set<Item> washable = WashTable.washable();

		Iterator<Map.Entry<Item, CauldronInteraction>> owned = OWNED.entrySet().iterator();
		while (owned.hasNext()) {
			Map.Entry<Item, CauldronInteraction> entry = owned.next();
			Item item = entry.getKey();
			if (!washable.contains(item)) {
				if (water.get(item) == WashInteraction.INSTANCE) {
					CauldronInteraction previous = entry.getValue();
					if (previous == null) {
						water.remove(item);
					} else {
						water.put(item, previous);
					}
				}
				owned.remove();
			}
		}

		int active = 0;
		for (Item item : washable) {
			CauldronInteraction current = water.get(item);
			if (current == WashInteraction.INSTANCE) {
				active++;
			} else if (current != null) {
				// чужую запись не отбираем и не запоминаем: она может исчезнуть, и тогда
				// восстановление мёртвой реакции при отключении семейства было бы ошибкой
				continue;
			} else {
				OWNED.putIfAbsent(item, null);
				water.put(item, WashInteraction.INSTANCE);
				active++;
			}
		}

		LOGGER.info("CauldronWash: стирается {} предметов в {} семействах: {}",
				active, WashTable.families().size(), WashTable.families().keySet());
		return active;
	}
}
