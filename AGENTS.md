# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`teleport` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that lets the player teleport without asking the server: `/tp @c …` and `/teleport @c …` change only the local player's position, whatever the player's permissions are. It also offers Click TP (teleport to the block you right-click) and quick destinations such as `back`, `home`, `spawn` and `worldspawn`.

[urntt/nojumpdelay](https://github.com/urntt/nojumpdelay) is the reference for the configuration screen, the configuration options, the multiplayer rules and the overall project layout. [urntt/litematica-creator](https://github.com/urntt/litematica-creator) shows how to render a translucent copy of the local player on Minecraft 26.3. [urntt/nomovementcheck](https://github.com/urntt/nomovementcheck) is a separate server-side mod that makes a server accept client teleports for trusted players; this mod only recommends it and never depends on it.

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `teleport` |
| Display name | `teleport` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.teleport` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must clearly warn about the risks:
  - Multiplayer: client teleports may conflict with server anti-cheat systems, and using the mod in multiplayer may get the player set back, kicked, or banned. The mod does not try to bypass any anti-cheat, and it is disabled in multiplayer until the player enables it on the configuration screen.
  - Singleplayer: the integrated server still validates movement and may move the player back; a teleport can drop the player into the void, lava or a fall, which can end a Hardcore world.
- `README.md` recommends [urntt/nomovementcheck](https://github.com/urntt/nomovementcheck) for servers the player controls.

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no server-side component and no custom networking.
- Pure client teleport, in singleplayer and in multiplayer alike: the mod changes the local player's position and lets the normal movement packets report it. The server, including the integrated singleplayer server, validates it as usual.
  - The mod never tries to get around server checks: no splitting moves into several packets, no faked on-ground flags, no extra or repeated movement packets, nothing else of the kind.
  - When the server moves the player back, the mod shows nothing.
- The only packet the mod sends itself is vanilla's spectator `ServerboundTeleportToEntityPacket`, exactly as the vanilla spectator menu sends it, used as a fallback destination source while the player is a spectator.
- Only the local player is affected, together with the root vehicle the local player controls. A client teleport never changes dimension.
- `/tp @c` and `/teleport @c` commands are intercepted on the client before they would be sent to the server. Every other command, including vanilla `/tp` with other targets, is sent to the server unchanged. Do not register them through Fabric's client command API: Fabric executes client commands first, so a client `tp` would shadow vanilla `/tp`.
- Command syntax after `@c`: everything vanilla `/tp <targets> …` accepts (location, rotation, `facing`, entity destination), plus `back [client|server|death]`, `spawn`, `worldspawn`, `home`, `temp`, `sethome`, `settemp` and `entities`. Every destination form accepts a trailing `get`, which prints the resolved destination instead of teleporting. `/tp @c` alone prints a menu of clickable destinations.
- Entity destinations are evaluated on the client against loaded entities. Players or UUIDs the client has not loaded fall back to the spectator teleport (while a spectator), then to an exact locator bar waypoint, and otherwise fail with whatever the locator bar knows.
- Click TP teleports the player to the block they right-click, within a configurable reach (0 to 32767 blocks, 64 by default). It is off by default, temporarily off while sneaking, repeats while the use key is held, and shows a translucent copy of the player at the destination. It never checks whether the player fits at the destination.
- `TeleportController` is the single owner of whether teleporting is allowed in the current scene and whether Click TP is active, following nojumpdelay's `JumpDelayController`:
  - Click TP has a current state, a singleplayer default and a server default (all off by default), and the reset rules "reset on world exit" and "reset on game exit" (both off by default).
  - The multiplayer mode is a hard limit: `DISABLED` (the default) rules out every server, `WHITELIST` allows only servers in the server list, and `BLACKLIST` allows every server except those in it. Joining another player's LAN world or a Realm counts as multiplayer. Server list entries match like nojumpdelay's.
  - On a ruled-out server, Click TP stays off and commands that would move the player only report that teleporting is disabled there. `get`, the menu, `sethome` and `settemp` do not move the player and work everywhere.
- Key bindings, all unbound by default: toggle Click TP (shows the new state on the action bar and saves it) and open the configuration screen. The configuration screen also opens through Mod Menu.

### Localization

- All user-facing text, including key binding names, the key binding category, action bar and chat messages, the configuration screen, and command feedback, uses translation keys. Never hard-code display strings. Reuse vanilla translation keys where vanilla shows the same message (for example `commands.teleport.success.location.single`).
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Configuration is hand-written without a config library: JSON files in the Fabric config directory, serialized with Gson (bundled with Minecraft). Any configuration screen uses vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json`. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyExpressionValue` and `@WrapOperation`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small.
- Commands reach the server through two `ClientPacketListener` methods: `sendCommand` for typed commands and `sendUnattendedCommand` for chat click events. Both must be intercepted.
- Fabric's `ClientPlayConnectionEvents.DISCONNECT` does not fire reliably when a singleplayer world is closed, so per-world state is also reset on `JOIN`.
- Vanilla treats a client teleport as ordinary movement: the server adds every block moved down to the fall distance, even when the player lands on a block. The client game tests measure this, and `README.md` warns about it; do not try to avoid it.

### Testing

- The client game tests in `src/gametest` start Minecraft and check the commands, the destinations, Click TP, the key bindings, the multiplayer rules on a local dedicated server, and the saved configuration. Keep them passing and extend them when behavior changes.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests on every push and pull request.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
