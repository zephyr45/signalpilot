package dev.signalpilot;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SignalPilotApplication {
  public static void main(String[] args) {
    SpringApplication.run(SignalPilotApplication.class, args);
  }

  @Bean
  ToolCallbackProvider incidentToolCallbacks(IncidentTools tools) {
    return MethodToolCallbackProvider.builder().toolObjects(tools).build();
  }
}
