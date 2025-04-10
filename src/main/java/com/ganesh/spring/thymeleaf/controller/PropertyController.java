// src/main/java/com/example/controller/PropertyController.java
package com.ganesh.spring.thymeleaf.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PropertyController {

    @Autowired
    private PropertyService propertyService;

    @GetMapping("/")
    public String showFileSelection(Model model) {
        //model.addAttribute("properties", propertyService.getProperties());

        return "file";
    }
    @PostMapping("/load-properties")
    public String loadProperties(@RequestParam String filePath, Model model) throws Exception {
        propertyService.loadProperties(filePath);
        model.addAttribute("properties", propertyService.getProperties());
        model.addAttribute("filePath", filePath);
        return "index";
    }

    @PostMapping("/update")
    public String updateProperty(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam String filePath,
            Model model) throws Exception {
        propertyService.updateProperty(key, value);
        model.addAttribute("properties", propertyService.getProperties());
        model.addAttribute("filePath", filePath);
        model.addAttribute("updatedKey", key);
        model.addAttribute("updateStatus", "Success");
        return "index";
    }
}