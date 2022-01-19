package it.gov.pagopa.cgn.portal.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import it.gov.pagopa.cgn.portal.email.EmailNotificationFacade;
import it.gov.pagopa.cgn.portal.enums.BucketCodeExpiringThresholdEnum;
import it.gov.pagopa.cgn.portal.model.*;
import it.gov.pagopa.cgn.portal.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import it.gov.pagopa.cgn.portal.IntegrationAbstractTest;
import it.gov.pagopa.cgn.portal.TestUtils;
import it.gov.pagopa.cgn.portal.config.ConfigProperties;
import it.gov.pagopa.cgn.portal.enums.BucketCodeLoadStatusEnum;
import it.gov.pagopa.cgn.portal.filestorage.AzureStorage;
import it.gov.pagopa.cgn.portal.util.BucketLoadUtils;

@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class BucketServiceTest extends IntegrationAbstractTest {

    @Autowired
    private ConfigProperties configProperties;

    @Autowired
    private AzureStorage azureStorage;

    @Autowired
    private BucketCodeLoadRepository bucketCodeLoadRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private DiscountBucketCodeRepository discountBucketCodeRepository;

    @Autowired
    private DiscountBucketCodeSummaryRepository discountBucketCodeSummaryRepository;

    @Autowired
    private BucketLoadUtils bucketLoadUtils;

    @Autowired
    private BucketService bucketService;

    @Autowired
    private NotificationRepository notificationRepository;

    private AgreementEntity agreementEntity;
    private MockMultipartFile multipartFile;

    @BeforeEach
    void init() throws IOException {
        agreementEntity = agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        documentRepository.saveAll(TestUtils.createSampleDocumentList(agreementEntity));
        byte[] csv = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-codes.csv"));
        multipartFile = new MockMultipartFile("bucketload", "test-codes.csv", "text/csv", csv);

        BlobContainerClient documentContainerClient = new BlobContainerClientBuilder()
                .connectionString(getAzureConnectionString())
                .containerName(configProperties.getDocumentsContainerName()).buildClient();
        if (!documentContainerClient.exists()) {
            documentContainerClient.create();
        }
    }

    @Test
    void Create_CreatePendingBucketCodeLoad_Ok() {

        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountEntity.setId(1L);

        bucketService.createPendingBucketLoad(discountEntity);
        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.PENDING, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertNull(bucketCodeLoadEntity.getNumberOfCodes());
        Assertions.assertEquals(bucketCodeLoadEntity.hashCode(), bucketCodeLoadEntity.hashCode());
        Assertions.assertEquals(bucketCodeLoadEntity.toString(), bucketCodeLoadEntity.toString());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());

    }

    @Test
    void Create_SetRunningBucketCodeLoad_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());

        Assertions.assertTrue(bucketService.checkBucketLoadUID(discountEntity.getLastBucketCodeLoad().getUid()));

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository
                .findById(discountEntity.getLastBucketCodeLoad().getId()).orElseThrow();

        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.RUNNING, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.RUNNING.getCode(), bucketCodeLoadEntity.getStatus().getCode());
        Assertions.assertEquals(2, bucketCodeLoadEntity.getNumberOfCodes()); // mocked files has 2 codes
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertEquals(bucketCodeLoadEntity.getFileName(), bucketCodeLoadEntity.getFileName());
    }

    @Test
    void PerformBucketCodeStore_Ko() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());

        bucketService.performBucketLoad(discountEntity.getId());
        Assertions.assertFalse(azureStorage.existsDocument(discountEntity.getLastBucketCodeLoad().getUid() + ".csv"));

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FAILED, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FAILED.getCode(), bucketCodeLoadEntity.getStatus().getCode());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());
    }

    @Test
    void PerformBucketCodeStore_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        Assertions.assertTrue(bucketService.checkBucketLoadUID(discountEntity.getLastBucketCodeLoadUid()));
        discountEntity = bucketService.createPendingBucketLoad(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());
        bucketService.performBucketLoad(discountEntity.getId());

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FINISHED, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertEquals(2, bucketCodeLoadEntity.getNumberOfCodes());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());

        DiscountBucketCodeSummaryEntity discountBucketCodeSummaryEntity = discountBucketCodeSummaryRepository.findByDiscount(discountEntity);
        Assertions.assertEquals(2, discountBucketCodeSummaryEntity.getAvailableCodes());

        List<DiscountBucketCodeEntity> codes = discountBucketCodeRepository.findAllByDiscount(discountEntity);
        Assertions.assertFalse(codes.isEmpty());
    }

    @Test
    void Async_PerformBucketCodeStore_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        Assertions.assertTrue(azureStorage.existsDocument(discountEntity.getLastBucketCodeLoadUid() + ".csv"));
        bucketService.createPendingBucketLoad(discountEntity);
        bucketLoadUtils.storeCodesBucket(discountEntity.getId());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> discountBucketCodeRepository.count() == 2);

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertEquals(2, bucketCodeLoadEntity.getNumberOfCodes());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());
    }

    @Test
    void BucketCodeLoadData_Ok() {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        BucketCodeLoadEntity bucketCodeLoadEntity = new BucketCodeLoadEntity();
        bucketCodeLoadEntity.setDiscountId(discountEntity.getId());
        bucketCodeLoadEntity.setUid(discountEntity.getLastBucketCodeLoadUid());
        bucketCodeLoadEntity.setFileName(discountEntity.getLastBucketCodeLoadFileName());
        bucketCodeLoadEntity.setStatus(BucketCodeLoadStatusEnum.PENDING);
        bucketCodeLoadEntity.setNumberOfCodes(100L);

        BucketCodeLoadEntity inserted = bucketCodeLoadRepository.save(bucketCodeLoadEntity);

        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.PENDING, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoadUid(), bucketCodeLoadEntity.getUid());
        Assertions.assertEquals(bucketCodeLoadEntity.hashCode(), bucketCodeLoadEntity.hashCode());
        Assertions.assertEquals(bucketCodeLoadEntity.toString(), bucketCodeLoadEntity.toString());
        Assertions.assertEquals(bucketCodeLoadEntity.getNumberOfCodes(), inserted.getNumberOfCodes());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());

    }

    @Test
    void PerformBucketCodeDelete_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        Assertions.assertTrue(bucketService.checkBucketLoadUID(discountEntity.getLastBucketCodeLoadUid()));
        discountEntity = bucketService.createPendingBucketLoad(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());
        bucketService.performBucketLoad(discountEntity.getId());

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FINISHED, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertEquals(2, bucketCodeLoadEntity.getNumberOfCodes());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());

        List<DiscountBucketCodeEntity> codes = discountBucketCodeRepository.findAllByDiscount(discountEntity);
        Assertions.assertFalse(codes.isEmpty());
        Assertions.assertEquals(2, (long) codes.size());

        bucketService.deleteBucketCodes(discountEntity.getId());

        codes = discountBucketCodeRepository.findAllByDiscount(discountEntity);
        Assertions.assertTrue(codes.isEmpty());
    }

    @Test
    void Async_PerformBucketCodeDelete_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        Assertions.assertTrue(azureStorage.existsDocument(discountEntity.getLastBucketCodeLoadUid() + ".csv"));
        bucketService.createPendingBucketLoad(discountEntity);
        bucketLoadUtils.storeCodesBucket(discountEntity.getId());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> discountBucketCodeRepository.count() == 2);

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository.findById(discountEntity.getLastBucketCodeLoad().getId()).get();
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(discountEntity.getLastBucketCodeLoad().getId(), bucketCodeLoadEntity.getId());
        Assertions.assertNotNull(bucketCodeLoadEntity.getFileName());

        bucketLoadUtils.deleteBucketCodes(discountEntity.getId());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> discountBucketCodeRepository.count() == 0);
    }

    @Test
    void CheckDiscountBucketCodeSummaryExpirationAndSendNotification_NotificationNotSent() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeLoadUid(),
                multipartFile.getSize());

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());
        bucketService.performBucketLoad(discountEntity.getId());

        Assertions.assertTrue(bucketService.checkBucketLoadUID(discountEntity.getLastBucketCodeLoad().getUid()));

        var discountBucketCodeSummaryEntity = discountBucketCodeSummaryRepository.findByDiscount(discountEntity);
        bucketService.checkDiscountBucketCodeSummaryExpirationAndSendNotification(discountBucketCodeSummaryEntity.getId());

        // no notification should be sent because all codes are available
        var notifications = notificationRepository.findAll();
        Assertions.assertTrue(notifications.isEmpty());
    }

    @Test
    void CheckDiscountBucketCodeSummaryExpirationAndSendNotification_Percent50NotificationSent() throws IOException {
        testNotification(BucketCodeExpiringThresholdEnum.PERCENT_50);
    }

    @Test
    void CheckDiscountBucketCodeSummaryExpirationAndSendNotification_Percent25NotificationSent() throws IOException {
        testNotification(BucketCodeExpiringThresholdEnum.PERCENT_25);
    }

    @Test
    void CheckDiscountBucketCodeSummaryExpirationAndSendNotification_Percent10NotificationSent() throws IOException {
        testNotification(BucketCodeExpiringThresholdEnum.PERCENT_10);
    }

    private void testNotification(BucketCodeExpiringThresholdEnum threshold) throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);
        bucketService.createEmptyDiscountBucketCodeSummary(discountEntity);

        // load 10 codes by uploading 5 times a "2 code" bucket.
        for (var i = 0; i < 5; i++) {
            var bucketCodeLoadUid = TestUtils.generateDiscountBucketCodeUid();

            azureStorage.uploadCsv(multipartFile.getInputStream(), bucketCodeLoadUid, multipartFile.getSize());

            discountEntity.setLastBucketCodeLoadUid(bucketCodeLoadUid);
            discountRepository.save(discountEntity);

            bucketService.createPendingBucketLoad(discountEntity);
            bucketService.setRunningBucketLoad(discountEntity.getId());
            bucketService.performBucketLoad(discountEntity.getId());
        }

        Assertions.assertTrue(bucketService.checkBucketLoadUID(discountEntity.getLastBucketCodeLoad().getUid()));

        var discountBucketCodeSummaryEntity = discountBucketCodeSummaryRepository.findByDiscount(discountEntity);
        Assertions.assertEquals(10, discountBucketCodeSummaryEntity.getAvailableCodes());

        // use 100% - threshold codes
        int codeToUse = 10 - (int) Math.floor((float) 10 * threshold.getValue() / 100);
        log.info("Will use " + codeToUse + " codes.");
        discountBucketCodeRepository.findAllByDiscount(discountEntity).stream().limit(codeToUse).forEach(c -> {
            c.setIsUsed(true);
            discountBucketCodeRepository.save(c);
        });

        bucketService.checkDiscountBucketCodeSummaryExpirationAndSendNotification(discountBucketCodeSummaryEntity.getId());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> notificationRepository.count() >= 1);

        var notification = notificationRepository.findByKey(EmailNotificationFacade.createTrackingKeyForExiprationNotification(discountEntity, threshold));
        Assertions.assertNotNull(notification);
    }
}
