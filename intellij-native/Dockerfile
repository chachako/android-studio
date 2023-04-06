FROM ubuntu:14.04

RUN apt-get update && apt-get install -y \
    make \
    cmake \
    xz-utils \
    zip unzip \
    binutils \
    clang \
    musl-tools

RUN useradd -ms /bin/bash builder
USER builder
WORKDIR /home/builder