package com.updateservice.update;

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
                case "scheduleGameStart": // define as job launched during ./start-backend . scheduled once a day to schedule game status switch for today's upcoming games
                    SchedulerFactory schedulerFactory = new StdSchedulerFactory();
                    Scheduler scheduler = schedulerFactory.getScheduler();
                    scheduler.start();
                    System.out.println("Scheduler started");
                    scheduleJob(scheduler, ScheduleGamesTask.class, "0 0 3 * * ?"); // runs everyday at 3 am
                    break;
                case "updateGameEndSettleBet": // scheduled every 3 hours (12 times a day * num_of_days in a month < 500 api calls per month (using free tier))
                    executeJobNow(UpdateGameEndSettleBetTask.class);
                    break;
                case "updateGroups": // scheduled once at 12:01 am to change status of each group that ended the previous day
                    executeJobNow(UpdateGroupTask.class);
                    break;
                case "getNewGames": // scheduled once at 1:00 am get new games from API
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
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobClass.getSimpleName())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + jobClass.getSimpleName())
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }
}
