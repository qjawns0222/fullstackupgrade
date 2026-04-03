"use client";

import * as Sentry from "@sentry/nextjs";
import React from "react";

interface Props {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface State {
  hasError: boolean;
  eventId: string | null;
}

/**
 * Sentry 연동 ErrorBoundary.
 *
 * 렌더링 중 발생한 예외를 잡아 Sentry에 캡처하고, 사용자에게 에러 ID와
 * 피드백 다이얼로그를 표시한다. fallback prop이 있으면 그것을 렌더하고,
 * 없으면 기본 에러 UI를 보여준다.
 */
export class SentryErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, eventId: null };
  }

  static getDerivedStateFromError(): Partial<State> {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    const eventId = Sentry.captureException(error, {
      contexts: {
        react: {
          componentStack: errorInfo.componentStack ?? "",
        },
      },
    });
    this.setState({ eventId: eventId ?? null });
  }

  handleFeedback = () => {
    if (this.state.eventId) {
      Sentry.showReportDialog({ eventId: this.state.eventId });
    }
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
          <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8 text-center">
            <div className="text-red-500 text-5xl mb-4">⚠</div>
            <h2 className="text-xl font-semibold text-gray-800 mb-2">
              예상치 못한 오류가 발생했습니다
            </h2>
            <p className="text-gray-500 text-sm mb-6">
              이미 오류 정보가 수집되었습니다. 페이지를 새로고침하거나 잠시 후 다시 시도해 주세요.
            </p>
            {this.state.eventId && (
              <p className="text-xs text-gray-400 mb-4 font-mono">
                Event ID: {this.state.eventId}
              </p>
            )}
            <div className="flex gap-3 justify-center">
              <button
                onClick={() => window.location.reload()}
                className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 transition-colors"
              >
                새로고침
              </button>
              {this.state.eventId && (
                <button
                  onClick={this.handleFeedback}
                  className="px-4 py-2 bg-gray-100 text-gray-700 text-sm rounded hover:bg-gray-200 transition-colors"
                >
                  피드백 보내기
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
