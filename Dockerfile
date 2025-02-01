FROM eclipse-temurin:21-alpine@sha256:a3ef08aadbf2d925a6af28ab644f9974df9bd053d3728caa4b28329ae968e7ad AS builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang18 git make patch && ln -s /usr/bin/clang-18 /usr/bin/clang && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:a3ef08aadbf2d925a6af28ab644f9974df9bd053d3728caa4b28329ae968e7ad AS runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang18 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
