# Anti-Kostka (Fabric, Minecraft 1.21.4)

Client-side mod that saves your hotbar layout and moves items back to their saved slots.
Items are matched by registry id (e.g. `minecraft:stick`). **The stack count is never used**, so 1 or 64 sticks are the same thing.

## STATUS: NOT COMPILED, NOT TESTED
This project was written without network access, so Gradle could not run. It has not been compiled
and none of the test scenarios have been run in-game. Expect to fix a small compile error or two.
Version pins in `gradle.properties` (Yarn `1.21.4+build.8`, Fabric API `0.119.4+1.21.4`, Loader `0.16.10`,
Loom `1.9-SNAPSHOT`) come from memory; if Gradle cannot resolve one, take the current value from
https://fabricmc.net/develop/ for 1.21.4.

API calls I am least sure of in this exact version (check these first if compilation fails):
- `ClientPlayerInteractionManager.isBreakingBlock()` (used in `HotbarRestorer.canAct`)
- `ItemEnchantmentsComponent.getLevel(RegistryEntry<Enchantment>)` (`Fingerprint`)
- `ComponentType.getCodec()` and `ComponentChanges.entrySet()` (`Fingerprint.componentsHash`)
- `DrawContext.drawTooltip(TextRenderer, Text, int, int)` (`LayoutScreen`)
- `HudRenderCallback` lambda signature `(DrawContext, RenderTickCounter)`

## Build
Requirements: JDK 21, internet access for the first build.

1. The Gradle wrapper JAR is not included. In this folder run once: `gradle wrapper --gradle-version 8.12`
   (needs a local Gradle install), or copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`
   from the Fabric example mod. Alternatively open the folder in IntelliJ IDEA and let it import the project.
2. Run `./gradlew build` (Windows: `gradlew.bat build`).
3. The mod JAR is `build/libs/anti-kostka-1.0.0.jar` (ignore the `-sources` JAR).

## Install
1. Install Fabric Loader for 1.21.4 and Fabric API for 1.21.4.
2. Put `anti-kostka-1.0.0.jar` into `.minecraft/mods`.
3. Config file: `.minecraft/config/anti_kostka.json` (created on first save).

## Controls (Options > Controls > Anti-Kostka, all rebindable)
| Action | Default |
|---|---|
| Save Layout | `]` |
| Toggle Anti-Kostka | `[` |
| Open Settings screen | `\` |
| Clear Layout | unbound, must be pressed twice within 3 s |
| Restore Now (one step, works even when disabled) | unbound |

A status indicator (ON/OFF, "no layout", "paused") is shown top-left; it can be switched off in the settings screen.

## Changing your layout
Key binds do not work inside the inventory screen. After you close it, auto-restore waits 40 ticks (2 s,
`graceTicks` in the config) so you can press Save Layout. Or toggle Anti-Kostka off, rearrange, save, toggle on.
Saving an empty hotbar is refused, so you cannot wipe a layout by accident.

## Matching
- **Basic (default):** registry id only.
- **Advanced:** additionally compares the facets you enable: enchantments, custom name, durability, all components.
  All facets are stored when you save, so changing these options does not require re-saving.
  "All components" compares every non-default component (this includes damage).

## How restoring works
- Only inventory clicks of type `SWAP` are sent (`clickSlot`). They are validated by the server like normal player clicks;
  the mod never edits the inventory directly.
- It swaps in a missing item, one swap every 4 ticks, and only re-checks when item identities change (not counts).
- A source stack is never taken from a hotbar slot that is already correct. Preference: a 2-way swap that fixes both slots,
  then other hotbar slots, then the main inventory, lowest slot first.
- Nothing is deleted, merged, split, dropped or duplicated. Missing items leave their slot untouched.
- Saved-empty slots are never targets. An unwanted item can still be swapped into one when it is displaced.
- It pauses while a screen or container is open, while eating/using an item, while breaking a block, with an item on the cursor, and in spectator mode.
- If the state does not change after a click 3 times in a row, or more than 16 swaps happen in 20 s, it pauses for 10 s
  and shows a message. It does not try to bypass server restrictions.

## Limitations
- One stack is moved per saved slot. Extra stacks stay where they are, and stacks are never split to fill two slots with the same item.
- Creative mode behavior with SWAP clicks is untested.
- Servers that forbid inventory clicks or reshuffle your items constantly will make the mod pause.

## Manual test checklist (none of these have been run)
A. 64 sticks saved in slot 5, later 12 sticks in slot 2: they should return to slot 5.
B. Save sword/pickaxe/axe/food/blocks, shuffle, close the inventory: layout returns.
C. Several stacks of one item: count items before and after, nothing lost or duplicated.
D. Save, then drop a saved item: no crash, nothing else moves.
E. Pick up an unrelated item: it stays put.
F. Save, restart the game, open the Settings screen: layout still shown.
G. Rearrange, save again: new layout is used.
H. Advanced mode with differently enchanted or renamed items.
I. Leave slots empty when saving: they are not touched.
J. Join a server, shuffle the hotbar: restore works or the mod pauses, with no item loss.
