package com.docpilot.backend.ai.rag.rewrite;

import java.util.List;

public interface QueryRewriteService {

    List<QueryRewriteVariant> rewrite(String query, int maxVariants);
}
