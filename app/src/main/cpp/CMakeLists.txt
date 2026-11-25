cmake_minimum_required(VERSION 3.22.1)
project(dtmp3 C)
add_library(dtmp3 SHARED mp3.c)
target_compile_options(dtmp3 PRIVATE -O3)
