FROM eclipse-temurin:21-alpine@sha256:511d5a9217ed753d9c099d3d753111d7f9e0e40550b860bceac042f4e55f715c as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:511d5a9217ed753d9c099d3d753111d7f9e0e40550b860bceac042f4e55f715c as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
