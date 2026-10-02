# Reference images

Put pictures in the scene and build from them, like Blender's reference images. This used to be the Reference Planes plugin. It is built in now.

## Adding one

- Click the picture button at the top of the **Layers** panel.
- Drop a PNG, JPEG, GIF or BMP on the window.
- Use **Import**.

You can also give **Add a reference image** a key in **Settings > Keybinds**.

The picture goes at the point the camera orbits around, facing you. If you add it in an orthographic axis view (numpad 1 / 3 / 7, or a view cube face), it faces that view straight on and shows only there.

## Starting values

The **Reference images** page of **Settings** sets how new pictures start out: height in blocks, opacity, how blocks cover them, and whether one added in an axis view shows only in that view.

## Working with them

They are listed above the layers with a **REFERENCE** tag.

- Click to select, double-click to rename. The eye and lock hide or lock.
- With one selected, **G** / **R** / **S** move, turn and scale it (Ctrl snaps).
- **Delete** removes it.
- Every change can be undone.

## Right-click menu

Right-click a picture, in the view or in Layers, for:

- **Properties...**: position, rotation, scale, UV offset and scale, opacity, with resets.
- **Opacity**.
- **Show in**: all views, orthographic views, or one of the six.
- **Draw**: behind blocks, in the scene, or in front of blocks.
- Flips, aligning it to the view, and **Replace picture...**.

## Saving

Pictures are saved inside the `.bdproj`, at full size, so a project opens with its references on another PC. Projects made with the Reference Planes plugin open with their pictures.

If you still have the Reference Planes plugin, BlockDesigner no longer loads it. It renames the jar to end in `.retired` and tells you once, and keeps the plugin's settings and key.
