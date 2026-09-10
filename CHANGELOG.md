# Changelog

## 0.4.28

- Adds a final guard at Leawind's `EntityAgent#setRawRotation` write while an
  eligible Tactical attack is held.
- Prevents a cached/smoothed camera-hit rotation from bypassing the earlier
  interaction-strategy suppression on the firing frame.

## 0.4.27

- Makes the forward attack behavior conditional on rear third person,
  BlackForge Movement Tactical stance, no ADS, and an eligible held item.
- Includes swords, axes, and TaCZ guns automatically, with a client-config list
  for additional item IDs.
- Suppresses Leawind's attack-key interaction rotation only while those
  conditions are active.
- Synchronizes that state for matching authoritative TaCZ shot direction.
- Keeps BlackForge Movement optional and preserves Passive/ADS behavior.

## 0.4.26

- Removes the 0.4.25 client-tick player-rotation override that caused the
  character to snap toward the crosshair while firing.
- Intercepts TaCZ's final server firing direction instead: non-ADS shots use
  zero pitch and the character body yaw, while ADS keeps TaCZ's original
  camera/crosshair pitch and yaw.
- Makes initialization dedicated-server safe; this version must be installed
  on both client and server for corrected multiplayer bullet direction.

## 0.4.25

- Expands the shader cutaway face-preparation shell to two blocks, while retaining directional camera look-ahead.
- Increases cutaway hysteresis to a full two blocks before removed blocks return.
- Makes non-ADS third-person attacks target perfectly level along the character's facing direction.
- Keeps ADS shooting and targeting tied to the crosshair as before.

## 0.4.24

- Precompiles the real textured cavity-wall layer around the shader cutaway.
- Adds a one-block face-preparation look-ahead along the dominant direction of
  camera movement, so the next exposed wall is meshed before it is revealed.
- Changes the 20% multiplicative release overlap into a true 100% one-block
  release margin for the cutaway radius and shortening endpoint.
- Rebuilds sections only when the prepared face-cell membership changes.

## 0.4.23

- Keeps a separate cutaway-cell snapshot while shaders are active instead of
  clearing the only set used by Embeddium's face-visibility hooks.
- Rebuilds only sections whose cutaway membership changed, including their
  neighboring sections.
- Forces real textured faces on every surviving block adjacent to a
  shader-removed block, eliminating the missing-geometry X-ray cavity.
- Keeps full original block geometry in the shader/shadow mesh; only the main
  camera shader discards cutaway blocks.

## 0.4.22

- Submits every exposed cutaway boundary face through an opaque, no-cull,
  depth-writing shader-aware render pass.
- Prevents Oculus translucent sorting from dropping or reordering the dark
  cavity-wall faces.
- Designed for BlackForge Unbound 1.14's whole-block owner matching.

## 0.4.21

- Removes the player-height ground-collision exception from both the vanilla
  and Leawind camera paths.
- Camera collision bypass now remains active at and below the player's level.
- Keeps the dynamic player-to-camera occlusion region and 20% cutaway
  hysteresis introduced in 0.4.20.

## 0.4.20 - BlackForge TLC Camera

- Renames the displayed mod and build artifact to BlackForge TLC Camera.
- Replaces the fixed player-centered 3x3x3 Embeddium occlusion bubble with
  the minimal dynamic section box spanning the player and camera (normally
  no more than 2x2x2 / eight sections at the 12-block camera limit).
- Raises release-only cutaway hysteresis from 15% to 20% to reduce edge
  flicker without enlarging the active cutaway.
- Keeps the existing mod id for save, config, and integration compatibility.

## 0.4.19

- Uses the complete 0.4.15 implementation as its base.
- Treats the player-centered 3x3x3 group of Embeddium render sections as
  internally open for terrain-occlusion traversal.
- Preserves normal terrain occlusion outside those 27 sections and leaves
  frustum culling, render distance, block geometry, and cutaway geometry intact.

## 0.4.10

- Adds a five-tick fade-in and fade-out to each stone boundary face.
- Starts reversal from the face's current opacity to prevent visual jumps.
- Remains compatible with BlackForge Unbound 1.7.

## 0.4.9

- Adds 15% per-layer release hysteresis to reduce edge flicker.
- Adds 15% release retention to the last-obstruction endpoint.
- Replaces the outer black-concrete lining texture with vanilla stone.
- Remains compatible with BlackForge Unbound 1.7.

## 0.4.8

- Rebuilt directly from the v0.4.5 source archive.
- Restores original block textures for transitional transparency.
- Leaves every other v0.4.5 behavior unchanged.
- Uses BlackForge Unbound 1.7.

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
## 0.4.11

- Changed the complete former three-ring transition footprint to fully invisible.
- Added separate player-side endpoints: top and center near the player, left/right
  and lower corners one block back, and lower-center two blocks back.
- Restored terrain collision whenever the requested third-person camera position
  occupies the player's block level or any block below it.
## 0.4.12

- Replaced the artificial stone boundary with the real exposed block face
  rendered at zero packed light.
- Removed all cutaway and boundary opacity timing; activation and restoration
  are now immediate.
- Limited bottom-center to one outer layer and both lower corners to two outer
  layers. Side and upper wedges retain all three layers.
## 0.4.13

- Normal obstruction mode now listens only to the center and top-center rays
  and opens a constant compact 2x2 camera-to-player tube.
- Directional wedges now exist only while the camera occupies solid terrain.
- Eight samples around the 4x4 midpoint select the active wall-mode wedges.
- Wall-mode wedges taper from approximately 2x2 at both ends to 4x4 at the
  midpoint, with no additional outer transition rings.
- Replaceable vegetation such as grass and flowers cannot trigger either mode.
## 0.4.14

- Publishes the player's minimum cutaway block level to BlackForge Unbound.
- BlackForge Unbound 1.12 quantizes shader cutaway tests to owning block
  centers, matching the mod's whole-block removal instead of cutting circles
  through individual block faces.
## 0.4.15

- Extends the cutaway start to two blocks behind the camera.
- Removes the old player-height cutaway restriction; physical ground camera
  collision remains responsible for preventing underground viewing.
- Adds smoothed camera-motion prediction, capped at one block, so fast camera
  movement prepares the cutaway before the camera arrives.
- Keeps the current compact tube, conditional wall wedges, and zero-light
  camera-only boundary unchanged.
