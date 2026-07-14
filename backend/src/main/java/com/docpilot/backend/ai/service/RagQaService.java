package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaQuery;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagQaService {

    RagQaAnswer answer(RagQaQuery query);

    SseEmitter streamAnswer(RagQaQuery query);
}
