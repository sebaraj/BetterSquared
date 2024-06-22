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

public class Updater {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No task specified");
            return;
        }

        String task = args[0];
        try {

            switch (task) {
                case "scheduleGameStart": // scheduled once at to schedule game status switch for today's upcoming games
                    executeJobNow(ScheduleGamesTask.class); // , "0 * * * * ?"
                    break;
                //case "updateGameStart": // scheduled once per job from scheduleGameStarts. doesnt need to be external facing job
                //    SchedulerFactory schedulerFactory = new StdSchedulerFactory();
                //    Scheduler scheduler = schedulerFactory.getScheduler();
                //    scheduler.start();
                //    System.out.println("Scheduler started");
                //    scheduleJob(scheduler, UpdateGameStartTask.class, "0 0 0 * * ?");
                 //   break;
                //case "updateGameEndSettleBet": // scheduled every 2 hour (12 times a day * num_of_days in a month < 500 api calls per month (using free tier))
                //    scheduleJob(scheduler, UpdateGameEndSettleBetTask.class, "54 17 * * * ?");
                //    break;
                //case "updateGrocd ups": // scheduled once at 12:01 am to change status of each group that ended the previous day
                //    scheduleJob(scheduler, UpdateGroupTask.class, "56 17 * * * ?");
                //    break;
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

    private static void scheduleJobNow(Scheduler scheduler, Class<? extends Job> jobClass) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobClass.getSimpleName())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + jobClass.getSimpleName())
                .startNow()
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
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
