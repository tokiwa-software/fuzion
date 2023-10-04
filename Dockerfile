FROM eclipse-temurin:17-alpine@sha256:fe702d6a9b2d0855f29154512358cd5c0c866b8b16544589e254a97743304d1a as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:fe702d6a9b2d0855f29154512358cd5c0c866b8b16544589e254a97743304d1a as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
