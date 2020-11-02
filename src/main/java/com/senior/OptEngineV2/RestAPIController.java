package com.senior.OptEngineV2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class RestAPIController {
	private static Engine x = new Engine();
	@GetMapping("/EngineServer")
	public String hello(@RequestParam(name = "date", defaultValue = "DateNoteSpecified")String date) {
		
		x.generate(date);
		return "Generated solution routes for: " + date;
	}
}
