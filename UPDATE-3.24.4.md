# ConquestSMP 3.24.4

Adds /troll on|off for the stored Owner role only. Console, ordinary operators, and other staff cannot enable it, and no target argument is accepted.

The session-only permission attachment enables grim.disabled, grim.nosetback and grim.nomodifypacket for the issuing owner. Grim's API refreshes that player's permissions. Off removes only this attachment, preserving pre-existing permissions. Disconnect, plugin shutdown, or loss of Owner rank removes the exemption. Toggles are recorded in the server log. Other players remain checked.

Scope: the installed Grim integration. ClientPolicy and ModDetector observations remain available. This does not disable vanilla movement validation, connection limits, or protections in arbitrary third-party plugins, and does not itself grant flight or reach.

Validation: 369 tests pass; packaged database smoke check and build pass. Owner-role gating, operator rejection, target-argument rejection and missing-Grim handling tested. Live client movement verification remains manual.
