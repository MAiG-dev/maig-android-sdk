# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2025-01-01

### Added
- `AIGatewayClient` with `generateText()` (non-streaming) and `streamText()` (SSE via `Flow<String>`)
- `GenerateOptions` for model selection, user ID, and token budget
- `AIGatewayError` sealed class: `AuthFailure`, `ServerError`, `NetworkError`
- Automatic retry with exponential backoff (2 retries, 1 s / 2 s delays) for transient failures
- `NetworkClient` internal interface for full unit-test coverage without a live server
- OkHttp + Gson transport layer
- JUnit 5 + kotlinx-coroutines-test test suite

[Unreleased]: https://github.com/your-org/maig-android-sdk/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/your-org/maig-android-sdk/releases/tag/v0.1.0
