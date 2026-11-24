# Cauldron Wash

Server-side Fabric mod for Minecraft **26.2**. A water cauldron strips the dye from **any** coloured item,
not just the six dyed-armour pieces vanilla handles. Vanilla clients need nothing installed.

## What washes

| family | example | becomes |
|---|---|---|
| wool | black wool | white wool |
| carpet | red carpet | white carpet |
| bed | blue bed | white bed |
| concrete | lime concrete | white concrete |
| concrete powder | lime concrete powder | white concrete powder |
| harness | green harness | white harness |
| stained glass | red stained glass | glass |
| stained glass pane | red stained glass pane | glass pane |
| terracotta | black terracotta | terracotta |
| glazed terracotta | black glazed terracotta | terracotta |
| candle | lime candle | candle |
| dyed bundle | orange bundle | bundle |

All 16 colours of every family, **186 items** in total. The table is built from vanilla
`ColorCollection` fields, so a family is one line of code and never a list of item names.

Families whose "undyed" form is the white variant (wool, carpet, bed, concrete, concrete powder,
harness) skip the white item itself: there is nothing to strip and the cauldron keeps its water.

## What is left alone

* **shulker boxes** — vanilla already washes them to a plain shulker box and keeps the contents
  (`shulkerBoxInteraction`); the mod does not touch that entry.
* **banners** — vanilla removes the top pattern layer and keeps the colour (`bannerInteraction`).
  Turning a banner white would throw away every pattern at once, so that stays vanilla.
* **dye** — there is no undyed dye.
* **dyed armour** (leather helmet/chestplate/leggings/boots, leather and wolf horse armour) — already
  in the vanilla tag `#minecraft:cauldron_can_remove_dye`, handled by `dyedItemIteration`.

## Behaviour

* **the whole stack washes in one click**: 64 black wool in hand → 64 white wool, and it costs one
  water level per click, not per item;
* the cauldron loses one level per click; the last level turns it back into an empty cauldron;
* everything except the dye travels to the new item (`ItemStack#transmuteCopy`): custom name, bundle
  contents, enchantments, damage;
* no chat messages, no sounds of its own — the vanilla block update is the only feedback.

### The block that flashes for a moment

Right-clicking a cauldron with wool, glass or terracotta briefly shows the block being placed. That is
**not** the mod: the server never places it (`ServerPlayerGameMode#useItemOn` returns as soon as the
interaction consumes the action, and the self test checks that the space above the cauldron stays air).
What you see is the vanilla **client** predicting a placement — it does not know these items are
washable, so it runs `BlockItem#place` locally and drops the prediction when the server's answer
arrives, one round trip later.

A server-side mod cannot suppress that prediction; only a client-side companion could, by registering
the same entries in the client's cauldron dispatcher so the client's own `useItemOn` returns SUCCESS
and never predicts a placement. Items that are not blocks (harness, bundle) do not flash at all.

## Config

`config/cauldronwash.json`, created on first start. Read at startup and on command:

`/cauldronwash reload` (admin permission) re-reads the file and rebuilds the wash table **in place** — no
restart needed. Old dispatcher entries are removed only if they still point at this mod's interaction, so a
vanilla or third-party reaction on the same item is never lost.

```json
{
  "disabled_families": ["glazed_terracotta"],
  "extra_items": {
    "mymod:dyed_rope": "mymod:rope"
  }
}
```

`disabled_families` — switch a whole family off; the valid names are printed to the log at start.
`extra_items` — your own or another mod's items: `what is washed` → `what it becomes`.

## How it works

`CauldronInteraction.Dispatcher` keeps a map `Item -> CauldronInteraction` and vanilla registers dye
washing in it two ways: a per-item loop for banners and shulker boxes, and a tag for dyed armour. The
`put` methods are package-private, so one mixin accessor exposes the map, and the mod adds one entry per
coloured item pointing at a single `CauldronInteraction` implementation. No block, item or component is
added, and the vanilla cauldron code is never patched — another mod touching the cauldron keeps working.

Client-side prediction is not involved: the vanilla client does not know these items are washable, so
the server performs the wash and syncs the slot and the block update. There is no desync to observe.

## Self test

```
java -Dcauldronwash.selftest=1 -Dcauldronwash.selftest.stop=1 -jar server.jar nogui
```

On the first server tick the mod builds a cauldron 3000 blocks from spawn and runs every entry of the
table through the real `BlockState#useItemOn` path with a `FakePlayer`. `-Dcauldronwash.selftest.stop`
shuts the server down right after the run, so a headless check needs no console.

```
SELFTEST registered: ok
SELFTEST table 186/186: ok
SELFTEST white-wool-kept minecraft:white_wool/3: ok
SELFTEST stack 64->64 64/minecraft:white_wool water=2: ok
SELFTEST no-place air=true washed=true: ok
SELFTEST components name=true bundle=true: ok
SELFTEST last-level glass=true cauldron=true: ok
SELFTEST vanilla-untouched banner=true shulker=true armor=true: ok
SELFTEST vanilla-armor: ok
SELFTEST vanilla-shulker box=true apple=true: ok
SELFTEST итог: 10/10 ok
```

Run on 18.11.2026 with the whole 38-mod server set: 10/10 ok, zero `ERROR` lines in the log.

## Changes

**1.0.1** — `/cauldronwash reload`: the config is no longer read only once at startup; the water dispatcher
is updated without a restart.

## Build

```powershell
.\gradlew.bat build
```

Jar lands in `build/libs/cauldronwash-1.0.1.jar`. Minecraft 26.x ships unobfuscated, so there is no
`mappings` line and dependencies are plain `implementation`.
