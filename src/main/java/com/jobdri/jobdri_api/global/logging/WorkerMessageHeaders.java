package com.jobdri.jobdri_api.global.logging;

public final class WorkerMessageHeaders {

    public static final String REQUEST_ID = "x-request-id";
    public static final String TASK_ID = "x-task-id";
    public static final String TASK_TYPE = "x-task-type";
    public static final String RETRY_COUNT = "x-retry-count";
    public static final String MESSAGE_ID = "x-message-id";

    private WorkerMessageHeaders() {
    }
}
