# Native Library Build Requirements

## Overview

The FreeJ2ME project includes several native components that require compilation for different architectures. This document outlines the build requirements and dependencies for these native libraries.

## Directory Structure

```
cpp/
├── native/          # Native JNI libraries
│   ├── audio/       # Audio processing components
│   ├── m3g/         # Mobile 3D Graphics (JSR-184) implementation
│   ├── micro3d/     # Micro3D graphics utilities
│   └── include/     # Header files and JNI interfaces
└── sdl2/           # SDL2 interface for Miyoo Mini
```

## Native Library Components

### 1. Audio Library (libaudio.so)
**Purpose**: Handles audio processing and playback
**Dependencies**:
- SDL2 development libraries (`libsdl2-dev`)
- SDL2_mixer (`libsdl2-mixer-dev`)
- Standard C++ compiler (g++)

**Build Command**:
```bash
cd cpp/native
make -f Makefile.audio.x64
```

### 2. M3G Library (libm3g.so)
**Purpose**: Mobile 3D Graphics (JSR-184) implementation
**Dependencies**:
- OpenGL ES 1.1 (`libgles1-mesa-dev`)
- EGL (`libegl1-mesa-dev`)
- zlib (`zlib1g-dev`)
- Standard C/C++ compilers (gcc/g++)

**Build Command**:
```bash
cd cpp/native
make -f Makefile.m3g.x64
```

### 3. Micro3D Library (libmicro3d.so)
**Purpose**: Micro3D graphics utilities
**Dependencies**:
- OpenGL ES 2.0 (`libgles2-mesa-dev`)
- EGL (`libegl1-mesa-dev`)
- Standard C++ compiler (g++)

**Build Command**:
```bash
cd cpp/native
make -f Makefile.micro3d.x64
```

### 4. SDL2 Interface (sdl_interface)
**Purpose**: SDL2 interface for Miyoo Mini handheld device
**Dependencies**:
- SDL2 development libraries (`libsdl2-dev`)
- pthread library (usually included with system)
- Standard C++ compiler with C++11 support

**Build Command**:
```bash
cd cpp/sdl2
./run.sh  # For ARM cross-compilation
# Or for local compilation:
g++ -std=c++11 -lSDL2 -lpthread miyoomini.cpp cJSON.c -o sdl_interface
```

## Architecture Support

The project supports multiple architectures:
- **x64**: Intel/AMD 64-bit (development/desktop)
- **ARM32**: 32-bit ARM (older devices)
- **ARM64**: 64-bit ARM (newer devices, including Miyoo Mini)

Each architecture has its own Makefile variants:
- `Makefile.*.x64` - For x86_64 systems
- `Makefile.*.arm32` - For 32-bit ARM systems  
- `Makefile.*.arm64` - For 64-bit ARM systems

## Pre-compiled Libraries

The `jlib/` directory contains pre-compiled LWJGL (Lightweight Java Game Library) native libraries:
- `liblwjgl.so` - Core LWJGL library
- `liblwjgl_opengles.so` - OpenGL ES bindings

These are provided for:
- `x64Linux/` - Linux x86_64
- `arm32/` - 32-bit ARM
- `arm64/` - 64-bit ARM

## Installation Requirements (macOS/Linux)

### macOS (via Homebrew)
```bash
# SDL2 and related libraries
brew install sdl2 sdl2_mixer

# For OpenGL ES development (if needed)
# Note: macOS uses OpenGL instead of OpenGL ES
```

### Ubuntu/Debian
```bash
# SDL2 libraries
sudo apt-get install libsdl2-dev libsdl2-mixer-dev

# OpenGL ES libraries
sudo apt-get install libgles1-mesa-dev libgles2-mesa-dev libegl1-mesa-dev

# Additional dependencies
sudo apt-get install zlib1g-dev build-essential
```

### Fedora/RHEL
```bash
# SDL2 libraries
sudo dnf install SDL2-devel SDL2_mixer-devel

# OpenGL ES libraries
sudo dnf install mesa-libGLES-devel mesa-libEGL-devel

# Additional dependencies
sudo dnf install zlib-devel gcc gcc-c++
```

## Build Process Integration

The native libraries are separate from the main Java build process. The Java application loads these libraries at runtime using JNI (Java Native Interface).

**Important Notes**:
1. Native libraries must be compiled for the target architecture
2. The Java application expects libraries to be in the system library path or specified via `-Djava.library.path`
3. For development, you typically only need the x64 versions
4. For deployment to specific devices (like Miyoo Mini), you need the appropriate ARM versions

## Cross-compilation for Miyoo Mini

The Miyoo Mini uses ARM architecture. The `cpp/sdl2/run.sh` script shows cross-compilation setup:
```bash
arm-linux-gnueabihf-g++ -DDIRECTFB -std=c++11 -O3 -fno-strict-aliasing -fPIC \
  -marm -mtune=cortex-a7 -march=armv7ve+simd -mfpu=neon-vfpv4 -mfloat-abi=hard \
  -lSDL2 -lpthread miyoomini.cpp cJSON.c -o sdl_interface
```

This requires the ARM cross-compilation toolchain to be installed.