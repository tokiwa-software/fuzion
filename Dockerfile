FROM eclipse-temurin:20-alpine@sha256:83f609aa050cce0490f10c07d36548e12973a9100d7144b87fb16f2897ff323a as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:20-alpine@sha256:83f609aa050cce0490f10c07d36548e12973a9100d7144b87fb16f2897ff323a as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
