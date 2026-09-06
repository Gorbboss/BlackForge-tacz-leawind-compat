# Changelog

## 0.4.7

- Removes the v0.4.6 closed mathematical boundary.
- Restores terrain-backed boundary faces from v0.4.5.
- Allows only activated outer wedge walls to extend through air.
- Rejects air-generated end caps and camera-covering faces.
- Remains compatible with BlackForge Unbound 1.8.

## 0.4.6

- Reverts transition layers from black concrete to original textured transparency.
- Generates the black-concrete boundary through air as well as solid terrain.
- Limits bottom boundary faces to positions below the player.
- Pairs with BlackForge Unbound 1.8.

## 0.4.5

- Replaces four directional sides with eight pie-like cutaway sectors.
- Opens an obstructed sector and its two narrower neighboring sectors.
- Stops the cutaway just beyond the last obstructing block.
- Renders all transition layers with the black-concrete texture while fading.
- Pairs with BlackForge Unbound 1.7.

## 0.4.4

- Replaces the untextured black shell with inward-facing black-concrete quads.
- Uses the vanilla black-concrete atlas texture without placing world blocks.
- Makes the same client-only boundary renderer available with shaders enabled.
- Pairs with BlackForge Unbound 1.6.

## 0.4.3

- Adds proportional one-second smoothing to outer cutaway visibility.
- Adds a one-second restoration transition for departing blocks.
- Adds a camera-only black enclosure around the cutaway and its end caps.
- Updates the matching shader contract for BlackForge Unbound 1.5.

## 0.4.2

- Replaces timed fades with immediate distance-based transparency.
- Adds directional 3x3 obstruction detection for four independent sides.
- Adds the overhead 2x2x2 camera-clearance volume.
- Updates the shader contract for BlackForge Unbound 1.4.

## 0.4.1

- Makes the exact camera block disappear immediately.
- Adds 0.25, 0.50, 0.75, and 1.00 second spatial cutaway bands.
- Expands the Oculus uniform contract for BlackForge Unbound 1.3.

## 0.1.0 - Black Forge private fork
- Reimplemented TaCZ third-person crosshair hook without the removed
  GunAnimationStateMachine target.
- Removed all dependency on Leawind's obsolete GameStatus API.
- Added configurable camera recoil (default off).
- Added forward-only crosshair target filter.
- Added camera collision bypass.
- Added client-side camera obstruction hiding and adjacent boundary-face forcing.
