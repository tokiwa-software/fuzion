FROM eclipse-temurin:17-alpine@sha256:58b8b3ed1ea3538babaf4438811ed3481294a4df852b1de093791c378f85f69b as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:58b8b3ed1ea3538babaf4438811ed3481294a4df852b1de093791c378f85f69b as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
