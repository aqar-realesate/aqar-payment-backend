package com.main.aqarpaymentbackend.controller;

import com.main.aqarpaymentbackend.service.ExcelService;
import com.main.aqarpaymentbackend.util.ReturnObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/excel")
public class ExcelController {

    private final ExcelService excelService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReturnObject> importExcelFile(@RequestParam("file") MultipartFile excelFile) throws IOException {

        return excelService.importExcelFile(excelFile);
    }
}
