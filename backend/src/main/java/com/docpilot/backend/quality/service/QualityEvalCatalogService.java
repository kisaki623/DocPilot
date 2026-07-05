package com.docpilot.backend.quality.service;

import com.docpilot.backend.quality.vo.QualityEvalCaseCatalogItem;

import java.util.List;

public interface QualityEvalCatalogService {

    List<QualityEvalCaseCatalogItem> listEvalCases();
}
