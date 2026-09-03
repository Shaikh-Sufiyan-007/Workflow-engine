package com.workflow.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowQuartzJob implements Job {

    private final WorkflowService workflowService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String workflowIdStr = context.getMergedJobDataMap().getString("workflowId");
        if (workflowIdStr == null) {
            log.error("Quartz job missing workflowId");
            return;
        }

        UUID workflowId = UUID.fromString(workflowIdStr);
        log.info("Quartz cron trigger executing for Workflow ID: {}", workflowId);
        try {
            workflowService.triggerWorkflow(workflowId, "CRON_SCHEDULER");
        } catch (Exception e) {
            log.error("Error executing scheduled workflow ID: {}", workflowId, e);
            throw new JobExecutionException(e);
        }
    }
}
