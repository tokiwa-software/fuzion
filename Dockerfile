FROM eclipse-temurin:17-alpine@sha256:31fd8cc4cef24cc3b25e5b9f9f86a09f4c27815220c4a8a2bb9b6bf869ff0726 as builder
WORKDIR /fuzion
COPY . .
RUN apk add --no-cache bash clang14 git make && FUZION_REPRODUCIBLE_BUILD="true" PRECONDITIONS="true" POSTCONDITIONS="true" make

FROM eclipse-temurin:17-alpine@sha256:31fd8cc4cef24cc3b25e5b9f9f86a09f4c27815220c4a8a2bb9b6bf869ff0726 as runner
COPY --from=builder /fuzion/build /fuzion
RUN apk add --no-cache bash clang14 gc-dev
ENV PATH="/fuzion/bin:${PATH}" PRECONDITIONS="true" POSTCONDITIONS="true"
