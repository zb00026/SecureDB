package com.verlake.dam.configuration;

import com.verlake.dam.batch.NotificationTaskReader;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.service.firebase.FirebaseMessagingService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.assets.Asset;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.Collections;
import java.util.Map;

import com.verlake.dam.repository.NotificationTaskRepository;

@Configuration
@EnableBatchProcessing
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class NotificationJobConfig {

    private final FirebaseMessagingService firebaseMessagingService;
    private final EmailService emailService;
    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final NotificationTaskRepository notificationTaskRepository;

    @Bean
    public ItemReader<NotificationTask> notificationReader() {
        RepositoryItemReader<NotificationTask> reader = new RepositoryItemReader<>();
        reader.setRepository(notificationTaskRepository);
        reader.setMethodName("findAll");
        reader.setPageSize(10);
        reader.setSort(Map.of("id", Sort.Direction.ASC));
        return reader;
    }

    @Bean
    public ItemWriter<NotificationTask> notificationWriter() {
        return tasks -> {
            for (NotificationTask task : tasks) {
                try {
                    firebaseMessagingService.sendNotification(NotificationMessage.fromJson(task.getNotificationMessage()));
                    emailService.sendDeveloperAssetRequestEmail(
                        task.getReceiver(),
                        task.getRequestor(),
                        task.getAsset(),
                        "developer-asset-request"
                    );
                    notificationTaskRepository.deleteById(task.getId());
                } catch (Exception e) {
                    log.error("Failed to process notification task for {}", task.getReceiver().getEmail(), e);
                    throw new RuntimeException("Failed to process notification task", e);
                }
            }
        };
    }

    @Bean
    public Job notificationJob() {
        return new JobBuilder("notificationJob", jobRepository)
                .start(notificationStep())
                .build();
    }

    @Bean
    public Step notificationStep() {
        return new StepBuilder("notificationStep", jobRepository)
                .<NotificationTask, NotificationTask>chunk(10, transactionManager)
                .reader(notificationReader())
                .writer(notificationWriter())
                .build();
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduleNotificationJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            
            jobLauncher.run(notificationJob(), params);
        } catch (Exception e) {
            log.error("Error running notification job", e);
        }
    }
} 