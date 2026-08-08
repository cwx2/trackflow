package com.trackflow.issue.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作结果。
 * <p>
 * 记录批量操作中每条记录的成功/失败状态。
 * 这是操作结果对象（非实体视图投影），因此放在 dto 包。
 */
@Data
public class BatchOperationResult {

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
