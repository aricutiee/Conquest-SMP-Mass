# Conquest SMP 3.15.0

The Ender Dragon now receives a persistent, namespaced +800 maximum-health contribution. A vanilla dragon therefore has 1,000 health points (500 hearts), five times its normal 200. First adoption preserves its existing health fraction, and subsequent loads do not stack the bonus or heal it. Other maximum-health modifiers are retained. `ender-dragon.health-multiplier` defaults to 5 and supports 1 through 5.

Its small-caps name uses a black, reddish-purple and purple gradient. Its existing native health bar is purple and follows the actual health fraction, without adding a second bar. Vanilla boss bars support preset colors rather than RGB gradients, so the gradient applies to the name only.

Beds, anchors, TNT, end crystals, explosive projectiles and fireworks cannot damage the dragon. The three sitting/perched phases accept direct player melee only, including hits to its body parts. Arrows remain available while it is flying. Administrative kill and void cleanup remain possible. These encounter rules apply to all attackers, including staff.

Successful Nether/End switches in the dimension GUI now show all online players a red LOCKED or OPENED title, matching subtitle and Ender Dragon growl. Changes are announced only after saving succeeds. Closing still evacuates ordinary players to overworld spawn; the four senior staff roles retain their dimension bypass.

Includes the 3.14.0 combat spawn barrier and all previous features. Automated checks cover persistent health, injured adoption, external modifiers, no resurrection, gradient name, every dragon phase's explosion immunity, perched melee, flying arrows, unrelated mobs, and all four dimension announcements. These are automated/platform-mock checks. Actual dragon combat, sounds and title appearance have not been tested through a Minecraft client.

Paper references: https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/EnderDragon.Phase.html and https://jd.papermc.io/paper/1.21.11/org/bukkit/boss/BarColor.html
