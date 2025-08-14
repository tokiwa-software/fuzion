FROM ubuntu:24.04@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061 AS builder
WORKDIR /fuzion
COPY . .
RUN apt-get update && apt-get -y --no-install-recommends install openjdk-21-jdk-headless git make patch libgc1 libgc-dev shellcheck asciidoc asciidoctor ruby-asciidoctor-pdf antlr4 clang-18 && ln -s /usr/bin/clang-18 /usr/bin/clang && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM ubuntu:24.04@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061 AS runner
COPY --from=builder /fuzion/build /fuzion
RUN apt-get update && apt-get -y --no-install-recommends install locales openjdk-21-jdk-headless git make patch libgc1 libgc-dev shellcheck asciidoc asciidoctor ruby-asciidoctor-pdf antlr4 clang-18 && ln -s /usr/bin/clang-18 /usr/bin/clang
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG=en_US.utf8 PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true" dev_flang_tools_serializeFUIR="true"
