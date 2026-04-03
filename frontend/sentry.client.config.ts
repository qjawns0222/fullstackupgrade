import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN,

  // 성능 트랜잭션 샘플링 — 로컬 개발 시 100%, 프로덕션은 0.1~0.2 권장
  tracesSampleRate: process.env.NODE_ENV === "production" ? 0.1 : 1.0,

  // 세션 리플레이 — 에러 발생 세션의 10%, 일반 세션의 1% 녹화
  replaysOnErrorSampleRate: 1.0,
  replaysSessionSampleRate: 0.01,

  integrations: [
    Sentry.replayIntegration({
      // 입력 필드 마스킹 — 비밀번호, 개인정보 보호
      maskAllInputs: true,
      blockAllMedia: false,
    }),
  ],

  environment: process.env.NODE_ENV ?? "development",

  // 개발 환경에서 콘솔 출력
  debug: process.env.NODE_ENV === "development",

  // 빌드 버전 추적
  release: process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.1",
});
