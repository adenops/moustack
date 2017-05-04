# the following variables can be overridden by creating a '.local.mk' file
# in repository root directory, or by passing them directly to 'make' command
# line

# 'release' target will push images to this docker registry
DOCKER_REGISTRY ?=

# this string will be appended at the beginning of each docker image name
DOCKER_IMAGE_PREFIX ?= $(shell test -n "${DOCKER_REGISTRY}" && echo "${DOCKER_REGISTRY}/")moustack/

# can be used to define an HTTP proxy for APT, for example
# DOCKER_BUILD_ARGS="--build-arg http_proxy=http://my.proxy:1080"
DOCKER_BUILD_ARGS ?=

# do not use cache if set to true
DOCKER_NO_CACHE ?= false

# docker labels metadata (keep trailing spaces)
DOCKER_LABEL_NAME_PREFIX    ?= Moustack - 
DOCKER_LABEL_DESCRIPTION    ?= A KISS deployment framework for Openstack clouds
DOCKER_LABEL_URL            ?= http://www.moustack.org
DOCKER_LABEL_VENDOR         ?= Adenops Consultants Informatique Inc.
DOCKER_LABEL_VCS_URL        ?= https://github.com/adenops/moustack.git
DOCKER_LABEL_VCS_REF        ?= $(shell git rev-parse HEAD)
DOCKER_LABEL_SCHEMA_VERSION ?= 1.0
