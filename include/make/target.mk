TRIGGER_DIR      = .cache
TRIGGER_BUILD    = $(TRIGGER_DIR)/build
TRIGGER_RELEASE  = $(TRIGGER_DIR)/release


all: usage


usage:
	@echo "make [usage|build|clean|distclean|release]"


clean:
	rm -rvf $(TRIGGER_BUILD)
	rm -rvf $(TRIGGER_RELEASE)


distclean: clean
	@$(call check_defined,DOCKER_IMAGE)
	@$(call check_defined,DOCKER_TAG)

	@$(call logme,"Remove image $(DOCKER_IMAGE):$(DOCKER_TAG)")
	docker rmi -f $(DOCKER_IMAGE):$(DOCKER_TAG) || true


$(TRIGGER_BUILD):
	@$(call check_defined,DOCKER_IMAGE)
	@$(call check_defined,DOCKER_TAG)

	@$(call logme,"Build image $(DOCKER_IMAGE)")
	docker build --file Dockerfile --tag $(DOCKER_IMAGE):$(DOCKER_TAG) $(DOCKER_BUILD_ARGS) --no-cache=$(DOCKER_NO_CACHE) --force-rm .

	@mkdir -p $(TRIGGER_DIR)
	@touch $(TRIGGER_BUILD)


build: $(TRIGGER_BUILD)


$(TRIGGER_RELEASE):
	@$(call check_defined,DOCKER_IMAGE)
	@$(call check_defined,DOCKER_TAG)

	@$(call logme,"Push image $(DOCKER_IMAGE):$(DOCKER_TAG)")
	docker push $(DOCKER_IMAGE):$(DOCKER_TAG)

	@mkdir -p $(TRIGGER_DIR)
	@touch $(TRIGGER_RELEASE)


release: build $(TRIGGER_RELEASE)

.NOTPARALLEL: clean build

.PHONY: all usage build clean distclean release
