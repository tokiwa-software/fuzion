FROM eclipse-temurin:17-alpine@sha256:ad135147f78ddb330275438075a7177aaf9408f62d6b97ad2ecb6e66c1adc7b9 as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:ad135147f78ddb330275438075a7177aaf9408f62d6b97ad2ecb6e66c1adc7b9 as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
