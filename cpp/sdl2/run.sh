arm-linux-gnueabihf-g++ -DDIRECTFB -std=c++11 -O3 -fno-strict-aliasing -fPIC -marm -mtune=cortex-a7 -march=armv7ve+simd -mfpu=neon-vfpv4 -mfloat-abi=hard -lSDL2 -lpthread  miyoomini.cpp cJSON.c -o sdl_interface

