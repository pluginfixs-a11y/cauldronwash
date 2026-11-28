package cauldronwash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Самопроверка без клиента: ставит водяной котёл в 3000 блоках от спавна и дёргает настоящий
 * {@code BlockState#useItemOn} за {@code FakePlayer} — то есть ровно тот путь, которым идёт сервер,
 * когда игрок кликает по котлу. Включается системным свойством {@code -Dcauldronwash.selftest=1}.
 */
public final class SelfTest {
	private static final BlockPos ORIGIN = new BlockPos(3000, 200, 3000);

	private SelfTest() {
	}

	private static boolean done;
	private static int passed;
	private static int total;

	/**
	 * Не SERVER_STARTED: в этот момент часть модов ещё держит null вместо сервера в своих обработчиках,
	 * и цепочка событий рвётся. Первый тик — всё уже поднято.
	 */
	public static void onTick(MinecraftServer server) {
		if (done) {
			return;
		}
		done = true;
		run(server);
	}

	public static void run(MinecraftServer server) {
		passed = 0;
		total = 0;
		try {
			ServerLevel level = server.overworld();
			level.setChunkForced(ORIGIN.getX() >> 4, ORIGIN.getZ() >> 4, true);
			level.getChunk(ORIGIN.getX() >> 4, ORIGIN.getZ() >> 4);

			FakePlayer player = FakePlayer.get(level);
			player.setPos(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5);
			player.getAbilities().instabuild = false;
			player.onUpdateAbilities();

			checkRegistered();
			checkWholeTable(level, player);
			checkWhiteWoolKept(level, player);
			checkWholeStack(level, player);
			checkNoPlace(level, player);
			checkComponents(level, player);
			checkLastLevel(level, player);
			checkVanillaUntouched();
			checkVanillaArmor(level, player);
			checkVanillaShulker(level, player);

			CauldronWash.LOGGER.info("SELFTEST итог: {}/{} ok", passed, total);

			if (System.getProperty("cauldronwash.selftest.stop") != null) {
				CauldronWash.LOGGER.info("SELFTEST: останавливаю сервер (-Dcauldronwash.selftest.stop)");
				server.halt(false);
			}
		} catch (Throwable t) {
			CauldronWash.LOGGER.error("SELFTEST упал", t);
		}
	}

	/** Наши предметы действительно попали в диспетчер воды, а не остались ванильными. */
	private static void checkRegistered() {
		CauldronInteraction wool = CauldronInteractions.WATER.get(new ItemStack(Items.WOOL.black()));
		CauldronInteraction glass = CauldronInteractions.WATER.get(new ItemStack(Items.STAINED_GLASS.red()));
		CauldronInteraction candle = CauldronInteractions.WATER.get(new ItemStack(Items.DYED_CANDLE.lime()));
		check("registered", wool == WashInteraction.INSTANCE
				&& glass == WashInteraction.INSTANCE
				&& candle == WashInteraction.INSTANCE);
	}

	/** Каждый предмет из таблицы стирается в свой целевой — через настоящий клик по котлу. */
	private static void checkWholeTable(ServerLevel level, FakePlayer player) {
		int ok = 0;
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<Item, Item> entry : WashTable.targets().entrySet()) {
			ItemStack washed = washOnce(level, player, new ItemStack(entry.getKey()), 3);
			if (washed.is(entry.getValue())) {
				ok++;
			} else if (wrong.size() < 5) {
				wrong.add(name(entry.getKey()) + "→" + name(washed.getItem()) + " (ждали " + name(entry.getValue()) + ")");
			}
		}
		check("table " + ok + "/" + WashTable.size() + (wrong.isEmpty() ? "" : " " + wrong), ok == WashTable.size());
	}

	/** Белая шерсть не тратит воду: стирать нечего. */
	private static void checkWhiteWoolKept(ServerLevel level, FakePlayer player) {
		ItemStack washed = washOnce(level, player, new ItemStack(Items.WOOL.white()), 3);
		int fill = level.getBlockState(ORIGIN).getValue(LayeredCauldronBlock.LEVEL);
		check("white-wool-kept " + name(washed.getItem()) + "/" + fill,
				washed.is(Items.WOOL.white()) && fill == 3);
	}

	/** Пачка целиком: 64 в руке → 64 стёртых, один уровень воды за клик. */
	private static void checkWholeStack(ServerLevel level, FakePlayer player) {
		ItemStack washed = washOnce(level, player, new ItemStack(Items.WOOL.black(), 64), 3);
		boolean kept = washed.is(Items.WOOL.white()) && washed.getCount() == 64;
		int fill = level.getBlockState(ORIGIN).getValue(LayeredCauldronBlock.LEVEL);
		boolean oneLevel = fill == 2;
		check("stack 64->64 " + washed.getCount() + "/" + name(washed.getItem()) + " water=" + fill, kept && oneLevel);
	}

	/** Сервер блок из руки не ставит: над котлом после стирки по-прежнему воздух. */
	private static void checkNoPlace(ServerLevel level, FakePlayer player) {
		level.setBlock(ORIGIN.above(), Blocks.AIR.defaultBlockState(), 3);
		ItemStack washed = washOnce(level, player, new ItemStack(Items.WOOL.black(), 4), 3);
		boolean air = level.getBlockState(ORIGIN.above()).isAir();
		boolean washedOk = washed.is(Items.WOOL.white()) && washed.getCount() == 4;
		check("no-place air=" + air + " washed=" + washedOk, air && washedOk);
	}

	/** Компоненты предмета переезжают на стёртый: имя, содержимое сумки. */
	private static void checkComponents(ServerLevel level, FakePlayer player) {
		ItemStack wool = new ItemStack(Items.WOOL.blue());
		wool.set(DataComponents.CUSTOM_NAME, Component.literal("selftest"));
		ItemStack washedWool = washOnce(level, player, wool, 3);
		boolean nameKept = washedWool.is(Items.WOOL.white()) && washedWool.has(DataComponents.CUSTOM_NAME);

		ItemStack bundle = new ItemStack(Items.DYED_BUNDLE.orange());
		bundle.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
		ItemStack washedBundle = washOnce(level, player, bundle, 3);
		boolean bundleOk = washedBundle.is(Items.BUNDLE)
				&& washedBundle.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
						.nonEmptyItemCopyStream().anyMatch(stack -> stack.is(Items.APPLE));
		check("components name=" + nameKept + " bundle=" + bundleOk, nameKept && bundleOk);
	}

	/** Последний уровень воды: котёл становится пустым, а не «нулевым водяным». */
	private static void checkLastLevel(ServerLevel level, FakePlayer player) {
		ItemStack washed = washOnce(level, player, new ItemStack(Items.STAINED_GLASS.black()), 1);
		boolean glass = washed.is(Items.GLASS);
		boolean empty = level.getBlockState(ORIGIN).is(Blocks.CAULDRON);
		check("last-level glass=" + glass + " cauldron=" + empty, glass && empty);
	}

	/** Ванильные стирки не перетёрты: знамя, шалкер и броня остались за ванилью. */
	private static void checkVanillaUntouched() {
		boolean banner = CauldronInteractions.WATER.get(new ItemStack(Items.BANNER.white())) != WashInteraction.INSTANCE;
		boolean shulker = CauldronInteractions.WATER.get(new ItemStack(Items.DYED_SHULKER_BOX.black())) != WashInteraction.INSTANCE;
		boolean armor = CauldronInteractions.WATER.get(new ItemStack(Items.LEATHER_CHESTPLATE)) != WashInteraction.INSTANCE;
		check("vanilla-untouched banner=" + banner + " shulker=" + shulker + " armor=" + armor,
				banner && shulker && armor);
	}

	/** Ванильная стирка кожи по-прежнему работает: краситель снимается компонентом. */
	private static void checkVanillaArmor(ServerLevel level, FakePlayer player) {
		ItemStack armor = new ItemStack(Items.LEATHER_CHESTPLATE);
		armor.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
		ItemStack washed = washOnce(level, player, armor, 3);
		check("vanilla-armor", washed.is(Items.LEATHER_CHESTPLATE) && !washed.has(DataComponents.DYED_COLOR));
	}

	/** Ванильная стирка шалкера по-прежнему сохраняет содержимое. */
	private static void checkVanillaShulker(ServerLevel level, FakePlayer player) {
		ItemStack shulker = new ItemStack(Items.DYED_SHULKER_BOX.black());
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
		ItemStack washed = washOnce(level, player, shulker, 3);
		boolean plain = washed.is(Items.SHULKER_BOX);
		boolean apple = washed.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.nonEmptyItemCopyStream()
				.anyMatch(stack -> stack.is(Items.APPLE));
		check("vanilla-shulker box=" + plain + " apple=" + apple, plain && apple);
	}

	/** Один клик по котлу с уровнем {@code fill}: возвращает то, что осталось в руке. */
	private static ItemStack washOnce(ServerLevel level, FakePlayer player, ItemStack held, int fill) {
		level.setBlock(ORIGIN,
				Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, fill), 3);
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockState state = level.getBlockState(ORIGIN);
		state.useItemOn(held, level, player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(ORIGIN), Direction.UP, ORIGIN, false));
		return player.getMainHandItem();
	}

	private static void check(String label, boolean ok) {
		total++;
		if (ok) {
			passed++;
		}
		CauldronWash.LOGGER.info("SELFTEST {}: {}", label, ok ? "ok" : "FAIL");
	}

	private static String name(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
