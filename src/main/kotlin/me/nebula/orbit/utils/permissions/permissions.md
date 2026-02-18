# Permissions

Hierarchical permission group system with inheritance support.

## Overview

Permission-based access control with named groups, inheritance chains, and per-player group assignment. Groups define permissions and can inherit from other groups. Player permissions are checked across all assigned groups.

## Key API

- `PermissionManager.defineGroup(name: String, block: PermissionGroupBuilder.() -> Unit): PermissionGroup` - Create permission group
  - `permission(perm: String)` / `permissions(vararg perms: String)` - Add permissions to group
  - `inherit(groupName: String)` - Inherit permissions from another group
  - `prefix: String` - Display prefix for group
  - `priority: Int` - Priority level
- `PermissionManager.applyGroup(player: Player, groupName: String)` - Assign group to player
- `PermissionManager.removeGroup(player: Player, groupName: String)` - Remove group from player
- `PermissionManager.hasPermission(uuid: UUID, permission: String): Boolean` - Check permission (includes inheritance)
- `Player.hasOrbitPermission(permission: String): Boolean` - Extension for player permission check

## Examples

```kotlin
PermissionManager.defineGroup("admin") {
    permissions("admin.ban", "admin.kick", "admin.console")
    prefix = "[ADMIN]"
    priority = 100
}

PermissionManager.defineGroup("moderator") {
    permissions("mod.kick", "mod.mute")
    inherit("user")
    prefix = "[MOD]"
    priority = 50
}

PermissionManager.applyGroup(player, "admin")
if (player.hasOrbitPermission("admin.ban")) { }
```
