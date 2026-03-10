# Catnip

Catnip is a specialized PaperMC plugin designed to enhance feline behavior and introduce a comprehensive bonding system between players and their tamed cats.

## Features

### Bonding System

The plugin implements a persistent bonding mechanic for tamed cats. Bonding levels influence cat behavior and can be tracked by players.

- **Bond Increases:** Players can improve their bond with their cats through positive interactions, such as feeding or playing with them.
- **Bond Decreases:** Hitting a tamed cat results in a significant reduction in the bonding level.
- **Bond Decay:** Bonding levels naturally decrease over time if a cat is neglected for extended periods.

### Enhanced AI and Behaviors

Catnip introduces several custom goals and behaviors for tamed cats:

- **Hugging:** Cats with high bonding levels (85% or higher) may occasionally approach their owner to hug them, accompanied by purring and heart particles.
- **Play Fighting:** Cats with low bonding levels (below 35%) may engage in playful fights with their owner, involving minor damage and hissing sounds.
- **Item Interaction:** Cats actively play with dropped items, specifically string and fish (cod and salmon).
- **Environmental Interaction:** Cats may choose to sit on warm blocks such as furnaces, blast furnaces, and smokers, or rest on red carpets.
- **Fish Theft:** Cats have a chance to steal fish from nearby chests when they are opened, running away with the item.

## Commands

| Command | Description |
|--------|-------------|
| `/catbond [player]` | Displays the bonding levels, names, and collar colors of all cats owned by the specified player. If no player is specified, the sender's cats are shown. |

## Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `catnip.bond` | Allows a player to check their own cat bonding levels. | true |
| `catnip.admin` | Allows checking bonding levels of other players' cats and receives update notifications on join. | op |

## Technical Requirements

- **Platform:** PaperMC  
- **API Version:** 1.21  
- **Dependencies:** Utilizes bStats for anonymous metrics collection.
