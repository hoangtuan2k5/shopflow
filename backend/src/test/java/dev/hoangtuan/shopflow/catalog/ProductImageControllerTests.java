package dev.hoangtuan.shopflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ActiveProfiles("test")
@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:images;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class ProductImageControllerTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client client;
  @Autowired ProductImageService service;
  @Autowired PlatformTransactionManager transactions;
  long id;
  byte[] png;

  @BeforeEach
  void setup() throws Exception {
    reset(client);
    jdbc.update("DELETE FROM shopflow.inventory_items");
    jdbc.update("DELETE FROM shopflow.products");
    jdbc.update(
        "INSERT INTO shopflow.products(name,price,active) VALUES ('Image test',10000,TRUE)");
    id = jdbc.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    png = output.toByteArray();
  }

  MockMultipartFile file(byte[] bytes) {
    return new MockMultipartFile("file", "untrusted.txt", "text/plain", bytes);
  }

  String key() {
    return jdbc.queryForObject(
        "SELECT image_key FROM shopflow.products WHERE id=?", String.class, id);
  }

  @Test
  void uploadsAndReplacesOnlyAfterCommit() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    doAnswer(
            invocation -> {
              assertThat(key()).isEqualTo("products/old.png");
              verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
              PutObjectRequest request = invocation.getArgument(0);
              assertThat(request.contentType()).isEqualTo("image/png");
              RequestBody body = invocation.getArgument(1);
              assertThat(body.contentStreamProvider().newStream().readAllBytes()).isEqualTo(png);
              return null;
            })
        .when(client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(png))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").isString());
    assertThat(key()).startsWith("products/" + id + "/").endsWith(".png");
    verify(client)
        .deleteObject(argThat((DeleteObjectRequest r) -> r.key().equals("products/old.png")));
    mvc.perform(get("/products/{id}", id))
        .andExpect(jsonPath("$.imageUrl").value("https://media.example.invalid/" + key()));
    mvc.perform(get("/products"))
        .andExpect(jsonPath("$.items[0].imageUrl").value("https://media.example.invalid/" + key()));
  }

  @Test
  void refusesInvalidFilesWithoutStorageCalls() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    for (byte[] bytes :
        new byte[][] {
          "fake png".getBytes(),
          "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(),
          Arrays.copyOf(png, 12)
        }) {
      mvc.perform(
              multipart("/products/{id}/image", id)
                  .file(file(bytes))
                  .with(user("owner").roles("SHOP_OWNER"))
                  .with(csrf()))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.status").value(415));
    }
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(new byte[2 * 1024 * 1024 + 1]))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isPayloadTooLarge());
    verifyNoInteractions(client);
    assertThat(key()).isEqualTo("products/old.png");
  }

  @Test
  void checksSizeBoundaryAndJpegApp2() throws Exception {
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(Arrays.copyOf(png, 2 * 1024 * 1024)))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isOk());
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpg", out);
    byte[] jpg = out.toByteArray();
    var app2 = new ByteArrayOutputStream();
    app2.write(jpg, 0, 2);
    byte[] profile =
        java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData();
    int segmentLength = 2 + 14 + profile.length;
    app2.write(
        new byte[] {(byte) 255, (byte) 226, (byte) (segmentLength >> 8), (byte) segmentLength});
    app2.write("ICC_PROFILE\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    app2.write(new byte[] {1, 1});
    app2.write(profile);
    app2.write(jpg, 2, jpg.length - 2);
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(app2.toByteArray()))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertThat(key()).endsWith(".jpg");
  }

  @Test
  void rejectsMissingEmptyOrDuplicateFiles() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    for (List<MockMultipartFile> files :
        List.of(
            List.<MockMultipartFile>of(),
            List.of(file(new byte[0])),
            List.of(file(png), file(png)))) {
      var request = multipart("/products/{id}/image", id);
      files.forEach(request::file);
      mvc.perform(request.with(user("owner").roles("SHOP_OWNER")).with(csrf()))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(client);
    assertThat(key()).isEqualTo("products/old.png");
  }

  @Test
  void protectsRoleAndCsrfAndProductExistence() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    mvc.perform(multipart("/products/{id}/image", id).file(file(png)).with(csrf()))
        .andExpect(status().isUnauthorized());
    for (String role : List.of("CUSTOMER", "WAREHOUSE")) {
      mvc.perform(
              multipart("/products/{id}/image", id)
                  .file(file(png))
                  .with(user("other").roles(role))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(png))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE shopflow.products SET active=FALSE WHERE id=?", id);
    for (long product : new long[] {id, Long.MAX_VALUE}) {
      mvc.perform(
              multipart("/products/{id}/image", product)
                  .file(file(png))
                  .with(user("owner").roles("SHOP_OWNER"))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }
    verifyNoInteractions(client);
    assertThat(key()).isEqualTo("products/old.png");
  }

  @Test
  void preservesOldImageOnStorageFailureAndCleansRollback() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    doThrow(new IllegalStateException("private storage details"))
        .when(client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(png))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Image storage unavailable"));
    assertThat(key()).isEqualTo("products/old.png");
    verify(client)
        .deleteObject(argThat((DeleteObjectRequest r) -> !r.key().equals("products/old.png")));
    reset(client);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              try {
                service.upload(id, List.of(file(png)));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
              tx.setRollbackOnly();
            });
    assertThat(key()).isEqualTo("products/old.png");
    verify(client)
        .deleteObject(argThat((DeleteObjectRequest r) -> !r.key().equals("products/old.png")));
  }

  @Test
  void rejectsNonMultipartAndExcessivePixelsAndExposesNullByDefault() throws Exception {
    mvc.perform(get("/products/{id}", id))
        .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.nullValue()));
    mvc.perform(
            post("/products/{id}/image", id)
                .contentType("application/json")
                .content("{}")
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isUnsupportedMediaType());
    byte[] oversized = png.clone();
    java.nio.ByteBuffer.wrap(oversized).putInt(16, 5000).putInt(20, 4000);
    var crc = new java.util.zip.CRC32();
    crc.update(oversized, 12, 17);
    java.nio.ByteBuffer.wrap(oversized).putInt(29, (int) crc.getValue());
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(oversized))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(client);
  }

  @Test
  void databaseFailureAfterUploadRollsBackAndCleansOnlyTheNewObject() {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    jdbc.execute(
        "ALTER TABLE shopflow.products ADD CONSTRAINT reject_new_image CHECK (image_key IS NULL OR image_key='products/old.png')");
    try {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> service.upload(id, List.of(file(png))))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(key()).isEqualTo("products/old.png");
      verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
      verify(client)
          .deleteObject(argThat((DeleteObjectRequest r) -> !r.key().equals("products/old.png")));
    } finally {
      jdbc.execute("ALTER TABLE shopflow.products DROP CONSTRAINT reject_new_image");
    }
  }

  @Test
  void cleanupFailureDoesNotUndoNewImage() throws Exception {
    jdbc.update("UPDATE shopflow.products SET image_key='products/old.png' WHERE id=?", id);
    doThrow(new IllegalStateException()).when(client).deleteObject(any(DeleteObjectRequest.class));
    mvc.perform(
            multipart("/products/{id}/image", id)
                .file(file(png))
                .with(user("owner").roles("SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertThat(key()).isNotEqualTo("products/old.png");
  }
}
