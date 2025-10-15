# ModeratorMode for Minecraft

[![Version](https://img.shields.io/badge/Version-1.0-blue.svg)](https://github.com/YourUsername/ModeratorMode-Minecraft/releases)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/ImVineprexDE/ModeratorMode.svg)](https://GitHub.com/ImVineprexDE/ModeratorMode/issues/)

A powerful and fully customizable moderator mode for PaperMC servers, designed to streamline your staff's workflow without interrupting their gameplay.

---

## 📖 Overview

**ModeratorMode** is an essential tool that allows staff members to switch into a dedicated moderation mode with a single command. It safely saves their entire survival state—including location, inventory, health, and effects—and equips them with a configurable set of admin tools. This allows them to perform their duties efficiently and return to their previous state exactly as they left it.

This plugin is built to be robust, lightweight, and fully manageable from in-game, making it a perfect fit for any server.

---

## ✨ Features

-   **Seamless State Saving:** Toggling moderator mode (`/mm`) instantly saves a player's location, inventory, gamemode, health, food level, experience, and potion effects.
-   **Crash-Proof Persistence:** Player data is saved to a local JSON file, ensuring that even if the server crashes, a moderator's inventory and state are never lost.
-   **Fully Configurable Hotbar:** Admins can define a custom set of items that moderators receive upon entering the mode. Add special tools, compasses, or custom items from other plugins directly from in-game!
-   **Built-in Moderator Tools:**
    -   **Simple Vanish:** Automatically hides moderators from regular players using the reliable built-in Bukkit method.
    -   **Permanent Night Vision:** Grants infinite night vision to ensure moderators can see everything clearly, day or night.
    -   **Item Drop Protection:** Prevents moderators from accidentally dropping their configured tools.
-   **Easy In-Game Management:** All hotbar items can be managed via simple admin commands. No need to manually edit YAML files after the initial setup.
-   **Lightweight & No Dependencies:** Built to be efficient and stable with no external plugin dependencies required for core functionality.

---

## 🎮 Commands & Permissions

### Player Commands
Requires the permission `moderatormode.use`.
| Command | Alias | Description |
| :--- | :--- | :--- |
| `/modmode` | `/mm` | Toggles moderator mode on or off. |
| `/modmode help` | | Displays the help message. |

### Admin Commands
Requires the permission `moderatormode.admin`.
| Command | Description |
| :--- | :--- |
| `/modmode list` | Shows the current list of configured hotbar items. |
| `/modmode add` | Adds the item you are currently holding to the hotbar list. |
| `/modmode remove <number>` | Removes an item from the list by its number (from `/mm list`). |
| `/modmode reload` | Reloads the plugin's configuration file from disk. |

---

## ⚙️ Installation

1.  Download the latest `ModeratorMode-*.jar` from the [**Releases Page**](https://github.com/ImVineprexDE/ModeratorMode/releases).
2.  Place the JAR file into your server's `/plugins` folder.
3.  Restart your server. The default configuration file (`config.yml`) will be generated.
4.  (Optional) Grant the `moderatormode.use` and `moderatormode.admin` permissions to your staff groups using a permissions plugin like [LuckPerms](https://luckperms.net/).

That's it! Your staff can now use `/mm` to moderate more effectively.

---

## 🛠️ Building from Source

If you wish to build the plugin from the source code, you will need:
-   Java 17 or higher
-   Apache Maven
