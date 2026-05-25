package io.agentscope.rag.kb.ops.web;

import io.agentscope.rag.kb.ops.OpsChatService;
import io.agentscope.rag.kb.web.dto.ChatRequest;
import io.agentscope.rag.kb.web.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/knowledge-bases/{kbId}/chat")
@Tag(name = "Ops Chat", description = "选择知识库后：Qdrant 检索 + 大模型生成回答")
public class OpsChatController {

    private final OpsChatService chatService;

    public OpsChatController(OpsChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "指定知识库 RAG 对话", description = "先检索对应 Qdrant 集合，再调用 ReActAgent 生成回答")
    public ChatResponse chat(@PathVariable String kbId, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(kbId, request);
    }
}
