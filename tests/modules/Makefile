OK   = "\033[32mOK\033[0m"
FAIL = "\033[1;31m*** FAIL ***\033[0m"
CHECK = && echo $(OK) || (echo $(FAIL); exit 1)

FZ = ../../bin/fz

.PHONY: all sources_only a ab b clean
all: sources_only a ab

# run example using all sources directly
sources_only:
	$(FZ) -sourceDirs=./src_a,./src_b,./src                          test_modules $(CHECK)

# compile src_a into a.fum
modules/a.fum:
	mkdir -p $(@D)
	$(FZ) -sourceDirs=./src_a                                        -saveLib=$@  $(CHECK)

# run example using a.fum and sources from src_b and src
a: modules/a.fum
	$(FZ) -sourceDirs=./src_b,./src -modules=a   -moduleDirs=modules test_modules $(CHECK)

# compile src_b into b.fum
modules/b.fum: modules/a.fum
	mkdir -p $(@D)
	$(FZ) -sourceDirs=./src_b       -modules=a   -moduleDirs=modules -saveLib=$@  $(CHECK)

# run example using a.fum and b.fum and sources src
ab: modules/a.fum modules/b.fum
	$(FZ) -sourceDirs=./src         -modules=a,b -moduleDirs=modules test_modules $(CHECK)

clean:
	rm -rf modules *~ */*~ */*/*~
