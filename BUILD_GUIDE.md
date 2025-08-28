# FreeJ2ME Build Guide

## Overview

This guide provides comprehensive instructions for building the FreeJ2ME project from source. FreeJ2ME is a J2ME (Java 2 Platform, Micro Edition) emulator that allows running mobile Java applications on desktop systems.

## Prerequisites

### System Requirements

- **Operating System**: macOS, Linux, or Windows
- **Java Development Kit**: OpenJDK 8 or higher
- **Apache Ant**: Version 1.9 or higher
- **Native Libraries**: SDL2 and SDL2_mixer (for audio support)

### macOS Installation

#### Install Java Development Kit
```bash
# Check if Java is already installed
java -version

# Install OpenJDK via Homebrew (recommended)
brew install openjdk

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk"
```

#### Install Apache Ant
```bash
# Check if Ant is already installed
ant -version

# Install Ant via Homebrew
brew install ant
```

#### Install Native Dependencies (Optional - for audio support)
```bash
# Install SDL2 libraries for native audio
brew install sdl2 sdl2_mixer

# For cross-compilation (if needed)
brew install gcc-arm-embedded
```

### Linux Installation

#### Ubuntu/Debian
```bash
# Install Java Development Kit
sudo apt update
sudo apt install openjdk-11-jdk

# Install Apache Ant
sudo apt install ant

# Install native dependencies
sudo apt install libsdl2-dev libsdl2-mixer-dev

# For cross-compilation
sudo apt install gcc-arm-linux-gnueabihf gcc-aarch64-linux-gnu
```

#### CentOS/RHEL/Fedora
```bash
# Install Java Development Kit
sudo dnf install java-11-openjdk-devel

# Install Apache Ant
sudo dnf install ant

# Install native dependencies
sudo dnf install SDL2-devel SDL2_mixer-devel
```

## Project Structure

```
freej2me/
├── build.xml              # Ant build configuration
├── src/                   # Java source code
│   ├── javax/             # J2ME API implementation
│   ├── org/               # Core emulator code
│   └── ...
├── cpp/                   # Native C++ code
│   └── native/            # Native libraries
│       ├── audio/         # Audio system implementation
│       ├── m3g/           # 3D graphics support
│       └── micro3d/       # Micro3D implementation
├── lib/                   # External dependencies
│   └── jsr305-3.0.2.jar   # JSR-305 annotations
├── resources/             # Resource files
├── META-INF/              # Build metadata
├── build/                 # Build output directory
└── jlib/                  # Native library binaries
```

## Building the Project

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd freej2me
   ```

2. **Build the JAR file**
   ```bash
   ant
   ```

3. **Locate the output**
   ```bash
   ls -la build/freej2me-sdl.jar
   ```

### Detailed Build Process

#### Step 1: Verify Prerequisites
```bash
# Check Java installation
java -version
javac -version

# Check Ant installation
ant -version

# Verify project structure
ls -la build.xml src/ lib/
```

#### Step 2: Clean Previous Builds (Optional)
```bash
# Remove previous build artifacts
rm -rf build/
```

#### Step 3: Compile Java Sources
```bash
# Run the Ant build
ant

# Or run with verbose output
ant -v
```

#### Step 4: Verify Build Output
```bash
# Check that JAR was created
ls -la build/freej2me-sdl.jar

# Verify JAR contents
jar -tf build/freej2me-sdl.jar | head -20
```

### Build Configuration Details

The build process is controlled by `build.xml` with the following key settings:

- **Source Directory**: `src/`
- **Output Directory**: `build/classes/`
- **JAR Output**: `build/freej2me-sdl.jar`
- **Main Class**: `org.recompile.freej2me.Anbu`
- **Dependencies**: `lib/jsr305-3.0.2.jar`

#### Compiler Settings
- **Debug**: Disabled for production builds
- **Warnings**: Enabled for unchecked and deprecation warnings
- **Classpath**: Includes all JARs in `lib/` directory

## Native Library Compilation (Advanced)

### Audio Library Compilation

The project includes native audio libraries for enhanced audio support:

#### x86-64 Linux
```bash
cd cpp/native
make -f Makefile.audio.x64
```

#### ARM64 (AArch64)
```bash
cd cpp/native
make -f Makefile.audio.arm64
```

#### ARM32
```bash
cd cpp/native
make -f Makefile.audio.arm32
```

### 3D Graphics Libraries

#### M3G (Mobile 3D Graphics)
```bash
cd cpp/native
make -f Makefile.m3g.x64    # For x86-64
make -f Makefile.m3g.arm64  # For ARM64
make -f Makefile.m3g.arm32  # For ARM32
```

#### Micro3D
```bash
cd cpp/native
make -f Makefile.micro3d.x64    # For x86-64
make -f Makefile.micro3d.arm64  # For ARM64
make -f Makefile.micro3d.arm32  # For ARM32
```

## Running the Emulator

### Basic Usage
```bash
# Run with a J2ME application
java -jar build/freej2me-sdl.jar path/to/application.jar

