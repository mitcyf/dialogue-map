# DialogueMap

DialogueMap turns user-supplied artwork into a vanilla Minecraft map dialog. It is a Paper
1.21.11 plugin and generated resource pack; it does not generate terrain or read chunks.

## Inputs

Place these files in `plugins/DialogueMap/assets/`:

```text
map.png                 Your map image. One source pixel represents one world block at zoom 1.
ui.png                  The complete UI artwork.
player.png              A 16×16 icon which points north/up.
buttons/<name>.png      Optional clickable button images.
```

`ui.png` must contain one fully transparent, axis-aligned map opening. The builder finds its
largest fully transparent rectangle and uses that rectangle as the map viewport. The UI artwork
is drawn over the map, so its frame, decorations, and any transparent button holes remain exactly
as authored.

Set the world coordinate represented by the top-left map pixel and define optional buttons in
`config.yml`:

```yaml
map:
  top-left: {x: -2048, y: -2048} # y is Minecraft Z on this 2D map

buttons:
  - id: pan-north
    texture: assets/buttons/up.png
    x: 216
    y: 0
    action: /dialoguemap pan north
```

Buttons may send any command you deliberately place in this trusted server configuration.
Their image dimensions define their click areas.

## Workflow

1. Put or replace the input files.
2. Run `/dialoguemap reload-assets` to validate paths, image dimensions, and the detected aperture.
3. Run `/dialoguemap build` to generate the pack and send its new hash to online players.
4. Run `/dialoguemap open` to open the map. `pan`, `zoom`, and `close` are available as commands
   and can be assigned to buttons.

The build output is `plugins/DialogueMap/generated-resource-pack/`. It contains source copies,
zoom levels, bitmap-font textures, generated shader files, a manifest, and `dialogue-map.zip`.

## Architecture

`MapDefinition` is the only user-facing contract. Both `MapScreenFactory` and
`ResourcePackWriter` consume it, ensuring that map origin, aperture, zoom levels, button bounds,
and page geometry have one source of truth.

The client composition protocol remains intentionally internal: the runtime supplies a 3×3 set of
map pages at the aperture origin and generated text shaders crop/clip them. The red and green text
colour bytes carry crop coordinates; blue identifies one of nine compositor slots. The player icon
has a separate documented shader payload. No user asset or layout decision requires changing those
bytes or editing GLSL.

## Development

```sh
./gradlew test
./gradlew deployPlugin
```

The project targets Java 21 and Paper 1.21.11.
