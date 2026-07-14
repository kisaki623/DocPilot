package com.docpilot.backend.document.constant;

public final class DocumentStatus {

    public static final String ACTIVE = "ACTIVE";
    public static final String REMOVED = "REMOVED";

    private DocumentStatus() {
    }

    public static boolean isRemoved(String status) {
        return REMOVED.equals(status);
    }
}
