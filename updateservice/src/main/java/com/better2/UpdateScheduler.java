/***********************************************************************************************************************
 *  File Name:       UpdateScheduler.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Intializes scheduler and switch to direct scheduled cronjobs to appropriate task
 **********************************************************************************************************************/
package com.better2.updateservice;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class UpdateScheduler {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No task specified");
            return;
        }

        String task = args[0];
        try {
            switch (task) {
                case "scheduleGameStart":
                    SchedulerFactory schedulerFactory = new StdSchedulerFactory();
                    Scheduler scheduler = schedulerFactory.getScheduler();
                    scheduler.start();
                    scheduleJob(scheduler, ScheduleGamesTask.class, "0 0 3 * * ?");
                    System.out.println("UpdateScheduler: ScheduleGameTask started");
                    break;
                case "updateGameEndSettleBet":
                    executeJobNow(UpdateGameEndSettleBetTask.class);
                    break;
                case "updateGroups":
                    executeJobNow(UpdateGroupTask.class);
                    break;
                case "getNewGames":
                    executeJobNow(GetNewGamesTask.class);
                    break;
                default:
                    System.err.println("Unknown task: " + task);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void executeJobNow(Class<? extends Job> jobClass) throws Exception {
        Job job = jobClass.getDeclaredConstructor().newInstance();
        job.execute(null);
    }

    private static void scheduleJob(Scheduler scheduler, Class<? extends Job> jobClass, String cronExpression) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(jobClass).withIdentity(jobClass.getSimpleName()).build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger-" + jobClass.getSimpleName()).withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).build();
        scheduler.scheduleJob(jobDetail, trigger);
    }
}
