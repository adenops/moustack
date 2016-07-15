PACKER_LOG       ?= 0
PACKER_CACHE_DIR ?= ${HOME}/packer_cache
PACKER_PARAMS    ?=

all: usage

usage:
	@echo "make [usage|build|build-ubuntu-1604]"

build: build-ubuntu-1604

build-ubuntu-1604:
	packer build $(PACKER_PARAMS) ubuntu-16.04.json

.PHONY: all usage build build-ubuntu-1604 clean
