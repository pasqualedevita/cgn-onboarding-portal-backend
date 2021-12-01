package it.gov.pagopa.cgn.portal.controller;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.cgn.portal.IntegrationAbstractTest;
import it.gov.pagopa.cgn.portal.TestUtils;
import it.gov.pagopa.cgn.portal.config.ConfigProperties;
import it.gov.pagopa.cgn.portal.converter.discount.CreateDiscountConverter;
import it.gov.pagopa.cgn.portal.enums.AgreementStateEnum;
import it.gov.pagopa.cgn.portal.enums.DiscountCodeTypeEnum;
import it.gov.pagopa.cgn.portal.enums.SalesChannelEnum;
import it.gov.pagopa.cgn.portal.model.*;
import it.gov.pagopa.cgn.portal.repository.BucketCodeLoadRepository;
import it.gov.pagopa.cgn.portal.repository.DiscountBucketCodeRepository;
import it.gov.pagopa.cgn.portal.service.AgreementService;
import it.gov.pagopa.cgn.portal.service.DiscountService;
import it.gov.pagopa.cgn.portal.service.ProfileService;
import it.gov.pagopa.cgn.portal.util.CGNUtils;
import it.gov.pagopa.cgnonboardingportal.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AgreementApiTest extends IntegrationAbstractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private DiscountService discountService;

    @Autowired
    private ConfigProperties configProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CreateDiscountConverter createDiscountConverter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void beforeEach() {
        setOperatorAuth();
        ReflectionTestUtils.setField(configProperties, "bucketMinCsvRows", 0);
    }

    @Test
    void Create_CreateAgreement_Ok() throws Exception {
        this.mockMvc.perform(post(TestUtils.AGREEMENTS_CONTROLLER_PATH)).andDo(log()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value(AgreementState.DRAFTAGREEMENT.getValue()))
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.completedSteps").isEmpty());

    }

    @Test
    void GetAgreement_GetAgreementWithProfile_Ok() throws Exception {
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        this.mockMvc.perform(post(TestUtils.AGREEMENTS_CONTROLLER_PATH)).andDo(log()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value(AgreementState.DRAFTAGREEMENT.getValue()))
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.completedSteps").isArray()).andExpect(jsonPath("$.completedSteps", hasSize(1)))
                .andExpect(jsonPath("$.completedSteps[0]").value(CompletedStep.PROFILE.getValue()));
    }

    @Test
    void GetAgreement_GetAgreementWithProfileAndDiscount_Ok() throws Exception {
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountService.createDiscount(agreementEntity.getId(), discountEntity);
        this.mockMvc.perform(post(TestUtils.AGREEMENTS_CONTROLLER_PATH)).andDo(log()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value(AgreementState.DRAFTAGREEMENT.getValue()))
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.completedSteps").isArray()).andExpect(jsonPath("$.completedSteps", hasSize(2)));

    }

    @Test
    void GetAgreement_GetAgreementWithAllStepsCompleted_Ok() throws Exception {
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountService.createDiscount(agreementEntity.getId(), discountEntity);

        List<DocumentEntity> documentList = TestUtils.createSampleDocumentList(agreementEntity);
        documentRepository.saveAll(documentList);
        this.mockMvc.perform(post(TestUtils.AGREEMENTS_CONTROLLER_PATH)).andDo(log()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value(AgreementState.DRAFTAGREEMENT.getValue()))
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.completedSteps").isArray()).andExpect(jsonPath("$.completedSteps", hasSize(3)));

    }

    @Test
    void RequestApproval_RequestApproval_Ok() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        // creating discount
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountService.createDiscount(agreementEntity.getId(), discountEntity);

        List<DocumentEntity> documentList = TestUtils.createSampleDocumentList(agreementEntity);
        documentRepository.saveAll(documentList);

        this.mockMvc.perform(post(TestUtils.getAgreementApprovalPath(agreementEntity.getId()))).andDo(log())
                .andExpect(status().isNoContent());
    }

    @Test
    void RequestApproval_RequestApprovalWithoutDiscount_BadRequest() throws Exception {
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        this.mockMvc.perform(post(TestUtils.getAgreementApprovalPath(agreementEntity.getId()))).andDo(log())
                .andExpect(status().isBadRequest());
    }

    @Test
    void RequestApproval_RequestApprovalWithoutDocuments_BadRequest() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        // creating discount
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountService.createDiscount(agreementEntity.getId(), discountEntity);

        this.mockMvc.perform(post(TestUtils.getAgreementApprovalPath(agreementEntity.getId()))).andDo(log())
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Test
    void CreateDiscount_BucketLoading_WithRetry_Ok() throws Exception {
        // create an agreement
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);

        // create a profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity, SalesChannelEnum.ONLINE,
                DiscountCodeTypeEnum.BUCKET);
        profileService.createProfile(profileEntity, agreementEntity.getId());

        // upload a csv
        byte[] csv = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-codes.csv"));
        MockMultipartFile multipartFile = new MockMultipartFile("document", "test-codes.csv", "multipart/form-data",
                csv);
        createBlobDocument();

        // call api to upload csv
        MvcResult result = this.mockMvc.perform(multipart(TestUtils.getUploadBucketPath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isOk()).andReturn();

        // get blob uid
        BucketLoad bucketLoad = objectMapper.readValue(result.getResponse().getContentAsString(), BucketLoad.class);
        var blobUid = bucketLoad.getUid();

        // create a discount with bucket of codes
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountEntity.setLastBucketCodeFileUid(blobUid);
        CreateDiscount createDiscountDto = createDiscountConverter.toDto(discountEntity);

        // call api to create a discount
        this.mockMvc.perform(
                        post(TestUtils.getDiscountPath(agreementEntity.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDiscountDto)))
                .andDo(log()).andExpect(status().isOk());

        // we have to wait for all retries to complete
        Thread.sleep(2000);
    }

    @Test
    void PublishDiscount_PublishDiscount_Ok() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        // creating discount
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountEntity = discountService.createDiscount(agreementEntity.getId(), discountEntity).getDiscountEntity();
        documentRepository.saveAll(TestUtils.createSampleDocumentList(agreementEntity));
        agreementEntity = agreementService.requestApproval(agreementEntity.getId());
        agreementEntity.setState(AgreementStateEnum.APPROVED);
        agreementEntity.setStartDate(LocalDate.now());
        agreementEntity.setEndDate(CGNUtils.getDefaultAgreementEndDate());
        agreementEntity = agreementRepository.save(agreementEntity);

        this.mockMvc.perform(post(TestUtils.getDiscountPublishingPath(agreementEntity.getId(), discountEntity.getId())))
                .andDo(log()).andExpect(status().isNoContent());
    }

    @Test
    void PublishDiscount_PublishDiscountOfNotApprovedAgreement_BadRequest() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        // creating profile
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        // creating discount
        DiscountEntity discountEntity = TestUtils.createSampleDiscountEntity(agreementEntity);
        discountEntity = discountService.createDiscount(agreementEntity.getId(), discountEntity).getDiscountEntity();
        documentRepository.saveAll(TestUtils.createSampleDocumentList(agreementEntity));
        agreementEntity = agreementService.requestApproval(agreementEntity.getId());

        this.mockMvc.perform(post(TestUtils.getDiscountPublishingPath(agreementEntity.getId(), discountEntity.getId())))
                .andDo(log()).andExpect(status().isBadRequest());
    }

    @Test
    void UploadBucket_UploadValidBucketWithoutProfile_Ko() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        byte[] csv = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-codes.csv"));

        MockMultipartFile multipartFile = new MockMultipartFile("document", "test-codes.csv", "multipart/form-data",
                csv);
        createBlobDocument();
        this.mockMvc.perform(multipart(TestUtils.getUploadBucketPath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isBadRequest());
    }

    @Test
    void UploadBucket_UploadInvalidBucket_Ko() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        byte[] csv = "sample".getBytes();

        MockMultipartFile multipartFile = new MockMultipartFile("document", "test-codes.pdf", "multipart/form-data",
                csv);
        createBlobDocument();
        this.mockMvc.perform(multipart(TestUtils.getUploadBucketPath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isBadRequest());
    }

    @Test
    void UploadBucket_UploadValidBucket_Ok() throws Exception {
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        ProfileEntity profileEntity = TestUtils.createSampleProfileEntity(agreementEntity, SalesChannelEnum.ONLINE,
                DiscountCodeTypeEnum.BUCKET);
        profileService.createProfile(profileEntity, agreementEntity.getId());
        byte[] csv = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-codes.csv"));

        MockMultipartFile multipartFile = new MockMultipartFile("document", "test-codes.csv", "multipart/form-data",
                csv);
        createBlobDocument();
        this.mockMvc.perform(multipart(TestUtils.getUploadBucketPath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isOk());
    }

    @Test
    void UploadImage_UploadValidPngImage_Ok() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        byte[] image = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-image.png"));
        MockMultipartFile multipartFile = new MockMultipartFile("image", "test-image.png", "image/png", image);
        createBlobImage();
        this.mockMvc.perform(multipart(TestUtils.getUploadImagePath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isOk());
    }

    @Test
    void UploadImage_UploadInValidImage_GetInvalidImageErrorCode() throws Exception {
        // creating agreement (and user)
        AgreementEntity agreementEntity = this.agreementService.createAgreementIfNotExists(TestUtils.FAKE_ID);
        byte[] image = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test-image.png"));

        MockMultipartFile multipartFile = new MockMultipartFile("image", "test-image.pdf", "image/png", image);
        createBlobImage();
        this.mockMvc.perform(multipart(TestUtils.getUploadImagePath(agreementEntity.getId())).file(multipartFile))
                .andDo(log()).andExpect(status().isBadRequest())
                .andExpect(content().string(ImageErrorCode.INVALID_IMAGE_TYPE.getValue()));
    }

    private void createBlobImage() {
        BlobContainerClient imageContainer = new BlobContainerClientBuilder()
                .connectionString(getAzureConnectionString()).containerName(configProperties.getImagesContainerName())
                .buildClient();
        if (!imageContainer.exists()) {
            imageContainer.create();
        }
    }

    private void createBlobDocument() {
        BlobContainerClient documentContainer = new BlobContainerClientBuilder()
                .connectionString(getAzureConnectionString())
                .containerName(configProperties.getDocumentsContainerName()).buildClient();
        if (!documentContainer.exists()) {
            documentContainer.create();
        }
    }

}
