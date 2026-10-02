# Layers

Every schematic, imported or new, is a layer. Layers can be shown or hidden, locked, ghosted, renamed, reordered, duplicated and merged.

## Managing layers

| Key | Action |
|---|---|
| Ctrl+Shift+N / Ctrl+D / Ctrl+M | New / duplicate / merge |
| F2 | Rename the active layer |
| H / Alt+H / Shift+H | Hide or show / show every layer / ghost |
| L | Lock or unlock |
| Shift+Delete | Delete the selected layers |
| [ / ] | Make the layer below / above active (with the brush or eraser, these change the brush size instead) |

Click a layer in the Layers panel to select it. Shift adds to the selection.

## Moving layers

- **Ctrl+wheel** moves the layer along the axis of the bounding-box side under the mouse.
- **Ctrl+Shift+wheel** moves it up or down.
- **Shift+wheel** does the same as Ctrl+wheel, outside Build mode.
- **Arrow keys** move it. Hold **Tab** for bigger steps.
- Or use the Move gizmo (**G**). See [Brushes, selections and BlockEdit](Brushes-Selections-And-BlockEdit).

## Turning layers

Layers turn in quarter turns. **Alt+wheel** turns the layer by the bounding-box side under the mouse: a spin (top or bottom) or a flip (a side). In Build mode, use **Shift+wheel** for this, because Alt+wheel changes the hotbar slot there.

You can also use the Rotate gizmo (**R**).

## Slice view

The slice view steps through Y levels.

- **PgUp** / **PgDn** step up and down.
- **Insert** shows a single level.

## Placing an import

When you import a file, it follows the mouse. **Click** or **Enter** places it, **R** rotates it and **Esc** cancels. See [Import and export](Import-And-Export).

## Reference images

Reference images are listed above the layers. See [Reference images](Reference-Images).
