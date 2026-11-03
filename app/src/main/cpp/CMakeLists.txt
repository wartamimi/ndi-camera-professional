cmake_minimum_required(VERSION 3.22.1)
project(lcscam LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(NDI_VALIDATION_BUILD "Build without the proprietary NDI SDK (CI validation only)" OFF)
set(NDI_SDK_DIR "" CACHE PATH "Root of the official NDI SDK for Android")

add_compile_options(-Wall -Wextra -Werror=return-type -O3 -fvisibility=hidden)

# ---- Media core: pure C++ image/audio processing, no NDI dependency -----------------------
add_library(mediacore STATIC
        camera/frame_converter.cpp
        camera/frame_analyzer.cpp
        camera/watermark.cpp
        audio/audio_utils.cpp)
target_include_directories(mediacore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# ---- libndicam.so: always built; overlays analysis + audio DSP JNI --------------------------
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
add_library(ndicam SHARED jni/jni_core.cpp)
target_link_libraries(ndicam PRIVATE mediacore ${log-lib} ${jnigraphics-lib})

# ---- libndibridge.so: JNI bridge to the official NDI SDK ------------------------------------
if (NOT NDI_VALIDATION_BUILD)
    set(NDI_INCLUDE_DIR "${NDI_SDK_DIR}/include")
    set(NDI_LIBRARY "${NDI_SDK_DIR}/lib/${ANDROID_ABI}/libndi.so")
    if (NOT EXISTS "${NDI_INCLUDE_DIR}/Processing.NDI.Lib.h")
        message(FATAL_ERROR "NDI SDK header not found at ${NDI_INCLUDE_DIR}/Processing.NDI.Lib.h. See docs/NDI_SDK_SETUP.md")
    endif ()
    if (NOT EXISTS "${NDI_LIBRARY}")
        message(FATAL_ERROR "NDI SDK library not found for ABI ${ANDROID_ABI}: ${NDI_LIBRARY}. See docs/NDI_SDK_SETUP.md")
    endif ()

    add_library(ndi SHARED IMPORTED)
    set_target_properties(ndi PROPERTIES IMPORTED_LOCATION "${NDI_LIBRARY}")

    add_library(ndibridge SHARED
            ndi/ndi_sender.cpp
            ndi/ndi_finder.cpp
            jni/jni_ndi.cpp)
    target_include_directories(ndibridge PRIVATE ${NDI_INCLUDE_DIR})
    target_link_libraries(ndibridge PRIVATE mediacore ndi ${log-lib})
else ()
    message(WARNING "NDI_VALIDATION_BUILD=ON: libndibridge.so is NOT built. The APK cannot send NDI.")
endif ()
