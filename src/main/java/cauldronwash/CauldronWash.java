package cauldronwash;

import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
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

	@Override
	public void onInitialize() {
		CauldronWashConfig config = CauldronWashConfig.load();
		WashTable.build(config.disabledFamilySet());
		config.applyExtraItems();

		Map<Item, CauldronInteraction> water =
				((DispatcherAccessor) CauldronInteractions.WATER).cauldronwash$items();

		int registered = 0;
		for (Item item : WashTable.washable()) {
			water.put(item, WashInteraction.INSTANCE);
			registered++;
		}

		LOGGER.info("CauldronWash: стирается {} предметов в {} семействах: {}",
				registered, WashTable.families().size(), WashTable.families().keySet());

		if (System.getProperty("cauldronwash.selftest") != null) {
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::onTick);
			LOGGER.info("CauldronWash: включена самопроверка (-Dcauldronwash.selftest)");
		}
	}
}
