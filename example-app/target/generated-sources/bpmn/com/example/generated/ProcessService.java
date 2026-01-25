package com.example.generated;

import java.lang.Object;
import java.lang.String;
import java.util.Map;
import org.activiti.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProcessService {
  private final RuntimeService runtimeService;

  @Autowired
  public ProcessService(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  public String startProcess(Map<String, Object> variables) {
    return runtimeService.startProcessInstanceByKey("Process_ObslugaFloty", variables).getId();
  }
}
