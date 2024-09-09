FROM eclipse-temurin:21-alpine@sha256:5b836a84d8287dcba9c89beb5a449871430206b8ff758a7732f2e43313c0fbd4 as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang18 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:21-alpine@sha256:5b836a84d8287dcba9c89beb5a449871430206b8ff758a7732f2e43313c0fbd4 as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang18 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
