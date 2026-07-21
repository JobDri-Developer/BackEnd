package com.jobdri.jobdri_api.global.logging;

public final class LoggingMdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String METHOD = "method";
    public static final String URI = "uri";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_ID = "userId";
    public static final String USER_EMAIL = "userEmail";
    public static final String LOG_TYPE = "logType";
    public static final String EVENT = "event";
    public static final String ERROR_CODE = "errorCode";
    public static final String TASK_ID = "taskId";
    public static final String MESSAGE_ID = "messageId";
    public static final String TASK_TYPE = "taskType";
    public static final String RETRY_COUNT = "retryCount";
    public static final String WORKER_ID = "workerId";
    public static final String QUEUE_LATENCY_MILLIS = "queueLatencyMillis";

    private LoggingMdcKeys() {
    }
}
