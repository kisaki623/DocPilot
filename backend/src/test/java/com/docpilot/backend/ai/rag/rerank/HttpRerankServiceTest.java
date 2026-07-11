package com.docpilot.backend.ai.rag.rerank;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRerankServiceTest {

    @Test
    void shouldReturnIdentityRerankWhenDisabled() {
        RerankProperties properties = new RerankProperties();
        HttpRerankService service = new HttpRerankService(properties);

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        assertThat(result.model()).isEqualTo("identity");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("provider_not_configured");
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(0, 1);
    }

    @Test
    void shouldReturnIdentityWithoutHttpCallWhenExternalProviderConfigIsIncomplete() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        properties.setProvider("openai_compatible");
        properties.setBaseUrl("http://rerank.local");
        properties.setModel("mock-reranker");
        HttpRerankService service = new HttpRerankService(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        server.verify();
        assertThat(result.model()).isEqualTo("identity");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("provider_not_configured");
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(0, 1);
    }

    @Test
    void shouldParseOpenAiCompatibleRerankResponse() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        properties.setProvider("openai_compatible");
        properties.setBaseUrl("http://rerank.local");
        properties.setModel("mock-reranker");
        properties.setApiKey("test-key");
        HttpRerankService service = new HttpRerankService(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://rerank.local/rerank"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "mock-reranker",
                          "query": "query",
                          "documents": ["a", "b"],
                          "top_n": 2
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "mock-reranker",
                          "results": [
                            {"index": 1, "relevance_score": 0.91},
                            {"index": 0, "relevance_score": 0.42}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        server.verify();
        assertThat(result.model()).isEqualTo("mock-reranker");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.fallbackReason()).isBlank();
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(1, 0);
        assertThat(result.hits()).extracting(RerankResult.RerankHit::relevanceScore)
                .containsExactly(0.91D, 0.42D);
    }

    @Test
    void shouldCallAliyunBailianQwen3RerankEndpoint() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        properties.setProvider("bailian");
        properties.setBaseUrl("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks");
        properties.setModel("qwen3-rerank");
        properties.setApiKey("test-key");
        HttpRerankService service = new HttpRerankService(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-api/v1/reranks"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3-rerank",
                          "query": "query",
                          "documents": ["a", "b"],
                          "top_n": 2
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen3-rerank",
                          "results": [
                            {"index": 1, "relevance_score": 0.93},
                            {"index": 0, "relevance_score": 0.31}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        server.verify();
        assertThat(properties.getProvider()).isEqualTo(RerankProperties.PROVIDER_ALIYUN_BAILIAN);
        assertThat(result.model()).isEqualTo("qwen3-rerank");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(1, 0);
    }

    @Test
    void shouldParseAliyunBailianOutputWrappedRerankResponse() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        properties.setProvider("aliyun_bailian");
        properties.setBaseUrl("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
        properties.setModel("gte-rerank-v2");
        properties.setApiKey("test-key");
        HttpRerankService service = new HttpRerankService(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"))
                .andExpect(content().json("""
                        {
                          "model": "gte-rerank-v2",
                          "input": {
                            "query": "query",
                            "documents": ["a", "b"]
                          },
                          "parameters": {
                            "return_documents": false,
                            "top_n": 2
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "results": [
                              {"index": 0, "relevance_score": 0.88},
                              {"index": 1, "relevance_score": 0.12}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        server.verify();
        assertThat(result.model()).isEqualTo("gte-rerank-v2");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(0, 1);
    }

    @Test
    void shouldFallbackToIdentityWhenProviderFails() {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        properties.setProvider("openai_compatible");
        properties.setBaseUrl("http://rerank.local");
        properties.setModel("mock-reranker");
        properties.setApiKey("test-key");
        HttpRerankService service = new HttpRerankService(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://rerank.local/rerank"))
                .andRespond(withServerError());

        RerankResult result = service.rerank(new RerankRequest("query", List.of("a", "b"), 2));

        server.verify();
        assertThat(result.model()).isEqualTo("identity");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("provider_http_error");
        assertThat(result.hits()).extracting(RerankResult.RerankHit::index)
                .containsExactly(0, 1);
    }
}
