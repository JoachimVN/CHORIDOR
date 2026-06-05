<p align="center">
    <img src="src/main/resources/images/logos/Choridor_Logo.png" alt="Choridor Title Logo"/>
</p>

<p align="center">
    <strong>A desktop implementation of Quoridor built with Java and JavaFX.</strong>
</p>

<p align="center">
    <a href="https://github.com/JoachimVN/Choridor/releases">Releases</a>
    |
    <a href="https://github.com/JoachimVN/Choridor/issues">Issues</a>
    |
    <a href="https://github.com/JoachimVN/Choridor/pulls">Pull Requests</a>
    |
    <a href="https://github.com/JoachimVN/Choridor/milestones">Milestones</a>
    |
    <a href="https://github.com/JoachimVN/Choridor/commits/main">Commits</a>
</p>

Choridor is a desktop implementation of Quoridor — a two-player strategy board game played on a 9×9 grid.
Each player races to reach the opposite side of the board while placing walls to block their opponent's path.
Walls must never completely seal off a player's route, keeping every game solvable until the final move.

## Project Links
- Repository: https://github.com/JoachimVN/Choridor
- Releases: https://github.com/JoachimVN/Choridor/releases
- Issues: https://github.com/JoachimVN/Choridor/issues
- Pull Requests: https://github.com/JoachimVN/Choridor/pulls
- Milestones: https://github.com/JoachimVN/Choridor/milestones

## Screenshots

<p align="center">
    <img src="src/main/resources/images/logos/screenshots/Board_Example.png" alt="Choridor Board"/>
    <br>
    <em>In-game board</em>
</p>

## Why This Project

A personal project built to explore:

- object-oriented domain modelling
- JavaFX UI architecture and canvas rendering
- rule engine design (move validation, BFS path-checking)
- testability and maintainability

## Core Features

- Full Quoridor rules — pawn moves with jump logic, wall placement with path-check enforcement
- Human vs Human local play
- Dark-slate themed board with legal-move dot indicators and hover-preview for walls
- Player-coloured walls with per-player wall-count display
- Win overlay and turn indicator
- Cross-platform packaging (Windows EXE, Linux zip, macOS DMG, portable JAR)

## Tech Stack

- Java 25
- JavaFX 26
- Maven
- JUnit 5
- Gson

## Tools Used

- Visual Studio Code
- Git
- GitHub
- Figma

## Getting Started

### Prerequisites

- JDK 25 installed and available on PATH
- Maven 3.9+ installed

### Run the App (development)

```bash
mvn javafx:run
```

### Run Tests

```bash
mvn test
```

Generate coverage report:

```bash
mvn verify
```

## Build and Distribution

### Releases

Pushing a `v*` tag triggers the release workflow, which builds and uploads all distribution artifacts automatically:

| Asset | Platform |
|---|---|
| `Choridor-<version>-windows.exe` | Windows installer (bundles JRE) |
| `Choridor-<version>-linux.zip` | Linux app-image |
| `Choridor-<version>-macos.dmg` | macOS disk image (Apple Silicon) |
| `Choridor-<version>-portable.jar` | Portable fat JAR (all platforms) |

### Portable JAR (cross-platform)

```bash
mvn package -Pportable-jar
```

Produces a fat runnable JAR that runs on Windows, Linux, and macOS without any additional install.

### Windows EXE installer

Requires [WiX Toolset v3](https://wixtoolset.org/) installed and on PATH.

```bash
mvn package -Pexe
```

Produces a Windows installer at `target/dist/Choridor-<version>.exe`. The installer bundles a JRE — recipients need nothing pre-installed.

### Linux app-image

```bash
mvn package -Plinux
```

Produces a zipped app-image at `target/dist/Choridor-<version>-linux.zip`. Extract and run `Choridor/bin/Choridor`.

### macOS DMG (Apple Silicon)

```bash
mvn package -Pmac
```

Produces a disk image at `target/dist/Choridor-<version>-macos.dmg`. Intel Mac users should replace the `mac-aarch64` classifier with `mac` in the `mac` profile in `pom.xml`.

## Project Structure

```text
src/main/java/io/github/joachimvn
    App.java                 # JavaFX application entry point and UI wiring
    Launcher.java            # Fat-JAR entry point
    core/model/              # domain model (GameState, Player, Wall, Move, Position, etc.)
    core/rules/              # rules engine (MoveValidator, PathChecker, GameEngine)
    strategy/                # strategy interface and RandomStrategy implementation
    ui/                      # JavaFX UI (BoardView, GameController)

src/main/resources
    css/                     # UI styling
    fonts/                   # custom typeface
    images/                  # logos and visual assets
```

## Developer

[Joachim Valdersnes Nilsen](https://github.com/JoachimVN)
