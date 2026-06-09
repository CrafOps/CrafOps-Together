# CrafOps: Together

CrafOps: Together adds immersive physical interactions between players, making multiplayer feel more connected and alive.

Hold hands, guide your friends, move together, and experience Minecraft with a greater sense of presence and companionship.

Designed to stay lightweight and vanilla-friendly, Together focuses on natural player-to-player interactions that fit seamlessly into survival, roleplay, SMPs, and casual multiplayer worlds.

Current and planned features include:

* Hand holding
* Guided movement
* Player-follow interactions
* Shared movement mechanics
* Additional social interactions

Whether you're exploring, roleplaying, or simply spending time with friends, Together helps players stay together.

Summary แบบสั้น
```
Add immersive physical interactions between players, from holding hands to moving together.
```
หรือ
```Bring players closer with immersive social and physical interactions.
```
หรือ
```Hold hands, guide friends, and stay together in a more immersive way.```
ถ้าอยากดูเป็นมอดคุณภาพสูงขึ้นอีกนิด
```A lightweight multiplayer interaction mod focused on connection, movement, and companionship.```

--------------------------------------

Create a Minecraft Fabric mod for Minecraft 1.20.1 using Yarn mappings.

Mod Information:

* Name: CrafOps: Together
* Mod ID: crafops_together
* Package: com.crafops.together

Goal:
Create an immersive multiplayer interaction system that allows players to physically connect with each other without requiring an acceptance request.

Core Feature: Hand Holding

When a player right-clicks another player with an empty hand:

* The initiator starts holding the target player's hand.
* No confirmation GUI or request is required.
* Only one hand-holding connection may exist per player.
* Right-clicking again disconnects the interaction.
* Sneaking while right-clicking also disconnects.

Behavior:

* The target player follows the initiator naturally.
* This should NOT behave like a vanilla lead.
* Movement should feel smooth and player-like.
* The follower should be gently pulled when exceeding a configurable distance.
* Small distances should not force movement.
* Large distances should gradually increase pulling strength.

Distance Rules:

* Comfortable range: 0–2 blocks
* Soft pull begins: 2 blocks
* Strong pull begins: 4 blocks
* Maximum connection distance: 8 blocks
* Beyond maximum distance, automatically disconnect

Movement Requirements:

* Apply velocity adjustments rather than teleportation.
* Avoid jittering and rubberbanding.
* Handle sprinting correctly.
* Handle jumping correctly.
* Handle stairs and slabs reasonably well.
* Server-authoritative implementation.
* Multiplayer-safe.

Visual Feedback:

* Render connected hands between players.
* Display a subtle visual connection.
* No ropes, chains, or leash rendering.
* Keep visuals lightweight and immersive.

Networking:

* Use Fabric networking APIs.
* Synchronize connection state from server to clients.
* Ensure compatibility with dedicated servers.

Architecture:

* Separate systems into:

    * InteractionManager
    * HandHoldComponent
    * Networking
    * Renderer
    * Config

Config Options:

* Maximum distance
* Pull strength
* Auto disconnect distance
* Enable visual connection
* Enable sound effects

Commands:

/together disconnect
/together debug

Code Quality:

* Follow Fabric best practices.
* Use clean object-oriented architecture.
* Avoid giant classes.
* Include comments explaining important systems.
* Make the project production-ready.

Future Expansion:

Design the architecture so future interactions can be added:

* Dragging players
* Carrying players
* Piggyback riding
* Guiding movement
* Social interactions

The hand-holding system should be implemented as the first interaction type within a generic player interaction framework.

Output:
Generate the complete Fabric mod source code, Gradle configuration, mixins, networking, renderer, and registration code required to compile and run the mod.

## Important
"Do not use teleportation. Prioritize smooth velocity-based movement and multiplayer synchronization similar to modern companion movement systems."