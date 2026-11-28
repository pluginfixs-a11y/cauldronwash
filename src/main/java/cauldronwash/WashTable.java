package cauldronwash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ColorCollection;

/**
 * Таблица «цветная вещь → её обесцвеченный вид».
 *
 * <p>Строится не по списку имён, а из ванильных {@link ColorCollection} в {@link Items}: одна запись
 * закрывает все 16 цветов семейства. Мод не заводит ни своих предметов, ни своих компонентов — только
 * дописывает реакции в ванильный диспетчер котла.
 *
 * <p>Семейство пропускается целиком, если цель совпадает с предметом (белая шерсть → белая шерсть):
 * так котёл не тратит воду на то, что уже без красителя.
 */
public final class WashTable {
	/** предмет → во что он превращается */
	private static final Map<Item, Item> TARGETS = new LinkedHashMap<>();
	/** имя семейства → предметы этого семейства (для лога и самопроверки) */
	private static final Map<String, List<Item>> FAMILIES = new LinkedHashMap<>();
	/** все известные имена семейств, включая отключённые */
	private static final Set<String> KNOWN_FAMILIES = new LinkedHashSet<>();

	private WashTable() {
	}

	/**
	 * @param disabledFamilies имена семейств, которые владелец выключил в конфиге
	 */
	public static void build(Set<String> disabledFamilies) {
		TARGETS.clear();
		FAMILIES.clear();
		KNOWN_FAMILIES.clear();

		// у этих семейств «без красителя» — это белый вариант
		family("wool", Items.WOOL, Items.WOOL.white(), disabledFamilies);
		family("carpet", Items.CARPET, Items.CARPET.white(), disabledFamilies);
		family("bed", Items.BED, Items.BED.white(), disabledFamilies);
		family("concrete", Items.CONCRETE, Items.CONCRETE.white(), disabledFamilies);
		family("concrete_powder", Items.CONCRETE_POWDER, Items.CONCRETE_POWDER.white(), disabledFamilies);
		family("harness", Items.HARNESS, Items.HARNESS.white(), disabledFamilies);

		// а у этих — отдельный бесцветный предмет
		family("stained_glass", Items.STAINED_GLASS, Items.GLASS, disabledFamilies);
		family("stained_glass_pane", Items.STAINED_GLASS_PANE, Items.GLASS_PANE, disabledFamilies);
		family("terracotta", Items.DYED_TERRACOTTA, Items.TERRACOTTA, disabledFamilies);
		family("glazed_terracotta", Items.GLAZED_TERRACOTTA, Items.TERRACOTTA, disabledFamilies);
		family("candle", Items.DYED_CANDLE, Items.CANDLE, disabledFamilies);
		family("bundle", Items.DYED_BUNDLE, Items.BUNDLE, disabledFamilies);

		Set<String> unknown = new LinkedHashSet<>(disabledFamilies);
		unknown.removeAll(KNOWN_FAMILIES);
		if (!unknown.isEmpty()) {
			CauldronWash.LOGGER.warn("CauldronWash: неизвестные имена в disabled_families: {} (допустимые: {})",
					unknown, KNOWN_FAMILIES);
		}

		// Ванильные семейства не трогаем намеренно:
		// * шалкер — ваниль уже стирает его до обычного, сохраняя содержимое (shulkerBoxInteraction);
		// * знамя — ваниль снимает верхний слой узора, оставляя цвет (bannerInteraction);
		// * краситель (Items.DYE) — бесцветного красителя не существует.
	}

	private static void family(String name, ColorCollection<Item> source, Item target, Set<String> disabled) {
		KNOWN_FAMILIES.add(name);
		if (disabled.contains(name)) {
			return;
		}
		List<Item> items = new ArrayList<>(16);
		for (Item item : source.asList()) {
			if (item != target) {
				TARGETS.put(item, target);
				items.add(item);
			}
		}
		FAMILIES.put(name, items);
	}

	/** Записи из конфига: свои и модовые предметы, которых нет в ванильных семействах. */
	public static void addExtra(Item item, Item target) {
		if (item != target) {
			TARGETS.put(item, target);
		}
	}

	public static Item targetOf(ItemStack stack) {
		return TARGETS.get(stack.getItem());
	}

	public static Set<Item> washable() {
		return new LinkedHashSet<>(TARGETS.keySet());
	}

	public static Map<Item, Item> targets() {
		return TARGETS;
	}

	public static Map<String, List<Item>> families() {
		return FAMILIES;
	}

	public static int size() {
		return TARGETS.size();
	}
}
