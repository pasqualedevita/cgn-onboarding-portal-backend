package it.gov.pagopa.cgn.portal.filestorage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import it.gov.pagopa.cgn.portal.config.ConfigProperties;
import it.gov.pagopa.cgn.portal.enums.DocumentTypeEnum;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class AzureStorage {

    private final ConfigProperties configProperties;

    private BlobContainerClient documentContainerClient;
    private BlobContainerClient imagesContainerClient;

    @Autowired
    public AzureStorage(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @PostConstruct
    protected void init(){

        documentContainerClient = new BlobContainerClientBuilder()
                .connectionString(configProperties.getAzureConnectionString())
                .containerName(configProperties.getDocumentsContainerName())
                .buildClient();

        imagesContainerClient = new BlobContainerClientBuilder()
                .connectionString(configProperties.getAzureConnectionString())
                .containerName(configProperties.getImagesContainerName())
                .buildClient();
    }

    public String storeDocument(String agreementId, DocumentTypeEnum documentType, InputStream content, long size) throws IOException {
        String blobName = agreementId + "/" + documentType.getCode().toLowerCase() + ".pdf";

        BlobClient blobClient = documentContainerClient.getBlobClient(blobName);
        try (ByteArrayInputStream contentIs = new ByteArrayInputStream(IOUtils.toByteArray(content))) {
            blobClient.upload(contentIs, size, true);
        }

        return configProperties.getDocumentsContainerName() + "/" + blobName;
    }


    public String storeImage(String agreementId, MultipartFile image) {
        String extension = FilenameUtils.getExtension(image.getOriginalFilename());
        try {
            return storeImage(agreementId, extension, image.getInputStream(), image.getSize());
        } catch (IOException e) {
           throw new RuntimeException(e);
        }
    }

    public String storeImage(String agreementId, String extension, InputStream content, long size){
        String blobName = "image-" + agreementId + "." + extension;

        BlobClient blobClient = imagesContainerClient.getBlobClient(blobName);
        try (ByteArrayInputStream contentIs = new ByteArrayInputStream(IOUtils.toByteArray(content))) {
            blobClient.upload(contentIs, size, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return configProperties.getImagesContainerName() + "/" + blobName;
    }

}
