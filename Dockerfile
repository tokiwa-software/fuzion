FROM eclipse-temurin:17-alpine@sha256:2478889d707f1883cd7c2682bba048b680261b09088c4af8ae735bc1d8a10d55 as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:2478889d707f1883cd7c2682bba048b680261b09088c4af8ae735bc1d8a10d55 as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
