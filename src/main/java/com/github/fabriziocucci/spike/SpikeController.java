package com.github.fabriziocucci.spike;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RefreshScope
@RestController
public class SpikeController {

	@Value("${password}")
	private String password;
	
	@GetMapping("/password")
	public String password() {
		return this.password;
	}
	
}
