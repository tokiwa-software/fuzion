FROM ubuntu:24.04@sha256:353675e2a41babd526e2b837d7ec780c2a05bca0164f7ea5dbbd433d21d166fc AS builder
WORKDIR /fuzion
COPY . .
RUN apt-get update && apt-get -y --no-install-recommends install \
  openjdk-21-jdk-headless \
  git \
  make \
  patch \
  libgc1 \
  libgc-dev \
  shellcheck \
  asciidoc \
  asciidoctor \
  ruby-asciidoctor-pdf \
  antlr4 \
  clang-18 \
  wget
RUN ln -s /usr/bin/clang-18 /usr/bin/clang
ENV FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true"
RUN make all build/apidocs_git/index.html

FROM ubuntu:24.04@sha256:353675e2a41babd526e2b837d7ec780c2a05bca0164f7ea5dbbd433d21d166fc AS runner
COPY --from=builder /fuzion/build /fuzion
RUN apt-get update && apt-get -y --no-install-recommends install locales openjdk-21-jdk-headless git make patch libgc1 libgc-dev shellcheck asciidoc asciidoctor ruby-asciidoctor-pdf antlr4 clang-18 wget ditaa inkscape unzip && ln -s /usr/bin/clang-18 /usr/bin/clang
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG=en_US.utf8 PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true" dev_flang_tools_serializeFUIR="true" dev_flang_fuir_analysis_dfa_DFA_MAX_ITERATIONS="50"
