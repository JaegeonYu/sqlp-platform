package dev.sqlp.system;

import dev.sqlp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void infoListsVerdicts() throws Exception {
		this.mockMvc.perform(get("/api/system/info"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("sqlp-platform"))
			.andExpect(jsonPath("$.verdicts[0]").value("AC"));
	}

	@Test
	void healthIsUp() throws Exception {
		this.mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

}
