# fail build if specified variable is not set
define check_defined
	test -n "$($(1))" && { \
		exit 0; \
	} || { \
		/bin/echo -e "\n\033[1;31mVariable $1 must be defined.\033[0m\n"; \
		exit 1; \
	}
endef

# display colorized log linw
define logme
	/bin/echo -e "\n\033[1;32m--- [$(notdir $(@))] $(1) ---\033[0m\n"
endef
