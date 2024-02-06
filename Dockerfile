FROM eclipse-temurin:21-alpine@sha256:82698e23d15ada036bc176f6fb210401e0679cd0a4b1e71d05e7329982d6062c as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:82698e23d15ada036bc176f6fb210401e0679cd0a4b1e71d05e7329982d6062c as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
