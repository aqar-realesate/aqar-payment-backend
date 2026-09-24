package com.main.aqarpaymentbackend.service;

import com.main.aqarpaymentbackend.model.PaymentDues;
import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.util.ReturnObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExcelService {

    private final PaymentDuesRepository paymentDuesRepository;

    @Transactional
    public ResponseEntity<ReturnObject> importExcelFile(MultipartFile excelFile) throws IOException {

        if (excelFile.isEmpty()) {
            return new ResponseEntity<>(new ReturnObject(
                    "Excel file is empty",
                    false,
                    null
            ), HttpStatus.BAD_REQUEST);
        }

        // Collect all dues before saving them
        List<PaymentDues> dues = new ArrayList<>();

        // Open the uploaded Excel file and close it automatically afterward
        try (Workbook workbook = new XSSFWorkbook(excelFile.getInputStream())) {

            // Read the first sheet in the workbook
            Sheet sheet = workbook.getSheetAt(0);

            // Go through each row in the sheet
            for (Row row : sheet) {

                // Skip Excel row 1 because it contains column names
                if (row.getRowNum() == 0) {
                    continue;
                }

                // Skip rows without a customer ID
                if (row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) == null) {
                    continue;
                }

                // Read the optional due date, an empty cell becomes null.
                Cell dateCell = row.getCell(5);
                LocalDateTime dueDate =
                        dateCell == null || dateCell.getCellType() == CellType.BLANK
                                ? null
                                : dateCell.getLocalDateTimeCellValue();

                // Read the optional description, an empty cell becomes null.
                Cell descriptionCell = row.getCell(6);
                String description =
                        descriptionCell == null || descriptionCell.getCellType() == CellType.BLANK
                                ? null
                                : descriptionCell.getStringCellValue();

                // Convert Excel cell values into a PaymentDues object.
                PaymentDues due = PaymentDues.builder()
                        .customerId((int) row.getCell(0).getNumericCellValue())
                        .unitId((int) row.getCell(1).getNumericCellValue())
                        .requestId((int) row.getCell(2).getNumericCellValue())
                        .amount(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()))
                        .currency(row.getCell(4).getStringCellValue())
                        .dueDate(dueDate)
                        .description(description)
                        .build();

                // Add this due to the list that will be saved.
                dues.add(due);
            }
        }

        // Save all Excel data rows to the payment_dues table.
        paymentDuesRepository.saveAll(dues);

        return new ResponseEntity<>(new ReturnObject(
                "Excel data has been imported successfully",
                true,
                dues
        ), HttpStatus.OK);
    }
}
