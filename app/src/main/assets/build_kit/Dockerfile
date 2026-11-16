FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake ninja-build meson git curl ca-certificates pkg-config \
    cargo golang nodejs npm default-jdk maven python3-pip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /work
COPY build.sh .
CMD ["sh", "build.sh"]
