# Floating sky words

Run `/setword purple Crates` while standing or flying at the desired location. The label is centered one block above your eye position. Text after the color can contain spaces. All letters are converted to bold Unicode small caps. Each viewer sees the front of the label. It has a transparent background and a text shadow, and does not show through blocks.

Colors include purple, red, pink, blue, aqua, green, yellow, gold, white, black, gray, dark_purple and Minecraft named colors. Hex colors also work: `/setword #B477FF Welcome to Conquest`.

Each label receives an ID such as word1:

* `/setword list` shows IDs and contents.
* `/setword move word1` moves it above your current eye position.
* `/setword size word1 8` changes its scale. Default 4, allowed 1 through 20.
* `/setword edit word1 red Arena` changes its color and words.
* `/setword remove word1` removes that label.

Operators, Admin, Senior Admin, Co-owner and Owner can manage labels. Explicit permission `conquest.setword.admin` also grants access and defaults to operators. Console can list, edit, resize and remove labels; placement and movement require a player.

Labels are saved in plugins/ConquestSMP/sky-words.yml, separately from leaderboards. They return after restart or when their chunk loads. No chunks are force-loaded. A lightweight five-second check visits only saved labels and restores missing displays. Displays are removed on shutdown and are not saved as permanent world entities. There is a limit of 200 labels and 100 characters per label. No extra resource pack or client mod is required.

Verification: automated tests cover literal small-caps conversion, colors, permissions, size validation, saving, reload and removal. Full build and packaged SQLite verification are run. Visual appearance, placement and rotation require an in-game check and are not claimed as live-tested.
