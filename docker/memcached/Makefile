# we use 'sinclude' instead of 'include' to avoid make to fail if '.local.mk' does not exists
sinclude \
	$(realpath $(dir $(MAKEFILE_LIST))/../..)/.local.mk \
	$(realpath $(dir $(MAKEFILE_LIST))/../..)/include/make/*.mk

DOCKER_IMAGE      = $(DOCKER_IMAGE_PREFIX)memcached
DOCKER_LABEL_NAME = $(DOCKER_LABEL_NAME_PREFIX)Memcached
