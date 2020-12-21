VERSION=HEAD-SNAPSHOT

.PHONY: build test release release-local

# This Makefile lets you build the project without Bazel.
# Internal users: please note that your build must also pass with Bazel.

build:
	sbt +clean +compile

test:
	sbt +test

release: test
	sbt 'set version := "$(VERSION)"' +publishSigned

snapshot:
	# TODO: some way to control if a package gets snapshots from the build chain or not.
    #  here we add a 'snapshot' target that does nothing to effectively disable snapshot releases.
	@echo "No snapshoting for this package."

# Do a +publishLocal if you need it in your local .ivy2 repo
release-local:
	sbt 'set version := "$(VERSION)"' +publishM2 +publishLocal
