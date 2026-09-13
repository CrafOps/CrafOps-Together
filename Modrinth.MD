# CrafOps: Together

CrafOps: Together is a lightweight Fabric mod that lets players hold hands and move together naturally.

Right-click another player with an empty main hand to send a request. Once accepted, each follower remains fully controllable while a server-authoritative, velocity-based pull gently keeps the pair together. Right-click your partner again to let go.

## Features

- Smooth hand holding without teleporting or locking player input
- Optional consent requests with a non-pausing Accept/Decline screen
- Configurable player chains (two players by default)
- Separate left/right arm poses and a subtle hand connection visual
- Dedicated-server-safe networking and late-join synchronization
- Automatic cleanup on death, logout, dimension changes, or excessive distance
- English and Thai localization

## Commands

- `/together disconnect`
- `/together accept [player]`
- `/together decline [player]`
- `/together debug` (operator level 2)
- `/together reload` (operator level 2)

## Configuration

The server creates `config/crafops_together.json` on first launch. `maxPlayersPerChain` defaults to `2` and can be configured from `2` to `64`. Consent, request limits, movement tuning, disconnect distance, visuals, and sounds can also be changed. Run `/together reload` to apply edits without restarting.

Requires Fabric Loader and Fabric API for Minecraft 1.20.1. Install the mod on both the server and every client.
