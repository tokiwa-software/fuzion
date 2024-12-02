FROM eclipse-temurin:21-alpine@sha256:4909fb9ab52e3ce1488cc6e6063da71a0f9f9833420cc254fe03bbe25daec9e0 AS builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang18 git make patch && ln -s /usr/bin/clang-18 /usr/bin/clang && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:4909fb9ab52e3ce1488cc6e6063da71a0f9f9833420cc254fe03bbe25daec9e0 AS runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang18 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
