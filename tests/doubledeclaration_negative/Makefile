NAME = doubledeclaration_negative
EXPECTED_ERRORS = `cat *.fu | grep "should.flag.an.error"  | sed "s ^.*//  g"| sort -n | uniq | wc -l`

all:
	$(FUSION) $(NAME) 2>err.txt || echo -n
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l | grep ^$(EXPECTED_ERRORS)$$ && echo "test passed." || exit 1

show:
	echo -n "Expected $(EXPECTED_ERRORS) errors, found " && cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq | wc -l
	cat err.txt  | grep "should.flag.an.error" | sed "s ^.*//  g"| sort -n | uniq
