package com.docpilot.backend.ai.vo;

import java.util.List;

public class DocumentQaResponse {

    private Long documentId;
    private String question;
    private String answer;
    private String sessionId;
    private List<CitationItem> citations;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<CitationItem> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationItem> citations) {
        this.citations = citations;
    }

    public static class CitationItem {

        private Integer chunkIndex;
        private Integer charStart;
        private Integer charEnd;
        private String snippet;
        private Integer score;

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public Integer getCharStart() {
            return charStart;
        }

        public void setCharStart(Integer charStart) {
            this.charStart = charStart;
        }

        public Integer getCharEnd() {
            return charEnd;
        }

        public void setCharEnd(Integer charEnd) {
            this.charEnd = charEnd;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }
    }
}

