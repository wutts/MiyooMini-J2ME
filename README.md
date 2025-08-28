# J2ME Emulator
A J2ME emulator based on FreeJ2ME, supporting 3D games for Miyoo Mini and Mini Plus.

## Compilation Instructions
To compile the front end, which utilizes SDL2, you need to compile the `sdl_interface`.

For sound support, you need to compile `libaudio`, as it requires SDL2_mixer.

For 3D game support:
- To support M3G, compile `libm3g` (dependencies: EGL, GLES1).
- For Mascot Capsule v3 support, compile `libmicro3d` (dependencies: EGL, GLES2).

Comprehensive build guide: [BUILD GUIDE](https://github.com/wutts/MiyooMini-J2ME/blob/main/BUILD_GUIDE.md)

## Adding the Emulator to Onion OS
1. Move the J2ME folder to the Emu folder on your SD Card.
2. To add J2ME games:
   - Inside the J2ME folder, you will find a `rom` folder containing the following directories: `128128`, `176208`, `240320`, `320240`, `640360`. These refer to the game resolutions.
   - Place downloaded J2ME files into the corresponding folder according to their resolution.

## Customization
- **Key Maps:** Customize the key mapping by modifying the `keymap.cfg` file.
- **Fonts:** To change the font, replace the `font.ttf` file.


View [KEY MAPS](https://github.com/wutts/J2ME-Miyoo-Mini/blob/main/KEYMAP.md)
