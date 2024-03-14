FUZION_OPTIONS ?=
FUZION = ../../bin/fz $(FUZION_OPTIONS)
FUZION_HOME = $(dir $(FUZION))..

all: jvm c int

jvm: mod_jvm
	echo "m is _ := a" | $(FUZION) -jvm -sourceDirs=src -

c: mod_c
	echo "m is _ := a" | $(FUZION) -c -sourceDirs=src -

int: mod_int
	echo "m is _ := a" | $(FUZION) -interpreter -sourceDirs=src -

int2: mod_int2
	echo "m is _ := a" | $(FUZION) -interpreter2 -sourceDirs=src -

a.fum: $(FUZION_HOME)/modules/base.fum
	$(FUZION) -saveLib=a.fum -sourceDirs=src

mod_jvm: a.fum
	echo "m is _ := a" | $(FUZION) -jvm -modules=a -moduleDirs=. -

mod_c: a.fum
	echo "m is _ := a" | $(FUZION) -c -modules=a -moduleDirs=. -

mod_int: a.fum
	echo "m is _ := a" | $(FUZION) -interpreter -modules=a -moduleDirs=. -

mod_int2: a.fum
	echo "m is _ := a" | $(FUZION) -interpreter2 -modules=a -moduleDirs=. -

clean:
	rm a.fum m *~ */*~
