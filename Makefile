LATEST_TAG=$(shell git describe --tags --abbrev=0)
VERSION=$(LATEST_TAG:=1)

build-docker:
	docker build \
		-t privado-core:$(VERSION) \
		--build-arg JAR_VERSION=$(VERSION) \
		--build-arg VERSION=$(VERSION) \
		.