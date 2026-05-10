package com.aichat.app.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmapMcpClientTest {

    @Test
    void mcpResultTextExtractsTextContentRecord() {
        String text = AmapMcpClient.extractContentText(List.of(new FakeTextContent("hello")));

        assertThat(text).isEqualTo("hello");
    }

    @Test
    void mcpResultTextFallsBackToRecordString() {
        String text = AmapMcpClient.extractContentText(List.of(new FakeOtherContent("image")));

        assertThat(text).contains("image");
    }

    record FakeTextContent(String text) {
    }

    record FakeOtherContent(String value) {
    }
}
