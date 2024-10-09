FROM eclipse-temurin:21-alpine@sha256:cf94706ed7b63f1f29b720182fe3385f2fd5d17b3a20ff60163ea480572d34c7 as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang18 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:cf94706ed7b63f1f29b720182fe3385f2fd5d17b3a20ff60163ea480572d34c7 as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang18 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
