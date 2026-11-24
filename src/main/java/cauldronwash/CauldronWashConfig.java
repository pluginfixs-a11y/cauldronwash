package cauldronwash;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * {@code config/cauldronwash.json}. Читается при старте сервера и по {@code /cauldronwash reload}
 * (перезапуск для правки конфига не нужен).
 *
 * <pre>
 * {
 *   "disabled_families": ["glazed_terracotta"],
 *   "extra_items": { "mymod:dyed_rope": "mymod:rope" }
 * }
 * </pre>
 *
 * <p>{@code disabled_families} — выключить семейство целиком; допустимые имена перечислены в логе при
 * старте. {@code extra_items} — свои и модовые предметы: «что стираем» → «во что превращается».
 */
public final class CauldronWashConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean lastLoadFailed;

	/** Признак неудачного чтения конфига: команда должна сказать об этом, а не рапортовать об успехе. */
	public static boolean lastLoadFailed() {
		return lastLoadFailed;
	}

	public List<String> disabled_families = new ArrayList<>();
	public Map<String, String> extra_items = new LinkedHashMap<>();

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cauldronwash.json");
	}

	public static CauldronWashConfig load() {
		lastLoadFailed = false;
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				CauldronWashConfig config = GSON.fromJson(reader, CauldronWashConfig.class);
				if (config != null) {
					if (config.disabled_families == null) {
						config.disabled_families = new ArrayList<>();
					}
					if (config.extra_items == null) {
						config.extra_items = new LinkedHashMap<>();
					}
					return config;
				}
				CauldronWash.LOGGER.warn("CauldronWash: {} пустой, беру значения по умолчанию", path);
			} catch (Exception e) {
				// файл не перезаписываем: владелец должен увидеть свою опечатку, а не потерять правки
				lastLoadFailed = true;
				CauldronWash.LOGGER.error("CauldronWash: не смог прочитать {}, работаю на умолчаниях", path, e);
			}
			return new CauldronWashConfig();
		}

		CauldronWashConfig config = new CauldronWashConfig();
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
			CauldronWash.LOGGER.info("CauldronWash: создал конфиг {}", path);
		} catch (Exception e) {
			CauldronWash.LOGGER.error("CauldronWash: не смог записать {}", path, e);
		}
		return config;
	}

	public Set<String> disabledFamilySet() {
		return new HashSet<>(disabled_families);
	}

	/** Дописывает в таблицу записи {@code extra_items}; неизвестный id — предупреждение, не падение. */
	public void applyExtraItems() {
		for (Map.Entry<String, String> entry : extra_items.entrySet()) {
			Optional<Item> from = item(entry.getKey());
			Optional<Item> to = item(entry.getValue());
			if (from.isEmpty() || to.isEmpty()) {
				CauldronWash.LOGGER.warn("CauldronWash: extra_items {} → {} пропущено (нет такого предмета)",
						entry.getKey(), entry.getValue());
				continue;
			}
			WashTable.addExtra(from.get(), to.get());
		}
	}

	private static Optional<Item> item(String id) {
		if (id == null) {
			return Optional.empty();
		}
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(identifier);
	}
}
