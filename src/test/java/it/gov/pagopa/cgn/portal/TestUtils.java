package it.gov.pagopa.cgn.portal;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.resourcemanager.apimanagement.fluent.models.SubscriptionContractInner;
import com.azure.resourcemanager.apimanagement.fluent.models.SubscriptionKeysContractInner;
import com.azure.resourcemanager.apimanagement.models.SubscriptionContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionKeysContract;
import com.azure.resourcemanager.apimanagement.models.SubscriptionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.gov.pagopa.cgn.portal.converter.discount.DiscountConverter;
import it.gov.pagopa.cgn.portal.enums.*;
import it.gov.pagopa.cgn.portal.model.*;
import it.gov.pagopa.cgn.portal.security.JwtAdminUser;
import it.gov.pagopa.cgn.portal.security.JwtAuthenticationToken;
import it.gov.pagopa.cgn.portal.security.JwtOperatorUser;
import it.gov.pagopa.cgnonboardingportal.model.*;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestUtils {

    public static final String AGREEMENTS_CONTROLLER_PATH = "/agreements"; // needed to bypass interceptor

    private static final String AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH = AGREEMENTS_CONTROLLER_PATH + "/";

    public static final String AGREEMENT_REQUESTS_CONTROLLER_PATH = "/agreement-requests/";

    public static final String AGREEMENT_APPROVED_CONTROLLER_PATH = "/approved-agreements/";

    public static final String PUBLIC_HELP_CONTROLLER_PATH = "/help";

    public static final String GEOLOCATION_PATH = "/geolocation-token";

    public static final String FAKE_ID = "FAKE_ID";

    public static String getProfilePath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/profile";
    }

    public static String getDiscountPath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/discounts";
    }

    public static String getDiscountPublishingPath(String agreementId, Long discountId) {
        return getDiscountPath(agreementId) + "/" + discountId + "/publishing";
    }

    public static String getDiscountUnpublishingPath(String agreementId, Long discountId) {
        return getDiscountPath(agreementId) + "/" + discountId + "/unpublishing";
    }

    public static String getDocumentPath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/documents";
    }

    public static String getAgreementApprovalPath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/approval";
    }

    public static String getUploadImagePath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/images";
    }

    public static String getUploadBucketPath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/discounts/bucket";
    }

    public static String getBackofficeDocumentPath(String agreementId) {
        return AGREEMENT_REQUESTS_CONTROLLER_PATH + agreementId + "/documents";
    }

    public static String getAuthenticatedHelpPath(String agreementId) {
        return AGREEMENTS_CONTROLLER_PATH_PLUS_SLASH + agreementId + "/help";
    }

    public static String getAgreementRequestsWithStatusFilterPath(String state, Optional<String> assigneeOpt) {
        StringBuilder path = new StringBuilder(AGREEMENT_REQUESTS_CONTROLLER_PATH);
        path.append("?states=").append(state);
        assigneeOpt.ifPresent(assignee -> path.append("&assignee=").append(assignee));
        return path.toString();
    }

    public static String getAgreementRequestsWithSortedColumn(BackofficeRequestSortColumnEnum columnEnum,
                                                              Sort.Direction direction) {
        return AGREEMENT_REQUESTS_CONTROLLER_PATH +
               "?sortColumn=" +
               columnEnum.getValue() +
               "&sortDirection=" +
               direction.name();
    }

    public static String getAgreementApprovalWithSortedColumn(BackofficeApprovedSortColumnEnum columnEnum,
                                                              Sort.Direction direction) {
        return AGREEMENT_APPROVED_CONTROLLER_PATH +
               "?sortColumn=" +
               columnEnum.getValue() +
               "&sortDirection=" +
               direction.name();
    }

    public static ReferentEntity createSampleReferent(ProfileEntity profileEntity) {
        ReferentEntity referentEntity = new ReferentEntity();
        referentEntity.setFirstName("FIRST_NAME");
        referentEntity.setLastName("LAST_NAME");
        referentEntity.setEmailAddress("referent.registry@pagopa.it");
        referentEntity.setTelephoneNumber("+390123456789");
        referentEntity.setProfile(profileEntity);
        referentEntity.setRole("CEO");
        return referentEntity;
    }

    public static ProfileEntity createSampleProfileWithCommonFields() {
        return createSampleProfileWithCommonFields(DiscountCodeTypeEnum.STATIC);
    }

    public static ProfileEntity createSampleProfileWithCommonFields(DiscountCodeTypeEnum discountCodeType) {
        ProfileEntity profileEntity = new ProfileEntity();
        profileEntity.setFullName("FULL_NAME");
        profileEntity.setName("NAME");
        profileEntity.setTaxCodeOrVat("abcdeghilmnopqrs");
        profileEntity.setPecAddress("pec.address@pagopa.it");
        profileEntity.setDescription("A Description");
        profileEntity.setReferent(createSampleReferent(profileEntity));
        profileEntity.setLegalRepresentativeTaxCode("abcdeghilmnopqrs");
        profileEntity.setLegalRepresentativeFullName("full name");
        profileEntity.setLegalOffice("legal office");
        profileEntity.setDiscountCodeType(discountCodeType);
        profileEntity.setTelephoneNumber("12345678");
        profileEntity.setAllNationalAddresses(true);
        return profileEntity;
    }

    public static UpdateProfile updatableOnlineProfileFromProfileEntity(ProfileEntity profileEntity,
                                                                        DiscountCodeType discountCodeType) {
        OnlineChannel salesChannel = new OnlineChannel();
        salesChannel.setChannelType(SalesChannelType.ONLINECHANNEL);
        salesChannel.setWebsiteUrl("anurl.com");
        salesChannel.setDiscountCodeType(discountCodeType);
        return updatableProfileFromProfileEntity(profileEntity, salesChannel);
    }

    public static UpdateProfile updatableOfflineProfileFromProfileEntity(ProfileEntity profileEntity) {
        Address address = new Address();
        address.setFullAddress("Via unavia, n.1, 30000, Veneto");

        OfflineChannel salesChannel = new OfflineChannel();
        salesChannel.setChannelType(SalesChannelType.OFFLINECHANNEL);
        salesChannel.setWebsiteUrl("anurl.com");
        salesChannel.setAddresses(Stream.of(address).collect(Collectors.toList()));
        salesChannel.setAllNationalAddresses(true);
        return updatableProfileFromProfileEntity(profileEntity, salesChannel);
    }

    public static UpdateProfile updatableProfileFromProfileEntity(ProfileEntity profileEntity,
                                                                  SalesChannel salesChannel) {
        UpdateReferent referent = new UpdateReferent();
        referent.setEmailAddress(profileEntity.getReferent().getEmailAddress());
        referent.setFirstName(profileEntity.getReferent().getFirstName());
        referent.setTelephoneNumber(profileEntity.getReferent().getTelephoneNumber());
        referent.setLastName(profileEntity.getReferent().getLastName());
        referent.setRole(profileEntity.getReferent().getRole());

        UpdateProfile updateProfile = new UpdateProfile();
        updateProfile.setDescription(profileEntity.getDescription());
        updateProfile.setSalesChannel(salesChannel);
        updateProfile.setName(profileEntity.getName());
        updateProfile.setLegalOffice(profileEntity.getLegalOffice());
        updateProfile.setReferent(referent);
        updateProfile.setPecAddress(profileEntity.getPecAddress());
        updateProfile.setTelephoneNumber(profileEntity.getTelephoneNumber());
        updateProfile.setLegalRepresentativeFullName(profileEntity.getLegalRepresentativeFullName());
        updateProfile.setLegalRepresentativeTaxCode(profileEntity.getLegalRepresentativeTaxCode());

        return updateProfile;
    }

    public static List<AddressEntity> createSampleAddress(ProfileEntity profileEntity) {
        AddressEntity addressEntity = new AddressEntity();
        addressEntity.setProfile(profileEntity);
        addressEntity.setFullAddress("GARIBALDI 1 00100 Rome RM");
        addressEntity.setLatitude(42.92439);
        addressEntity.setLongitude(12.50181);
        List<AddressEntity> list = new ArrayList<>(1);
        list.add(addressEntity);
        return list;
    }

    public static List<Address> createSampleAddressDto() {
        Address address = new Address();
        address.setFullAddress("GARIBALDI 1 00100 Rome RM");
        Coordinates coordinates = new Coordinates();
        coordinates.setLongitude(BigDecimal.valueOf(9.1890953));
        coordinates.setLatitude(BigDecimal.valueOf(45.489751));
        address.setCoordinates(coordinates);
        return Collections.singletonList(address);
    }

    public static AgreementEntity createSampleAgreementEntityWithCommonFields() {
        AgreementEntity agreementEntity = new AgreementEntity();
        agreementEntity.setId("agreement_id");
        agreementEntity.setImageUrl("image12345.png");
        return agreementEntity;
    }

    public static ProfileEntity createSampleProfileEntity(AgreementEntity agreementEntity) {
        return createSampleProfileEntity(agreementEntity, SalesChannelEnum.ONLINE, DiscountCodeTypeEnum.STATIC);
    }

    public static ProfileEntity createSampleProfileEntity(AgreementEntity agreementEntity,
                                                          SalesChannelEnum salesChannel,
                                                          DiscountCodeTypeEnum discountCodeType) {
        ProfileEntity profileEntity = createSampleProfileWithCommonFields(discountCodeType);
        profileEntity.setWebsiteUrl("https://www.pagopa.gov.it/");
        profileEntity.setSalesChannel(salesChannel);
        profileEntity.setAgreement(agreementEntity);
        return profileEntity;
    }

    public static UpdateProfile createSampleUpdateProfileWithCommonFields() {
        UpdateProfile profileDto = new UpdateProfile();
        profileDto.setName("name_dto");
        profileDto.setDescription("description_dto");
        profileDto.setPecAddress("myname.profile@pagopa.it");
        profileDto.setLegalRepresentativeTaxCode("abcdeghilmnopqrs");
        profileDto.setLegalRepresentativeFullName("full name");
        profileDto.setLegalOffice("legal office");
        profileDto.setTelephoneNumber("12345678");
        UpdateReferent updateReferent = new UpdateReferent();
        updateReferent.setFirstName("referent_first_name");
        updateReferent.setLastName("referent_last_name");
        updateReferent.setEmailAddress("referent.profile@pagopa.it");
        updateReferent.setTelephoneNumber("01234567");
        updateReferent.setRole("updatedRole");

        profileDto.setReferent(updateReferent);
        return profileDto;
    }

    public static it.gov.pagopa.cgnonboardingportal.publicapi.model.HelpRequest createSamplePublicApiHelpRequest() {
        it.gov.pagopa.cgnonboardingportal.publicapi.model.HelpRequest helpRequest
                = new it.gov.pagopa.cgnonboardingportal.publicapi.model.HelpRequest();
        helpRequest.setCategory(it.gov.pagopa.cgnonboardingportal.publicapi.model.HelpRequest.CategoryEnum.ACCESS);
        helpRequest.setTopic("a topic");
        helpRequest.setMessage("I need help");
        helpRequest.setEmailAddress("myname.help@pagopa.it");
        helpRequest.setLegalName("PagoPa");
        helpRequest.setReferentFirstName("Me");
        helpRequest.setReferentLastName("You");
        helpRequest.setRecaptchaToken("token");
        return helpRequest;
    }

    public static it.gov.pagopa.cgnonboardingportal.model.HelpRequest createSampleAuthenticatedHelpRequest() {
        it.gov.pagopa.cgnonboardingportal.model.HelpRequest helpRequest
                = new it.gov.pagopa.cgnonboardingportal.model.HelpRequest();
        helpRequest.setCategory(it.gov.pagopa.cgnonboardingportal.model.HelpRequest.CategoryEnum.ACCESS);
        helpRequest.setTopic("a topic");
        helpRequest.setMessage("I need help");
        return helpRequest;
    }

    public static DiscountEntity createSampleDiscountEntityWithStaticCode(AgreementEntity agreement,
                                                                          String staticCode) {
        DiscountEntity discountEntity = createSampleDiscountEntity(agreement);
        discountEntity.setStaticCode(staticCode);
        discountEntity.setLandingPageUrl(null);
        discountEntity.setLandingPageReferrer(null);
        discountEntity.setDiscountUrl("https://anurl.com");
        return discountEntity;
    }

    public static DiscountEntity createSampleDiscountEntityWithLandingPage(AgreementEntity agreement,
                                                                           String url,
                                                                           String referrer) {
        DiscountEntity discountEntity = createSampleDiscountEntity(agreement);
        discountEntity.setStaticCode(null);
        discountEntity.setLandingPageUrl(url);
        discountEntity.setLandingPageReferrer(referrer);
        return discountEntity;
    }

    public static DiscountEntity createSampleDiscountEntityWithBucketCodes(AgreementEntity agreement) {
        DiscountEntity discountEntity = createSampleDiscountEntity(agreement);
        discountEntity.setStaticCode(null);
        discountEntity.setLastBucketCodeLoadUid(generateDiscountBucketCodeUid());
        discountEntity.setLastBucketCodeLoadFileName("codes.csv");
        return discountEntity;
    }

    public static BucketCodeLoadEntity createDummyBucketLoadEntity(Long discountId) {
        BucketCodeLoadEntity bucketCodeLoadEntity = new BucketCodeLoadEntity();
        bucketCodeLoadEntity.setId(1L);
        bucketCodeLoadEntity.setUid(generateDiscountBucketCodeUid());
        bucketCodeLoadEntity.setFileName("codes.txt");
        bucketCodeLoadEntity.setDiscountId(discountId);
        bucketCodeLoadEntity.setStatus(BucketCodeLoadStatusEnum.PENDING);
        bucketCodeLoadEntity.setNumberOfCodes(100L);
        return bucketCodeLoadEntity;
    }

    public static DiscountEntity createSampleDiscountEntity(AgreementEntity agreement) {
        DiscountEntity discountEntity = createSampleDiscountEntityWithoutProduct(agreement);
        discountEntity.setProducts(getProductEntityList(discountEntity));
        return discountEntity;
    }

    public static DiscountEntity createSampleDiscountEntityWithoutProduct(AgreementEntity agreement) {
        DiscountEntity discountEntity = new DiscountEntity();
        discountEntity.setState(DiscountStateEnum.DRAFT);
        discountEntity.setName("discount_name");
        discountEntity.setDescription("discount_description");
        discountEntity.setDiscountValue(15);
        discountEntity.setCondition("discount_condition");
        discountEntity.setStartDate(LocalDate.now());
        discountEntity.setEndDate(LocalDate.now().plusMonths(6));
        discountEntity.setAgreement(agreement);
        discountEntity.setStaticCode("static_code");
        discountEntity.setVisibleOnEyca(false);
        discountEntity.setDiscountUrl("anurl.com");
        return discountEntity;
    }

    public static List<DiscountProductEntity> getProductEntityList(DiscountEntity discountEntity) {
        List<DiscountProductEntity> productEntityList = new ArrayList<>();
        DiscountProductEntity productEntity = new DiscountProductEntity();
        productEntity.setProductCategory(ProductCategoryEnum.TRAVELLING);
        productEntityList.add(productEntity);
        productEntity = new DiscountProductEntity();
        productEntity.setProductCategory(ProductCategoryEnum.SPORTS);
        productEntityList.add(productEntity);
        productEntityList.forEach(p -> p.setDiscount(discountEntity));
        return productEntityList;
    }

    public static String generateDiscountBucketCodeUid() {
        return UUID.randomUUID().toString();
    }

    public static UpdateDiscount updatableDiscountFromDiscountEntity(DiscountEntity discountEntity) {
        DiscountConverter discountConverter = new DiscountConverter();
        Discount discount = discountConverter.toDto(discountEntity);
        UpdateDiscount updateDiscount = new UpdateDiscount();
        updateDiscount.setName(discount.getName());
        updateDiscount.setDescription(discount.getDescription());
        updateDiscount.setCondition(discount.getCondition());
        updateDiscount.setStartDate(discount.getStartDate());
        updateDiscount.setEndDate(discount.getEndDate());
        updateDiscount.setStaticCode(discount.getStaticCode());
        updateDiscount.setLandingPageUrl(discount.getLandingPageUrl());
        updateDiscount.setLandingPageReferrer(discount.getLandingPageReferrer());
        updateDiscount.setProductCategories(discount.getProductCategories());
        updateDiscount.setLastBucketCodeLoadUid(discount.getLastBucketCodeLoadUid());
        updateDiscount.setLastBucketCodeLoadFileName(discount.getLastBucketCodeLoadFileName());
        return updateDiscount;
    }

    public static List<DocumentEntity> createSampleDocumentList(AgreementEntity agreementEntity) {
        List<DocumentEntity> documentList = new ArrayList<>();
        documentList.add(createDocument(agreementEntity, DocumentTypeEnum.AGREEMENT));
        documentList.add(createDocument(agreementEntity, DocumentTypeEnum.ADHESION_REQUEST));
        return documentList;
    }

    public static List<DocumentEntity> createSampleBackofficeDocumentList(AgreementEntity agreementEntity) {
        List<DocumentEntity> documentList = new ArrayList<>();
        documentList.add(createDocument(agreementEntity, DocumentTypeEnum.BACKOFFICE_AGREEMENT));
        return documentList;
    }

    public static DocumentEntity createDocument(AgreementEntity agreementEntity, DocumentTypeEnum documentTypeEnum) {
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setDocumentType(documentTypeEnum);
        documentEntity.setDocumentUrl("file_" + documentTypeEnum.getCode() + agreementEntity.getId());
        documentEntity.setAgreement(agreementEntity);
        return documentEntity;
    }

    public static String getJson(Object obj) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper.writeValueAsString(obj);
    }

    public static String API_TOKEN_PRIMARY_KEY = "primary-key-001";
    public static String API_TOKEN_SECONDARY_KEY = "secondary-key-001";

    public static ApiTokens createSampleApiTokens() {
        ApiTokens at = new ApiTokens();
        at.setPrimaryToken(API_TOKEN_PRIMARY_KEY);
        at.setSecondaryToken(API_TOKEN_SECONDARY_KEY);
        return at;
    }

    public static SubscriptionKeysContract createSubscriptionKeysContract() {
        return new SubscriptionKeysContractTestData(API_TOKEN_PRIMARY_KEY, API_TOKEN_SECONDARY_KEY);
    }

    public static SubscriptionContract createSubscriptionContract() {
        return new SubscriptionContractTestData(API_TOKEN_PRIMARY_KEY, API_TOKEN_SECONDARY_KEY);
    }

    public static class SubscriptionKeysContractTestData implements SubscriptionKeysContract {
        private String primaryKey;
        private String secondaryKey;

        public SubscriptionKeysContractTestData(String primaryKey, String secondaryKey) {
            this.primaryKey = primaryKey;
            this.secondaryKey = secondaryKey;
        }

        @Override
        public String primaryKey() {
            return primaryKey;
        }

        @Override
        public String secondaryKey() {
            return secondaryKey;
        }

        @Override
        public SubscriptionKeysContractInner innerModel() {
            return null;
        }
    }

    public static class SubscriptionContractTestData implements SubscriptionContract {
        private String primaryKey;
        private String secondaryKey;

        public SubscriptionContractTestData(String primaryKey, String secondaryKey) {
            this.primaryKey = primaryKey;
            this.secondaryKey = secondaryKey;
        }

        @Override
        public String id() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String type() {
            return null;
        }

        @Override
        public String ownerId() {
            return null;
        }

        @Override
        public String scope() {
            return null;
        }

        @Override
        public String displayName() {
            return null;
        }

        @Override
        public SubscriptionState state() {
            return null;
        }

        @Override
        public OffsetDateTime createdDate() {
            return null;
        }

        @Override
        public OffsetDateTime startDate() {
            return null;
        }

        @Override
        public OffsetDateTime expirationDate() {
            return null;
        }

        @Override
        public OffsetDateTime endDate() {
            return null;
        }

        @Override
        public OffsetDateTime notificationDate() {
            return null;
        }

        @Override
        public String primaryKey() {
            return primaryKey;
        }

        @Override
        public String secondaryKey() {
            return secondaryKey;
        }

        @Override
        public String stateComment() {
            return null;
        }

        @Override
        public Boolean allowTracing() {
            return null;
        }

        @Override
        public SubscriptionContractInner innerModel() {
            return null;
        }
    }

    public static HttpResponse createEmptyApimHttpResponse(int statusCode) {
        return new HttpResponse(null) {
            @Override
            public int getStatusCode() {
                return statusCode;
            }

            @Override
            public String getHeaderValue(String s) {
                return null;
            }

            @Override
            public HttpHeaders getHeaders() {
                return null;
            }

            @Override
            public Flux<ByteBuffer> getBody() {
                return null;
            }

            @Override
            public Mono<byte[]> getBodyAsByteArray() {
                return null;
            }

            @Override
            public Mono<String> getBodyAsString() {
                return null;
            }

            @Override
            public Mono<String> getBodyAsString(Charset charset) {
                return null;
            }
        };
    }

    public static void setOperatorAuth() {
        SecurityContextHolder.getContext()
                             .setAuthentication(new JwtAuthenticationToken(new JwtOperatorUser(TestUtils.FAKE_ID,
                                                                                               TestUtils.FAKE_ID)));
    }

    public static void setAdminAuth() {
        SecurityContextHolder.getContext()
                             .setAuthentication(new JwtAuthenticationToken(new JwtAdminUser(TestUtils.FAKE_ID)));
    }
}
