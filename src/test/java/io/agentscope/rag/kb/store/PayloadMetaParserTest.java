package io.agentscope.rag.kb.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PayloadMetaParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractContentText_fromTextBlockJson() throws Exception {
        var payload =
                mapper.readTree(
                        """
                        {
                          "doc_id": "d1",
                          "chunk_id": "0",
                          "content": {"type": "text", "text": "年假 10 天"}
                        }
                        """);
        assertEquals("年假 10 天", PayloadMetaParser.extractContentText(payload));
    }

    @Test
    void extractContentText_fromPlainString() throws Exception {
        var payload =
                mapper.readTree(
                        """
                        {"doc_id": "d1", "content": "纯文本内容"}
                        """);
        assertEquals("纯文本内容", PayloadMetaParser.extractContentText(payload));
    }
}
