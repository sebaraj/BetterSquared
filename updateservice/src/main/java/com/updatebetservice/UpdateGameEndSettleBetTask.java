package com.updateservice.update;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class UpdateGameEndSettleBetTask implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("Executing updateGameEndSettleBet task");
        return;
    }
}