package com.docpilot.backend.ai.rag.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP-based rerank service supporting multiple providers.
 * Supports Cohere Rerank API and OpenAI-compatible rerank endpoints.
 */
@Service
public class HttpRerankService implements RerankService {

    private static final Logger log = LoggerFactory.getLogger(HttpRerankService.class);

    private static final String PROVIDER_DISABLED = "disabled";
    private static final String PROVIDER_COHERE = "cohere";
    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";

    private final RerankProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpRerankService(RerankProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate(requestFactory(properties));
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public RerankResult rerank(RerankRequest request) {
        if (!properties.isEnabled()) {
            return fallbackToIdentityRerank(request);
        }

        String provider = properties.getProvider();
        if (PROVIDER_DISABLED.equals(provider)) {
            return fallbackToIdentityRerank(request);
        }

        try {
            if (PROVIDER_COHERE.equals(provider)) {
                return rerankWithCohere(request);
            } else if (PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
                return rerankWithOpenAICompatible(request);
            } else {
                return fallbackToIdentityRerank(request);
            }
        } catch (Exception e) {
            log.warn("RAG rerank failed, fallback to identity. provider={}, model={}, error={}",
                    provider, properties.getModel(), e.getClass().getSimpleName());
            return fallbackToIdentityRerank(request);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(RerankProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getRequestTimeoutMs());
        return factory;
    }

    /**
     * Fallback: return documents in original order with uniform scores.
     */
    private RerankResult fallbackToIdentityRerank(RerankRequest request) {
        List<RerankResult.RerankHit> hits = new ArrayList<>();
        int topK = Math.min(request.topK(), request.documents().size());
        for (int i = 0; i < topK; i++) {
            hits.add(new RerankResult.RerankHit(i, 1.0 - (i * 0.01))); // Slight decay
        }
        return new RerankResult(hits, "identity");
    }

    /**
     * Rerank using Cohere Rerank API.
     * API doc: https://docs.cohere.com/reference/rerank-1
     */
    private RerankResult rerankWithCohere(RerankRequest request) throws Exception {
        String url = properties.getBaseUrl().isEmpty()
                ? "https://api.cohere.ai/v1/rerank"
                : properties.getBaseUrl();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        body.put("documents", request.documents());
        body.put("top_n", request.topK());
        if (!properties.getModel().isEmpty()) {
            body.put("model", properties.getModel());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String response = restTemplate.postForObject(url, entity, String.class);

        return parseCohereResponse(response);
    }

    private RerankResult parseCohereResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.get("results");
        String model = root.has("model") ? root.get("model").asText() : "cohere";

        List<RerankResult.RerankHit> hits = new ArrayList<>();
        if (results != null && results.isArray()) {
            for (JsonNode result : results) {
                int index = result.get("index").asInt();
                double score = result.get("relevance_score").asDouble();
                hits.add(new RerankResult.RerankHit(index, score));
            }
        }

        return new RerankResult(hits, model);
    }

    /**
     * Rerank using OpenAI-compatible rerank endpoint.
     */
    private RerankResult rerankWithOpenAICompatible(RerankRequest request) throws Exception {
        String url = properties.getBaseUrl() + "/rerank";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        body.put("documents", request.documents());
        body.put("top_k", request.topK());
        if (!properties.getModel().isEmpty()) {
            body.put("model", properties.getModel());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!properties.getApiKey().isEmpty()) {
            headers.setBearerAuth(properties.getApiKey());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String response = restTemplate.postForObject(url, entity, String.class);

        return parseOpenAICompatibleResponse(response);
    }

    private RerankResult parseOpenAICompatibleResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.get("results");
        String model = root.has("model") ? root.get("model").asText() : "rerank";

        List<RerankResult.RerankHit> hits = new ArrayList<>();
        if (results != null && results.isArray()) {
            for (JsonNode result : results) {
                int index = result.get("index").asInt();
                double score = result.get("relevance_score").asDouble();
                hits.add(new RerankResult.RerankHit(index, score));
            }
        }

        return new RerankResult(hits, model);
    }
}
