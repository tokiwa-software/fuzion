FROM eclipse-temurin:21-alpine@sha256:c63d8669d87e16bcee66c0379d1deedf844152da449ad48f2c8bd73a3705d36b as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang18 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:c63d8669d87e16bcee66c0379d1deedf844152da449ad48f2c8bd73a3705d36b as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang18 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
