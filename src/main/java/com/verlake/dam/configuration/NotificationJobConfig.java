package com.verlake.dam.configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.json.Json;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.assets.dto.DeleteQueryAlertData;
import com.verlake.dam.service.firebase.FirebaseMessagingService;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.exception.FirebaseMessagingOperationException;
import com.verlake.dam.exception.NotificationJobException;
import com.verlake.dam.exception.NotificationProcessingException;
import com.verlake.dam.exception.NotificationTimeoutException;
import com.verlake.dam.exception.NotificationEmailException;
import lombok.RequiredArgsConstructor;

import org.keycloak.email.EmailException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.enums.EmailType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;

@Configuration
@EnableBatchProcessing
@EnableScheduling
@EnableRetry
@Slf4j
public class NotificationJobConfig {
    // Counters for monitoring
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger successfullyProcessed = new AtomicInteger(0);
    private final AtomicInteger failedToProcess = new AtomicInteger(0);

    @Value("${notification.job.chunk-size:10}")
    private int chunkSize;

    @Value("${notification.job.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.job.retry-delay:1000}")
    private long retryDelay;

    @Value("${notification.job.firebase.timeout-ms:5000}")
    private long firebaseTimeoutMs;

    private final FirebaseMessagingService firebaseMessagingService;
    private final EmailService emailService;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final NotificationTaskRepository notificationTaskRepository;
    private final ObjectMapper objectMapper;
    private final JobLauncher jobLauncher;

    public NotificationJobConfig(
            FirebaseMessagingService firebaseMessagingService,
            EmailService emailService,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            NotificationTaskRepository notificationTaskRepository,
            ObjectMapper objectMapper,
            @Qualifier("notificationJobLauncher") JobLauncher jobLauncher) {
        this.firebaseMessagingService = firebaseMessagingService;
        this.emailService = emailService;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.notificationTaskRepository = notificationTaskRepository;
        this.objectMapper = objectMapper;
        this.jobLauncher = jobLauncher;
    }

    @Bean
    public AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("notification-job-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ItemReader<NotificationTask> notificationReader() {
        RepositoryItemReader<NotificationTask> reader = new RepositoryItemReader<>();
        reader.setRepository(notificationTaskRepository);
        reader.setMethodName("findByIsSentFalse");
        reader.setPageSize(chunkSize);
        reader.setSort(Map.of("id", Sort.Direction.ASC));
        return reader;
    }

    @Bean
    public ItemWriter<NotificationTask> notificationWriter() {
        return tasks -> {
            try {
                processNotificationTasks(tasks);
            } catch (Exception e) {
                log.error("Error processing notification batch", e);
                // Failed tasks will be picked up on the next run
            }
        };
    }

    private void processNotificationTasks(Iterable<? extends NotificationTask> tasks) {
        Map<Long, Exception> failedTasks = new HashMap<>();

        for (NotificationTask task : tasks) {
            totalProcessed.incrementAndGet();
            try {
                processNotificationTask(task);
                successfullyProcessed.incrementAndGet();
            } catch (Exception e) {
                String errorMessage = String.format("Failed to process notification task id=%d for recipient=%s",
                        task.getId(), task.getReceiver().getEmail());
                log.error(errorMessage, e);
                failedTasks.put(task.getId(), e);
                failedToProcess.incrementAndGet();

                if (e instanceof NotificationJobException notJobException) {
                    throw notJobException;
                }
                throw new NotificationProcessingException(errorMessage, task.getId(), task.getReceiver().getEmail(), e);
            }
        }

        if (!failedTasks.isEmpty()) {
            log.warn("Failed to process {} notification tasks", failedTasks.size());
        }

        // Log statistics periodically
        if (totalProcessed.get() % 100 == 0) {
            logProcessingStatistics();
        }
    }

