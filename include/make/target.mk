TRIGGER_DIR      = .cache
TRIGGER_BUILD    = $(TRIGGER_DIR)/build
TRIGGER_RELEASE  = $(TRIGGER_DIR)/release
DOCKERFILE_TEMP  = .dockerfile.generated


all: usage


usage:
	@echo "make [usage|build|clean|distclean|release]"


clean:
	rm -rvf $(TRIGGER_BUILD)
	rm -rvf $(TRIGGER_RELEASE)
	rm -rvf $(DOCKERFILE_TEMP)


distclean: clean
	@$(call check_defined,DOCKER_IMAGE)
	@$(call check_defined,DOCKER_TAG)

	@$(call logme,"Remove image $(DOCKER_IMAGE):$(DOCKER_TAG)")
	docker rmi -f $(DOCKER_IMAGE):$(DOCKER_TAG) || true


$(TRIGGER_BUILD):
	@$(call check_defined,DOCKER_IMAGE)
	@$(call check_defined,DOCKER_TAG)

	@$(call logme,"Generate dockerfile")
	sed -r "s#^FROM moustack/([^:]+).*#FROM $(DOCKER_IMAGE_PREFIX)\1:$(DOCKER_TAG)#" Dockerfile >$(DOCKERFILE_TEMP)

	@$(call logme,"Build image $(DOCKER_IMAGE):$(DOCKER_TAG)")
	docker build \
		--file $(DOCKERFILE_TEMP) \
		--tag $(DOCKER_IMAGE):$(DOCKER_TAG) \
		$(DOCKER_BUILD_ARGS) \
		--no-cache=$(DOCKER_NO_CACHE) \
		--force-rm \
		--label "org.label-schema.name=$(DOCKER_LABEL_NAME)" \
		--label "org.label-schema.description=$(DOCKER_LABEL_DESCRIPTION)" \
		--label "org.label-schema.url=$(DOCKER_LABEL_URL)" \
		--label "org.label-schema.vendor=$(DOCKER_LABEL_VENDOR)" \
		--label "org.label-schema.vcs-url=$(DOCKER_LABEL_VCS_URL)" \
		--label "org.label-schema.vcs-ref=$(DOCKER_LABEL_VCS_REF)" \
		--label "org.label-schema.schema-version=$(DOCKER_LABEL_SCHEMA_VERSION)" \
		.

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
