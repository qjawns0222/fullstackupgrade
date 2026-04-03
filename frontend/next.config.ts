import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const nextConfig: NextConfig = {
  // Turbopack을 사용하므로 빈 설정으로 webpack 설정 충돌 방지
  turbopack: {},
};

export default withSentryConfig(nextConfig, {
  // Sentry 소스맵 업로드 — SENTRY_AUTH_TOKEN 환경변수 필요
  org: process.env.SENTRY_ORG,
  project: process.env.SENTRY_PROJECT,

  // 소스맵을 번들에서 제거해 클라이언트에 노출되지 않도록
  hideSourceMaps: true,

  // 빌드 시 Sentry에 소스맵 업로드 비활성화 (토큰 없을 때)
  silent: true,

  // 터널 라우트 — ad-blocker로 인한 Sentry 요청 차단 우회
  tunnelRoute: "/monitoring",
});
