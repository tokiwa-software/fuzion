FROM eclipse-temurin:21-alpine@sha256:6da40af213861c1b9cc475affa011d395d4dbcf0cb031b6a5d05a658fd9e3c5e as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:6da40af213861c1b9cc475affa011d395d4dbcf0cb031b6a5d05a658fd9e3c5e as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
