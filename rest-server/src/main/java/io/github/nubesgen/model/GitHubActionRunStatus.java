package io.github.nubesgen.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubActionRunStatus {

    @JsonAlias("workflow_runs")
    List<run> workflowRuns;

    public List<run> getWorkflowRuns() {
        return workflowRuns;
    }

    public void setWorkflowRuns(List<run> workflowRuns) {
        this.workflowRuns = workflowRuns;
    }

    public static class run {
        private String status;

        @JsonAlias("status")
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

}
