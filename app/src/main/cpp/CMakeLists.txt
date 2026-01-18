cmake_minimum_required(VERSION 3.18.1)

project(ksec)

add_library( # Nome libreria
        ksec
        SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/ksec.c
)

find_library( # libreria log di Android
        log-lib
        log )

target_link_libraries( # link
        ksec
        ${log-lib} )
