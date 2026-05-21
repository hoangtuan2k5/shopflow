package dev.hoangtuan.shopflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopflowBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
