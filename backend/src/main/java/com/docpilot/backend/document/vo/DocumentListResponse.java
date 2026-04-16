package com.docpilot.backend.document.vo;

import java.util.List;

public class DocumentListResponse {

    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private List<DocumentListItemResponse> records;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<DocumentListItemResponse> getRecords() {
        return records;
    }

    public void setRecords(List<DocumentListItemResponse> records) {
        this.records = records;
    }
}

