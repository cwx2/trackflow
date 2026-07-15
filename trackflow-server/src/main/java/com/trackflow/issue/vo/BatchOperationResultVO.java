package com.trackflow.issue.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作结果 VO
 */
@Data
public class BatchOperationResultVO {

    private int total;
    private int succeeded;
    private int failed;
    private List<BatchFailureItem> failures = new ArrayList<>();

    @Data
    public static class BatchFailureItem {
        private String issueId;
        private String issueKey;
        private String reason;

        public BatchFailureItem(String issueId, String issueKey, String reason) {
            this.issueId = issueId;
            this.issueKey = issueKey;
            this.reason = reason;
        }
    }

    public void addSuccess() {
        this.succeeded++;
    }

    public void addFailure(Long issueId, String issueKey, String reason) {
        this.failed++;
        this.failures.add(new BatchFailureItem(
                String.valueOf(issueId), issueKey, reason));
    }
}