# Run with specific options
java -Xmx512m -jar build/freej2me-sdl.jar application.jar
```

### Configuration Options
- **Memory**: Use `-Xmx` to set maximum heap size
- **Audio**: Native audio libraries must be in library path
- **Display**: Supports various screen resolutions and orientations

## Troubleshooting

### Common Build Issues

#### Java Version Compatibility
**Problem**: Compilation errors due to Java version mismatch
```
Solution:
1. Verify Java version: java -version
2. Ensure JDK 8 or higher is installed
3. Set JAVA_HOME environment variable
```

#### Missing Dependencies
**Problem**: ClassNotFoundException or compilation errors
```
Solution:
1. Verify lib/jsr305-3.0.2.jar exists
2. Check build.xml classpath configuration
3. Ensure all required JARs are in lib/ directory
```

#### Ant Build Failures
**Problem**: Ant cannot find build.xml or fails to compile
```
Solution:
1. Verify you're in the project root directory
2. Check build.xml syntax and permissions
3. Ensure src/ directory structure is intact
```

#### Native Library Issues
**Problem**: Native libraries fail to load at runtime
```
Solution:
1. Verify SDL2 libraries are installed
2. Check library path configuration
3. Compile native libraries for your architecture
```

### Build Environment Verification

#### Check Java Environment
```bash
# Verify Java installation
which java
which javac
echo $JAVA_HOME

# Test compilation
javac -version
```

#### Check Ant Environment
```bash
# Verify Ant installation
which ant
ant -version

# Test Ant with simple build
ant -f build.xml
```

#### Verify Dependencies
```bash
# Check required files exist
ls -la build.xml
ls -la src/org/recompile/freej2me/Anbu.java
ls -la lib/jsr305-3.0.2.jar
ls -la META-INF/freej2me-build.version
```

### Performance Optimization

#### Build Performance
- Use `-j` flag with make for parallel compilation of native libraries
- Increase Java heap size for large projects: `export ANT_OPTS="-Xmx1g"`
- Use SSD storage for faster I/O during compilation

#### Runtime Performance
- Allocate sufficient heap memory: `-Xmx512m` or higher
- Use native libraries for better audio/graphics performance
- Enable JIT compilation optimizations

## Development Workflow

### Incremental Development
1. **Make code changes** in `src/` directory
2. **Rebuild**: `ant`
3. **Test**: Run the updated JAR file
4. **Debug**: Use Java debugging tools as needed

### Code Quality
- Enable all compiler warnings in build.xml
- Use static analysis tools for code quality
- Follow Java coding standards and conventions

### Version Management
- Build version is stored in `META-INF/freej2me-build.version`
- Current version: FreeJ2ME-SDL-2.4.7
- Update version for releases and major changes

## Advanced Configuration

### Custom Build Targets
You can extend `build.xml` with custom targets:

```xml
<target name="clean">
    <delete dir="build"/>
</target>

<target name="test" depends="compile">
    <!-- Add test execution here -->
</target>
```

### Cross-Platform Builds
- Use appropriate native library versions for target platform
- Adjust classpath and library paths for different operating systems
- Test on target platforms before deployment

### Integration with IDEs
- **Eclipse**: Import as existing project, configure build path
- **IntelliJ IDEA**: Open as Ant project, configure SDK
- **VS Code**: Use Java extension pack, configure tasks.json

This build guide provides comprehensive instructions for successfully building and running the FreeJ2ME project across different platforms and configurations.