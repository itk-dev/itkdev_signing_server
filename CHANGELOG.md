<!-- markdownlint-configure-file { "blanks-around-headers": { "lines_below": 0 } } -->
<!-- markdownlint-configure-file { "blanks-around-lists": false } -->

# Changelog

![keep a changelog](https://img.shields.io/badge/Keep%20a%20Changelog-v1.0.0-brightgreen.svg?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGZpbGw9IiNmMTVkMzAiIHZpZXdCb3g9IjAgMCAxODcgMTg1Ij48cGF0aCBkPSJNNjIgN2MtMTUgMy0yOCAxMC0zNyAyMmExMjIgMTIyIDAgMDAtMTggOTEgNzQgNzQgMCAwMDE2IDM4YzYgOSAxNCAxNSAyNCAxOGE4OSA4OSAwIDAwMjQgNCA0NSA0NSAwIDAwNiAwbDMtMSAxMy0xYTE1OCAxNTggMCAwMDU1LTE3IDYzIDYzIDAgMDAzNS01MiAzNCAzNCAwIDAwLTEtNWMtMy0xOC05LTMzLTE5LTQ3LTEyLTE3LTI0LTI4LTM4LTM3QTg1IDg1IDAgMDA2MiA3em0zMCA4YzIwIDQgMzggMTQgNTMgMzEgMTcgMTggMjYgMzcgMjkgNTh2MTJjLTMgMTctMTMgMzAtMjggMzhhMTU1IDE1NSAwIDAxLTUzIDE2bC0xMyAyaC0xYTUxIDUxIDAgMDEtMTItMWwtMTctMmMtMTMtNC0yMy0xMi0yOS0yNy01LTEyLTgtMjQtOC0zOWExMzMgMTMzIDAgMDE4LTUwYzUtMTMgMTEtMjYgMjYtMzMgMTQtNyAyOS05IDQ1LTV6TTQwIDQ1YTk0IDk0IDAgMDAtMTcgNTQgNzUgNzUgMCAwMDYgMzJjOCAxOSAyMiAzMSA0MiAzMiAyMSAyIDQxLTIgNjAtMTRhNjAgNjAgMCAwMDIxLTE5IDUzIDUzIDAgMDA5LTI5YzAtMTYtOC0zMy0yMy01MWE0NyA0NyAwIDAwLTUtNWMtMjMtMjAtNDUtMjYtNjctMTgtMTIgNC0yMCA5LTI2IDE4em0xMDggNzZhNTAgNTAgMCAwMS0yMSAyMmMtMTcgOS0zMiAxMy00OCAxMy0xMSAwLTIxLTMtMzAtOS01LTMtOS05LTEzLTE2YTgxIDgxIDAgMDEtNi0zMiA5NCA5NCAwIDAxOC0zNSA5MCA5MCAwIDAxNi0xMmwxLTJjNS05IDEzLTEzIDIzLTE2IDE2LTUgMzItMyA1MCA5IDEzIDggMjMgMjAgMzAgMzYgNyAxNSA3IDI5IDAgNDJ6bS00My03M2MtMTctOC0zMy02LTQ2IDUtMTAgOC0xNiAyMC0xOSAzN2E1NCA1NCAwIDAwNSAzNGM3IDE1IDIwIDIzIDM3IDIyIDIyLTEgMzgtOSA0OC0yNGE0MSA0MSAwIDAwOC0yNCA0MyA0MyAwIDAwLTEtMTJjLTYtMTgtMTYtMzEtMzItMzh6bS0yMyA5MWgtMWMtNyAwLTE0LTItMjEtN2EyNyAyNyAwIDAxLTEwLTEzIDU3IDU3IDAgMDEtNC0yMCA2MyA2MyAwIDAxNi0yNWM1LTEyIDEyLTE5IDI0LTIxIDktMyAxOC0yIDI3IDIgMTQgNiAyMyAxOCAyNyAzM3MtMiAzMS0xNiA0MGMtMTEgOC0yMSAxMS0zMiAxMXptMS0zNHYxNGgtOFY2OGg4djI4bDEwLTEwaDExbC0xNCAxNSAxNyAxOEg5NnoiLz48L3N2Zz4K)

All notable changes to this project will be documented in this file.

See [keep a changelog](https://keepachangelog.com/en/1.1.0/) for information about writing changes to this log.

## [Unreleased]

## [1.0.0] - 2026-11-26

- Upgrade the NemLog-In SignSDK from 2.0.2 to 2.0.11.
- Upgrade Spring Boot from 3.5.14 to 4.1.3.
  The 3.5 line gets no more open-source security fixes.
- Build and run on Java 25 images. The Java language level is now 21.
- Correct the sdk:download task for the new SDK zip layout.

## [0.0.7] - 2026-11-18

- Set JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics" in the runtime image.
  This turns off the vulnerable AVX-512 VBMI decode path on all CPUs.
  Attackers can reach Base64 decode without authentication through
  /sign (forward_url) and /signing-result (result).
- Add --pull to the docker:build and docker:push tasks. Each build then
  gets the latest eclipse-temurin:21-jre-jammy base image.

## [0.0.6] - 2026-10-27

- Switched to the maintained `nginx/nginx-unprivileged` image (replaces archived `nginxinc/nginx-unprivileged`).

## [0.0.5] - 2026-09-29

- Added standard labels to nginx to match itkdev setup and redirect http -> https.

## [0.0.4] - 2026-06-08

- Upgrade Sprint boot to 3.5.x
- Replaced SDK git clone with official zip download from cms.nemlog-in.dk for easier upgrades.

## [0.0.3] - 2026-01-19

- Ensure documents are only downloadable once and deleted after being downloaded.

## [0.0.2] - 2026-01-17

- Runtime UID/GID configuration via `PUID`/`PGID` environment variables (no rebuild needed).

## [0.0.1] - 2026-01-16

- First release with basic functionality


[unreleased]: https://github.com/itk-dev/itkdev_signing_server.git/compare/1.0.0...HEAD
[1.0.0]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/1.0.0
[0.0.7]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.7
[0.0.6]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.6
[0.0.5]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.5
[0.0.4]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.4
[0.0.3]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.3
[0.0.2]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.2
[0.0.1]: https://github.com/itk-dev/itkdev_signing_server/releases/tag/0.0.1
