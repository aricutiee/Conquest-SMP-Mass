# Conquest SMP 3.17.0

Paper 1.21.11, Java 21.

## Weapon caps

Spears deal at most 70% of the victim's maximum health per hit; maces at most 99%. This limits final health damage after armor and other damage modifiers. Lower hits remain unchanged. A later hit can kill an injured player. At 20 maximum health, the caps are 14 and 19.8 damage. Configure weapon-damage-caps.spear and weapon-damage-caps.mace as fractions. Cancelled and fully blocked hits stay untouched.

## Floating leaderboards

Use /leaderboard set kills, /leaderboard set deaths, /leaderboard set streaks or /leaderboard set playtime at the desired position. Your feet mark the footer position. /leaderboard remove <type> removes a board. Permission: conquest.leaderboard.admin, default operator. Tab completion lists the four types.

Boards show top ten entries, skin heads and a personal footer per viewer. They face each viewer while staying anchored, with see-through text and purple styling. Default viewing distance is 24 blocks. Locations, stats and cached skins persist in leaderboards.yml. Native player kills, deaths and playtime match the sidebar. Offline records import gradually, two players per second. Skins refresh on login.

Streaks can show one gray completed record and one purple active run per player. When a lower run ends, it disappears; only the highest completed record remains. Every ten kills announces the milestone. Earlier completed streaks cannot be reconstructed if the old version had already reset them.

## Boss rewards and equipment

Both bosses wear Protection VI helmets and boots, Protection V chestplates and leggings, with Unbreaking IV on all four armor pieces. Juggernaut swords use Sharpness V, Warlord swords Sharpness VI. Warlord has no mace. Existing event settings receive a one-time balance migration.

Juggernaut defeat now releases only the mace publicly, replacing automatic delivery to the top damage dealer. Damage rankings remain. A three-times-size visual rises for five seconds, shakes for two seconds, then releases one real mace at the defeat position. Its three-minute glow deadline begins on release and transfers between holders without resetting. Temporary boss gear is not loot. Warlord's separate relic hunt remains.

## Verification

Automated Gradle checks cover caps using maximum health, weaker/cancelled/blocked hits, modifier recalculation, rankings, completed versus active streaks, milestones, boss enchantments, and the 140-tick mace animation release. See bundled XML results for exact totals. These checks do not replace visual or combat testing in a Minecraft client.
