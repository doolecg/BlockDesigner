# Controls and keybinds

These are the default keys. **Every keyboard shortcut can be rebound in Settings > Keybinds** (Ctrl+,). The app always shows your current keys: press **F1** (or Alt+K), or click the command button in the viewport, for the list.

Single-letter keys don't fire while you're typing in a text box, and while flying W A S D, Space, Shift and Ctrl belong to flight.

## Modes and tools

| Key | Action |
|---|---|
| 1 | View mode: look around only |
| 2 | Select mode |
| 3 | Build mode on / off (also while flying) |
| G / R / S | Move / Rotate / Scale tool (gizmos for the selected layers, or the selected blocks) |
| 4 / 5 | Brush / Eraser |
| Esc | Leave Build mode (in Build mode the number keys pick hotbar slots) |
| Esc | Cancel a drag or placement, clear the selection, stop flying, then back to Select |

## Camera

| Key | Action |
|---|---|
| Middle-drag | Orbit around the point under the mouse |
| Shift+middle-drag | Pan |
| Alt+middle-drag | Swing to the next orthographic view in that direction |
| Wheel | Zoom |
| F | Focus: frame the selected blocks, or else the active layer |
| Home | Frame everything |
| C | Creative flight on / off |
| P / O | Perspective / orthographic |
| Numpad 1 / 3 / 7 | Front / right / top (Ctrl: back / left / bottom) |
| Numpad 9 / 5 | Opposite side / toggle perspective and orthographic |
| Numpad 2 4 6 8 / . | Orbit 15 degrees / frame the active layer |
| View cube | Click a face for that view (again for the opposite side), drag to orbit |

## Flying

| Key | Action |
|---|---|
| W A S D | Move (W and S follow where you look) |
| Space / Shift | Up / down |
| Ctrl | Sprint |
| Wheel | Next / previous hotbar slot |
| C / Esc | Stop flying |

## Build mode and hotbar

| Key | Action |
|---|---|
| Left-click | Break (hold to repeat) |
| Right-click | Place (hold to repeat), oriented like Minecraft |
| Middle-click | Pick the block into the hotbar |
| Hold Alt | Easy Build wheel: point at a shape and let go, then right-drag to draw it |
| M / Shift+M | Symmetry settings / put the symmetry centre on the aimed block |
| Shift+X | Replace mode: right-click swaps the aimed block and keeps its facing |
| Shift+Z | Shuffle mode: place random blocks from the hotbar |
| 1 - 9 (Build mode) | Hold a hotbar slot |
| 1 - 9 over a palette block | Put that block in that hotbar slot, like Minecraft's creative inventory |
| Delete | Empty the held hotbar slot |
| Shift+C | Clear the hotbar |

## Brush and Eraser

| Key | Action |
|---|---|
| Drag | Paint with the brush mode (Eraser: remove blocks) |
| Right-drag | Smooth |
| Alt+1 ... Alt+0 | Draw, Erase, Smooth, Erode, Fill, Pinch, Raise, Lower, Flatten, Slope |
| Shift+right-click | Brush settings: mode, size, strength, shape |
| [ / ] (or - / =) | Smaller / bigger brush (1-16) |
| , / . | Weaker / stronger brush (1-5) |
| Shift+drag / Ctrl+drag | Smooth / inverse mode (not while flying) |

## Select mode and BlockEdit

| Key | Action |
|---|---|
| Left-click / right-click | Set pos1 / pos2; the box's blocks become the selection |
| Shift+click / Ctrl+click | Add a block to / remove it from the selection |
| Drag | Marquee select; pos1 and pos2 are set to the corners of the box around the selection |
| Shift+right-click or Menu key | Context menu |
| Alt+T | Select or replace by type |
| Ctrl+A / Alt+A | Select every block of the active layer / deselect |
| Ctrl+C / Ctrl+X | Copy / cut the selection (shares the clipboard with /copy and /paste) |
| Ctrl+V | Paste: the copy follows the mouse, click to put it in the active layer (it stays selected), right-click or Esc cancels |
| Ctrl+J / Ctrl+Shift+J | Copy / move the selection to a new layer |
| Ctrl+R | Fill the selection with the held block |
| Delete | Delete the selected blocks |
| Esc | Clear the region and selection |
| T or / | Command line, like Minecraft's chat (Tab completes, up and down for history) |

The commands are listed on [Brushes, selections and BlockEdit](Brushes-Selections-And-BlockEdit).

## Move, Rotate and Scale tools

| Action | Result |
|---|---|
| Drag an arrow / square / the centre | Move along an axis / in a plane / freely |
| Drag a ring | Turn in 90 degree steps |
| Scale: drag a square handle / the centre | Stretch along that axis / scale evenly (whole blocks, nearest neighbour) |
| Click a layer | Select it (Shift adds) |
| Esc / right-click | Cancel the drag |

## Layers

| Key | Action |
|---|---|
| Ctrl+wheel | Move along the axis of the layer's bounding-box side under the mouse |
| Ctrl+Shift+wheel | Up / down |
| Shift+wheel (outside Build mode) | Same as Ctrl+wheel |
| Arrow keys, hold Tab | Move, bigger steps |
| Alt+wheel (Shift+wheel in Build mode) | Turn by the bounding-box side under the mouse: spin (top or bottom) or flip (a side) |
| Alt+wheel in Build mode | Next / previous hotbar slot (the wheel still zooms) |
| [ / ] | Make the layer below / above active (with the brush or eraser: brush size) |
| Ctrl+Shift+N / Ctrl+D / Ctrl+M | New / duplicate / merge |
| F2 | Rename the active layer |
| H / Alt+H / Shift+H | Hide or show / show every layer / ghost |
| L | Lock or unlock |
| Shift+Delete | Delete the selected layers |
| PgUp / PgDn, Insert | Slice view: step through Y levels, single level |
| Click / Enter, R, Esc | Placing an import: place, rotate, cancel |

## File, edit and view

| Key | Action |
|---|---|
| Ctrl+N / Ctrl+O / Ctrl+I | New / open / import |
| Ctrl+S / Ctrl+Shift+S | Save / save as |
| Ctrl+E / Ctrl+Shift+E | Export schematic / worldgen data pack |
| Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z) | Undo / redo |
| Ctrl+F | Search blocks |
| Ctrl+, | Settings |
| N | Viewport settings |
| Alt+G | Ground grid on / off |
| F1 / Alt+K | Keyboard shortcuts |
| Shift+F1 | Key hints on / off |
| F11 | Full screen |
