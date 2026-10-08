package dev.sqlp.system;

import java.util.Arrays;
import java.util.List;

import dev.sqlp.judge.Verdict;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

	@GetMapping("/info")
	public SystemInfo info() {
		return new SystemInfo("sqlp-platform", Arrays.stream(Verdict.values()).map(Enum::name).toList());
	}

	public record SystemInfo(String name, List<String> verdicts) {
	}

}
