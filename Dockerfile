FROM eclipse-temurin:17-alpine@sha256:0f65d052cba399992199061c48ad3456d2ef33214a02c983d14fccb52b79b26c as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:0f65d052cba399992199061c48ad3456d2ef33214a02c983d14fccb52b79b26c as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
