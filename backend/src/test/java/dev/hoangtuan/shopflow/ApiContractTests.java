package dev.hoangtuan.shopflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
class ApiContractTests {
  @Autowired MockMvc mvc;

  @Test
  void publicContract() throws Exception {
    var json = JsonMapper.builder().build();
    var document =
        json.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var contract = json.createObjectNode();
    contract.set("paths", document.get("paths"));
    contract.set("schemas", document.path("components").get("schemas"));
    var expected =
        json.readTree(
            new ClassPathResource("api-contract.json").getContentAsString(StandardCharsets.UTF_8));
    assertThat(contract).isEqualTo(expected);
  }
}
