FROM eclipse-temurin:17-alpine@sha256:854b05154ed3e25ca817137463c9d84b425350d51d958a2b264094622914731f as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:854b05154ed3e25ca817137463c9d84b425350d51d958a2b264094622914731f as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
