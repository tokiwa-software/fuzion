FROM ubuntu:24.04@sha256:72297848456d5d37d1262630108ab308d3e9ec7ed1c3286a32fe09856619a782 AS builder
WORKDIR /fuzion
COPY . .
RUN apt-get update && apt-get -y --no-install-recommends install openjdk-21-jdk-headless git make patch libgc1 libgc-dev shellcheck asciidoc asciidoctor ruby-asciidoctor-pdf pcregrep antlr4 clang-18 && ln -s /usr/bin/clang-18 /usr/bin/clang && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM ubuntu:24.04@sha256:72297848456d5d37d1262630108ab308d3e9ec7ed1c3286a32fe09856619a782 AS runner
COPY --from=builder /fuzion/build /fuzion
RUN apt-get update && apt-get -y --no-install-recommends install openjdk-21-jdk-headless git make patch libgc1 libgc-dev shellcheck asciidoc asciidoctor ruby-asciidoctor-pdf pcregrep antlr4 clang-18 && ln -s /usr/bin/clang-18 /usr/bin/clang
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true" dev_flang_tools_serializeFUIR="true"
