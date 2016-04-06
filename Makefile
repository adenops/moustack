# base containers
BASE_IMAGES := \
	docker/ubuntu-base \
	docker/openstack-base

# list all containers expect the base ones
CONTAINER_IMAGES := \
	$(shell find docker/ -mindepth 1 -maxdepth 1 -type d ! -name '*-base' | sort -n)

all: usage
	@echo BASE_IMAGES=$(BASE_IMAGES)
	@echo CONTAINER_IMAGES=$(CONTAINER_IMAGES)

usage:
	@echo "make [usage|build|clean|distclean|release]"

docker/ubuntu-base:
	$(MAKE) -C $@ $(MAKECMDGOALS)

docker/openstack-base: docker/ubuntu-base
	$(MAKE) -C $@ $(MAKECMDGOALS)

$(CONTAINER_IMAGES): docker/openstack-base
	$(MAKE) -C $@ $(MAKECMDGOALS)

build clean distclean release: $(CONTAINER_IMAGES)

.PHONY: all usage build clean distclean release $(BASE_IMAGES) $(CONTAINER_IMAGES)
