cmake_minimum_required(VERSION 3.24)
project(process_tracker_agent)

set(CMAKE_CXX_STANDARD 11)

add_executable(process_tracker process-tracker.cc)
