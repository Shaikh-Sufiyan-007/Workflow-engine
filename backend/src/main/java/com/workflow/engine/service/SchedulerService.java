package com.workflow.engine.service;

import com.workflow.engine.entity.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final Scheduler scheduler;

    public void scheduleWorkflowCron(Workflow workflow) {
        if (workflow.getCronExpression() == null || workflow.getCronExpression().trim().isEmpty()) {
            unscheduleWorkflowCron(workflow);
            return;
        }

        try {
            JobKey jobKey = JobKey.jobKey(workflow.getId().toString(), "WORKFLOW_JOBS");
            TriggerKey triggerKey = TriggerKey.triggerKey(workflow.getId().toString(), "WORKFLOW_TRIGGERS");

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            JobDetail jobDetail = JobBuilder.newJob(WorkflowQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("workflowId", workflow.getId().toString())
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(workflow.getCronExpression()))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Successfully scheduled Quartz cron trigger for Workflow '{}' (ID: {}) with cron '{}'",
                    workflow.getName(), workflow.getId(), workflow.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule Quartz cron for Workflow ID: {}", workflow.getId(), e);
        }
    }

    public void unscheduleWorkflowCron(Workflow workflow) {
        try {
            JobKey jobKey = JobKey.jobKey(workflow.getId().toString(), "WORKFLOW_JOBS");
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Unscheduled Quartz job for Workflow ID: {}", workflow.getId());
            }
        } catch (Exception e) {
            log.error("Failed to unschedule Quartz job for Workflow ID: {}", workflow.getId(), e);
        }
    }
}