    private void logProcessingStatistics() {
        log.info("Notification processing statistics - Total: {}, Success: {}, Failed: {}",
                totalProcessed.get(),
                successfullyProcessed.get(),
                failedToProcess.get());
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000), include = {
            NotificationJobException.class }, exclude = { JsonParseException.class })
    protected void processNotificationTask(NotificationTask task) throws Exception {
        // Send push notification with timeout
        try {
            sendPushNotificationWithTimeout(task);
        } catch (Exception e) {
            if (e instanceof InterruptedException intException) {
                Thread.currentThread().interrupt();
                throw intException;
            }
            log.error("Failed to send push notification", e);
            // Continue with email sending even if push notification fails
        }

        // Send email based on type
        sendEmailBasedOnType(task);

        // Update task status
        task.setSent(true);
        notificationTaskRepository.saveAndFlush(task);
    }

    private void sendPushNotificationWithTimeout(NotificationTask task)
            throws InterruptedException, JsonParseException, FirebaseMessagingOperationException {
        // Create a timeout wrapper around the firebase call
        boolean[] completed = new boolean[1];
        Exception[] exception = new Exception[1];

        Thread notificationThread = new Thread(() -> {
            try {
                firebaseMessagingService.sendNotification(
                        NotificationMessage.fromJson(task.getNotificationMessage()));
                completed[0] = true;
            } catch (Exception e) {
                exception[0] = e;
            }
        });

        notificationThread.start();
        notificationThread.join(firebaseTimeoutMs);

        if (!completed[0]) {
            if (notificationThread.isAlive()) {
                notificationThread.interrupt();
                throw new NotificationTimeoutException(
                        String.format("Firebase notification timed out after %d ms", firebaseTimeoutMs),
                        firebaseTimeoutMs);
            }

            if (exception[0] != null) {
                if (exception[0] instanceof InterruptedException intException) {
                    Thread.currentThread().interrupt();
                    throw intException;
                }
                if (exception[0] instanceof FirebaseMessagingOperationException fbException) {
                    throw fbException;
                }
                throw new FirebaseMessagingOperationException("Failed to send push notification", exception[0]);
            }
        }
    }

    protected void sendEmailBasedOnType(NotificationTask task) throws Exception {
        // Map of handlers for different email types
        Map<EmailType, Consumer<NotificationTask>> emailHandlers = Map.of(
                EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY, this::sendDeveloperAssetRequestEmail,
                EmailType.DEVELOPER_RELINQUISH_ASSET_NOTIFY, this::sendDeveloperRelinquishAssetEmail,
                EmailType.ASSET_QUERY_CHANGE_REQUEST_NOTIFY, this::sendAssetQueryChangeRequestEmail,
                EmailType.INVITATION, notificationTask -> {
                    try {
                        sendInvitationEmail(notificationTask);
                    } catch (Exception e) {
                        throw new NotificationProcessingException(
                                "Failed to send invitation email",
                                notificationTask.getId(),
                                notificationTask.getReceiver().getEmail(),
                                e);
                    }
                },
                EmailType.ASSET_QUERY_CHANGE_REQUEST_APPROVAL_NOTIFY, notificationTask -> {
                    try {
                        sendAssetQueryChangeRequestApprovalEmail(notificationTask);
                    } catch (Exception e) {
                        throw new NotificationProcessingException(
                                "Failed to send asset query change request approval email",
                                notificationTask.getId(),
                                notificationTask.getReceiver().getEmail(),
                                e);
                    }
                },
                EmailType.APPROVAL_ASSET_ACCESS_REQUEST, notificationTask -> {
                    try {
                        sendApprovalAssetAccessRequestEmail(notificationTask);
                    } catch (Exception e) {
                        throw new NotificationProcessingException(
                                "Failed to send approval email",
                                notificationTask.getId(),
                                notificationTask.getReceiver().getEmail(),
                                e);
                    }
                },
                EmailType.DELETE_QUERY_ALERT, notificationTask -> {
                    try {
                        sendDeleteQueryNotifyEmail(notificationTask);
                    } catch (Exception e) {
                        throw new NotificationProcessingException(
                                "Failed to send DELETE query notify email",
                                notificationTask.getId(),
                                notificationTask.getReceiver().getEmail(),
                                e);
                    }
                },
                EmailType.FORGOT_PASSWORD, notificationTask -> {
                    try {
                        sendForgotPasswordEmail(notificationTask);
                    } catch (Exception e) {
                        throw new NotificationProcessingException(
                                "Failed to send forgot password email",
                                notificationTask.getId(),
                                notificationTask.getReceiver().getEmail(),
                                e);
                    }
                });

        // Get handler for the email type
        Consumer<NotificationTask> handler = emailHandlers.get(task.getEmailType());

        if (handler != null) {
            handler.accept(task);
        } else {
            log.warn("Unhandled email type: {}", task.getEmailType());
        }
    }

    private void sendDeveloperAssetRequestEmail(NotificationTask task) {
        emailService.sendDeveloperAssetRequestEmail(
                task.getReceiver(),
                task.getSender(),
                task.getAsset(),
                "developer-asset-request");
    }

    private void sendDeveloperRelinquishAssetEmail(NotificationTask task) {
        emailService.sendDeveloperRelinquishEmail(
                task.getReceiver(),
                task.getSender(),
                task.getAsset(),
                "developer-relinquish-asset");
    }

    private void sendAssetQueryChangeRequestEmail(NotificationTask task) {
        // Parse notification message to extract additional data
        try {
            ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());
            JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);
            if (dataNode == null) {
                throw new JsonParseException(null, "Data node not found in asset query change request notification");
            }
            String ticketReference = dataNode.has(Constants.EMAIL_VAR_TICKET_REFERENCE)
                    ? dataNode.get(Constants.EMAIL_VAR_TICKET_REFERENCE).asText()
                    : Constants.DEFAULT_UNKNOWN_VALUE;
            String changeDescription = dataNode.has(Constants.EMAIL_VAR_CHANGE_DESCRIPTION)
                    ? dataNode.get(Constants.EMAIL_VAR_CHANGE_DESCRIPTION).asText()
                    : Constants.DEFAULT_UNKNOWN_VALUE;
            String query = dataNode.has(Constants.EMAIL_VAR_QUERY) ? dataNode.get(Constants.EMAIL_VAR_QUERY).asText()
                    : Constants.DEFAULT_UNKNOWN_VALUE;

            emailService.sendAssetQueryChangeRequestEmail(
                    task.getReceiver(),
                    task.getSender(),
                    task.getAsset(),
                    ticketReference,
                    changeDescription,
                    query,
                    "asset-query-change-request");
        } catch (Exception e) {
            log.error("Failed to parse notification data for asset query change request email", e);
            throw new NotificationEmailException(
                    "Failed to send asset query change request email",
                    EmailType.ASSET_QUERY_CHANGE_REQUEST_NOTIFY.name(),
                    task.getId(),
                    e);
        }
    }

    private void sendAssetQueryChangeRequestApprovalEmail(NotificationTask task) throws Exception {
        ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());
        JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);

        if (dataNode == null) {
            throw new JsonParseException(null,
                    "Data node not found in asset query change request approval notification");
        }

        // Create AccessQueryDTO from notification data
        AccessQueryDTO queryDTO = new AccessQueryDTO();
        queryDTO.setApprovalStatus(ApprovalStatus.valueOf(
                dataNode.has(Constants.EMAIL_VAR_APPROVAL_STATUS)
                        ? dataNode.get(Constants.EMAIL_VAR_APPROVAL_STATUS).asText()
                        : "PENDING"));
        queryDTO.setTicketReference(dataNode.has(Constants.EMAIL_VAR_TICKET_REFERENCE)
                ? dataNode.get(Constants.EMAIL_VAR_TICKET_REFERENCE).asText()
                : "");
        queryDTO.setChangeDescription(dataNode.has(Constants.EMAIL_VAR_CHANGE_DESCRIPTION)
                ? dataNode.get(Constants.EMAIL_VAR_CHANGE_DESCRIPTION).asText()
                : "");
        queryDTO.setQuery(
                dataNode.has(Constants.EMAIL_VAR_QUERY) ? dataNode.get(Constants.EMAIL_VAR_QUERY).asText() : "");
        queryDTO.setRejectReason(dataNode.has(Constants.EMAIL_VAR_REJECT_REASON)
                ? dataNode.get(Constants.EMAIL_VAR_REJECT_REASON).asText()
                : "");

        emailService.sendAssetQueryChangeRequestApprovalEmail(
                task.getReceiver(),
                task.getSender(),
                task.getAsset(),
                queryDTO,
                "approval-asset-query-change-request");
    }

    private void sendApprovalAssetAccessRequestEmail(NotificationTask task) throws Exception {
        ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());

        String statusStr = getApprovalStatus(notificationNode);
        HashMap<String, String> credentials = extractCredentials(notificationNode);

        emailService.sendApprovalAssetAccessRequestEmail(
                task.getReceiver(),
                task.getSender(),
                task.getAsset(),
                "approval-asset-access-request",
                ApprovalStatus.valueOf(statusStr),
                credentials);
    }

    private void sendDeleteQueryNotifyEmail(NotificationTask task) throws Exception {
        ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());
        JsonNode dataNode = notificationNode.get("data");

        if (dataNode == null) {
            throw new JsonParseException(null, "Data node not found in DELETE query notification");
        }

        // Extract all the required fields for DELETE query alert
        String executorName = dataNode.has(Constants.EMAIL_VAR_EXECUTOR_NAME)
                ? dataNode.get(Constants.EMAIL_VAR_EXECUTOR_NAME).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;
        String executionTime = dataNode.has(Constants.EMAIL_VAR_EXECUTION_TIME)
                ? dataNode.get(Constants.EMAIL_VAR_EXECUTION_TIME).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;
        String tableName = dataNode.has(Constants.EMAIL_VAR_TABLE_NAME)
                ? dataNode.get(Constants.EMAIL_VAR_TABLE_NAME).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;
        String affectedRows = dataNode.has(Constants.EMAIL_VAR_AFFECTED_ROWS)
                ? dataNode.get(Constants.EMAIL_VAR_AFFECTED_ROWS).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;
        String query = dataNode.has(Constants.EMAIL_VAR_QUERY) ? dataNode.get(Constants.EMAIL_VAR_QUERY).asText() : "";
        String databaseType = dataNode.has(Constants.EMAIL_VAR_DATABASE_TYPE)
                ? dataNode.get(Constants.EMAIL_VAR_DATABASE_TYPE).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;
        String hostUrl = dataNode.has(Constants.EMAIL_VAR_HOST_URL)
                ? dataNode.get(Constants.EMAIL_VAR_HOST_URL).asText()
                : Constants.DEFAULT_UNKNOWN_VALUE;

        // Create the DTO
        DeleteQueryAlertData alertData = DeleteQueryAlertData.builder()
                .executorName(executorName)
                .executionTime(executionTime)
                .tableName(tableName)
                .affectedRows(affectedRows)
                .query(query)
                .databaseType(databaseType)
                .hostUrl(hostUrl)
                .build();

        emailService.sendDeleteQueryAlertEmail(
                task.getReceiver(),
                task.getAsset(),
                alertData,
                "asset-delete-query-notify");
    }

    private void sendInvitationEmail(NotificationTask task) throws Exception {
        // Parse notification message to extract auth provider info
        ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());
        JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);

        if (dataNode == null) {
            throw new JsonParseException(null, "Data node not found in invitation notification");
        }

        // Extract auth provider from notification data
        String authProvider = dataNode.has("authProvider") ? dataNode.get("authProvider").asText() : "GOOGLE";
        String tempPassword = dataNode.has("tempPassword") ? dataNode.get("tempPassword").asText() : "";
        
        // For SSO users, password is not required
        if (!"SSO".equalsIgnoreCase(authProvider) && tempPassword.isEmpty()) {
            throw new EmailException("Password can not be empty for non-SSO users");
        }
        
        // Determine email template based on auth provider
        String emailTmplFile = Constants.EMAIL_TEMPLATE_GOOGLE_INVITE;
        if ("KEYCLOAK".equalsIgnoreCase(authProvider)) {
            emailTmplFile = Constants.EMAIL_TEMPLATE_KEYCLOAK_INVITE;
        } else if ("SSO".equalsIgnoreCase(authProvider)) {
            emailTmplFile = Constants.EMAIL_TEMPLATE_SSO_INVITE;
        }
        
        User user = task.getReceiver();
        if (user != null && !tempPassword.isEmpty()) {
            user.setPassword(tempPassword);
        }


        // Send invitation email
        emailService.sendInvitationEmail(task.getReceiver(), emailTmplFile);
    }

    private void sendForgotPasswordEmail(NotificationTask task) throws Exception {
        // Parse notification message to extract reset link
        ObjectNode notificationNode = (ObjectNode) objectMapper.readTree(task.getNotificationMessage());
        JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);

        if (dataNode == null) {
            throw new JsonParseException(null, "Data node not found in forgot password notification");
        }

        // Extract reset link from notification data
        String resetLink = dataNode.has("resetLink") ? dataNode.get("resetLink").asText() : "";
        if (resetLink.isEmpty()) {
            throw new JsonParseException(null, "Reset link not found in forgot password notification data");
        }

        // Send forgot password email
        emailService.sendForgotPasswordEmail(task.getReceiver(), resetLink, "forgot-password");
    }

    protected String getApprovalStatus(ObjectNode notificationNode) throws JsonParseException {
        JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);
        if (dataNode == null) {
            throw new JsonParseException(null, "Data node not found in notification");
        }

        JsonNode statusNode = dataNode.get("approvalStatus");
        if (statusNode == null) {
            throw new JsonParseException(null, "Approval status not found in notification data");
        }

        String statusStr = statusNode.asText();
        if (statusStr == null || statusStr.isEmpty()) {
            throw new JsonParseException(null, "Approval status is empty");
        }

        return statusStr;
    }

    protected HashMap<String, String> extractCredentials(ObjectNode notificationNode) {
        HashMap<String, String> credentials = new HashMap<>();

        JsonNode dataNode = notificationNode.get(Constants.ACCESS_OBJECT_ATTR_DATA);
        if (dataNode == null) {
            return credentials;
        }

        if (dataNode.has(Constants.EMAIL_VAR_DB_USERNAME) && dataNode.has(Constants.EMAIL_VAR_DB_PASSWORD)) {
            String dbUsername = dataNode.get(Constants.EMAIL_VAR_DB_USERNAME).asText();
            String dbPassword = dataNode.get(Constants.EMAIL_VAR_DB_PASSWORD).asText();

            if (dbUsername != null && !dbUsername.isEmpty()) {
                credentials.put(Constants.EMAIL_VAR_DB_USERNAME, dbUsername);
            }
            if (dbPassword != null && !dbPassword.isEmpty()) {
                credentials.put(Constants.EMAIL_VAR_DB_PASSWORD, dbPassword);
            }
        }

        return credentials;
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
                .<NotificationTask, NotificationTask>chunk(chunkSize, transactionManager)
                .reader(notificationReader())
                .writer(notificationWriter())
                .taskExecutor(taskExecutor())
                .build();
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduleNotificationJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(notificationJob(), params);

            // Log statistics after each job run
            logProcessingStatistics();
        } catch (Exception e) {
            String errorMessage = "Error running notification job";
            log.error(errorMessage, e);
            throw new NotificationJobException(errorMessage, e);
        }
    }
}
