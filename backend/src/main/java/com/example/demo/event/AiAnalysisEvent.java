package com.example.demo.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AiAnalysisEvent {
    private final Long analysisRequestId;
}
