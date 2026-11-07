# Night drive source

`launcher/app/src/main/assets/home/nightdrive.js` is generated from these files. Edit here, then
regenerate:

```bash
python3 port.py block.js photos.js ../../launcher/app/src/main/assets/home/nightdrive.js
```

- `block.js`: the road, scenery, cars, driving, smoke and the focus city. World units are dock pixels
  (x 0..1152, y 0..150 inside a window). `port.py` wraps it into `NightDrive.start()` for the Home page.
- `cars/`: the five cartoon cars as 640 px PNGs, the masters. `recolor.py` repaints them
  (`uv run --with pillow --with numpy python recolor.py <in> <out>`).
- `make_cars.py`: makes the 320 px WebP files the Home page loads (`assets/home/cars/`) and writes
  `photos.js`: each car's wheel positions, roof and rear-deck height, and length.
- The ink outline and the tail view's height are scaled from those numbers, so a car of any size lines up.
- `ytbend.py`: generates the YouTube hairpins (round, with eased curvature) as control points for `RP`
  in `block.js` (`uv run --with numpy python ytbend.py`).
- `sim.mjs`: runs the road and driving code in Node and prints the car's position, curvature, facing and
  drift over time (`node sim.mjs block.js out.json`). Useful to check a change before looking at it on a TV.

Preview: serve `launcher/app/src/main/assets/` (`python3 -m http.server`) and open `home/index.html`
at 960×540. The page stands in sample data when it isn't running inside the launcher.
