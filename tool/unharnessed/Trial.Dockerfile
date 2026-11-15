FROM ubuntu:22.04
RUN apt-get update -qq && apt-get install -y -qq python3 ca-certificates && rm -rf /var/lib/apt/lists/*
