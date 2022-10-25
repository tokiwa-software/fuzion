OK   = "\033[32mOK\033[0m"
FAIL = "\033[1;31m*** FAIL ***\033[0m"
CHECK = && echo $(OK) || (echo $(FAIL); exit 1)

FZ = ../../bin/fz

.PHONY: all sources_only a ab b int c clean
all: sources_only a ab b
                       # xy (shows problem related to #462)

int: all

c: sources_only a_c ab_c b_c

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

# compile to C code and run example using a.fum and sources from src_b and src
a_c: modules/a.fum
	($(FZ) -sourceDirs=./src_b,./src -modules=a   -moduleDirs=modules -c test_modules -o=testbin && ./testbin) $(CHECK)

# compile src_b into b.fum
modules/b.fum: modules/a.fum
	mkdir -p $(@D)
	$(FZ) -sourceDirs=./src_b       -modules=a   -moduleDirs=modules -saveLib=$@  $(CHECK)

# run example using a.fum and b.fum and sources src
ab: modules/a.fum modules/b.fum
	$(FZ) -sourceDirs=./src         -modules=a,b -moduleDirs=modules test_modules $(CHECK)

# compile to C code and run example using a.fum and b.fum and sources src
ab_c: modules/a.fum modules/b.fum
	($(FZ) -sourceDirs=./src         -modules=a,b -moduleDirs=modules -c test_modules -o=testbin && ./testbin) $(CHECK)

# run example using b.fum and sources src.
# a.fum is not added automatically, so this causes errors, see #484
b: modules/b.fum
	make -f b.make int

# compile to C code and run example using b.fum and sources src.
# a.fum is not added automatically, so this causes errors, see #484
b_c: modules/b.fum
	make -f b.make c

# NYI: see #462: try loading inner feature 'x.y' from command line while outer
# 'x' is defined in module, does not work currently.
xy:
	mkdir -p modules
	$(FZ) -sourceDirs=src_x -saveLib=modules/x.fum
	$(FZ) -sourceDirs=src_y -modules=x -moduleDirs=modules x.y

clean:
	rm -rf modules *~ */*~ */*/*~
