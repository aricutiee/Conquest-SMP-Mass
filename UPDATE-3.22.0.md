# Conquest SMP 3.22.0

Administrators with conquestsmp.launch can use /smp start cancel. This clears the saved launch timestamp, returning booster kits to the pre-launch lock. The next /smp start starts a fresh 24-hour unlock delay and restores the normal world border. Existing per-player kit claim cooldowns and inventories remain intact. Repeating cancel while already waiting is harmless. Tab completion includes start and cancel.

Use /spawn area to select the two horizontal corners if not already selected. Then /spawn border sets the actual overworld world border around that area. Minecraft borders are square, so a rectangular selection is enclosed by a square centered on the selection. It applies at all heights and prevents normal walking across its boundary. /spawn border off restores the saved normal border. If the launch is already active, the command now directs the administrator to /smp start cancel first.

Recommended sequence: /smp start cancel, /spawn border, then /smp start when ready. Installing this update does not cancel an active launch or shrink the live world automatically.

Verification covers persisted cancellation, a fresh 24-hour delay after starting again, preserving individual claim cooldowns, enabling the border after cancelling launch, and restoring its original size/center. No live launch cancellation or world-border change was performed as a test.
