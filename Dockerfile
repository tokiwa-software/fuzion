FROM ubuntu:26.04@sha256:f3d28607ddd78734bb7f71f117f3c6706c666b8b76cbff7c9ff6e5718d46ff64 AS builder
WORKDIR /fuzion
COPY . .
RUN apt-get update && apt-get -y --no-install-recommends install \
  openjdk-25-jdk-headless \
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
  wget \
  systemtap-sdt-dev
RUN ln -s /usr/bin/clang-18 /usr/bin/clang
ENV FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true"
RUN make all build/apidocs_git/index.html

FROM ubuntu:26.04@sha256:f3d28607ddd78734bb7f71f117f3c6706c666b8b76cbff7c9ff6e5718d46ff64 AS runner
# NYI: HACK: chmod is a workaround for Jenkins permission issue
COPY --from=builder --chmod=o=g /fuzion/build /fuzion
RUN apt-get update && apt-get -y --no-install-recommends install \
  antlr4 \
  asciidoc \
  asciidoctor \
  clang-18 \
  ditaa \
  git \
  inkscape \
  libgc-dev \
  libgc1 \
  libsodium-dev \
  libsodium23 \
  libwolfssl-dev \
  libwolfssl42t64 \
  locales \
  make \
  openjdk-25-jdk-headless \
  patch \
  ruby-asciidoctor-pdf \
  shellcheck \
  unzip \
  wget
RUN ln -s /usr/bin/clang-18 /usr/bin/clang
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG=en_US.utf8 PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true" dev_flang_tools_serializeFUIR="true" dev_flang_fuir_analysis_dfa_DFA_MAX_ITERATIONS="50" FUZION_HOME="/fuzion"
