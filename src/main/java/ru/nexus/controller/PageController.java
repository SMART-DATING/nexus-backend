package ru.nexus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

  @GetMapping("/match/{id:[0-9]+}")
  public String chat() {
    return "forward:/index.html";
  }
}
