package it.gov.pagopa.cgn.portal.service;

import java.io.IOException;
import java.util.List;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import org.apache.commons.io.IOUtils;
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
import it.gov.pagopa.cgn.portal.model.AgreementEntity;
import it.gov.pagopa.cgn.portal.model.BucketCodeLoadEntity;
import it.gov.pagopa.cgn.portal.model.DiscountBucketCodeEntity;
import it.gov.pagopa.cgn.portal.model.DiscountEntity;
import it.gov.pagopa.cgn.portal.model.ProfileEntity;
import it.gov.pagopa.cgn.portal.repository.BucketCodeLoadRepository;
import it.gov.pagopa.cgn.portal.repository.DiscountBucketCodeRepository;
import it.gov.pagopa.cgn.portal.repository.DiscountRepository;

@SpringBootTest
@ActiveProfiles("dev")
class BucketServiceTest extends IntegrationAbstractTest {

    @Autowired
    private ConfigProperties configProperties;

    @Autowired
    private AzureStorage azureStorage;

    @Autowired
    private BucketService bucketService;

    @Autowired
    private BucketCodeLoadRepository bucketCodeLoadRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private DiscountBucketCodeRepository discountBucketCodeRepository;

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
        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository
                .findByDiscountIdAndUid(discountEntity.getId(), discountEntity.getLastBucketCodeFileUid());
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.PENDING, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeFileUid(), bucketCodeLoadEntity.getUid());
        Assertions.assertNull(bucketCodeLoadEntity.getNumberOfCodes());

    }

    @Test
    void Create_SetRunningBucketCodeLoad_Ok() {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());
        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository
                .findByDiscountIdAndUid(discountEntity.getId(), discountEntity.getLastBucketCodeFileUid());
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.RUNNING, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeFileUid(), bucketCodeLoadEntity.getUid());
    }

    @Test
    void PerformBucketCodeStore_Ko() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());

        bucketService.performBucketLoad(discountEntity.getId());

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository
                .findByDiscountIdAndUid(discountEntity.getId(), discountEntity.getLastBucketCodeFileUid());
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FAILED, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeFileUid(), bucketCodeLoadEntity.getUid());
    }

    @Test
    void PerformBucketCodeStore_Ok() throws IOException {
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntityWithBucketCodes(agreementEntity);
        discountRepository.save(discountEntity);

        azureStorage.uploadCsv(multipartFile.getInputStream(), discountEntity.getLastBucketCodeFileUid(),
                multipartFile.getSize());

        bucketService.createPendingBucketLoad(discountEntity);
        bucketService.setRunningBucketLoad(discountEntity.getId());
        bucketService.performBucketLoad(discountEntity.getId());

        BucketCodeLoadEntity bucketCodeLoadEntity = bucketCodeLoadRepository
                .findByDiscountIdAndUid(discountEntity.getId(), discountEntity.getLastBucketCodeFileUid());
        Assertions.assertNotNull(bucketCodeLoadEntity.getId());
        Assertions.assertEquals(discountEntity.getId(), bucketCodeLoadEntity.getDiscountId());
        Assertions.assertEquals(BucketCodeLoadStatusEnum.FINISHED, bucketCodeLoadEntity.getStatus());
        Assertions.assertEquals(discountEntity.getLastBucketCodeFileUid(), bucketCodeLoadEntity.getUid());

        List<DiscountBucketCodeEntity> codes = discountBucketCodeRepository.findAllByDiscount(discountEntity);
        Assertions.assertFalse(codes.isEmpty());
    }
}
