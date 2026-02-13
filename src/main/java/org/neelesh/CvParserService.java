package org.neelesh;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class CvParserService {

    public String parseCv(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            
            if (document.isEncrypted()) {
                throw new IOException("The PDF is encrypted and cannot be parsed.");
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            
            if (text == null || text.trim().isEmpty()) {
                System.out.println("Warning: Parsed text is empty.");
                return "";
            }
            
            return text;
        } catch (IOException e) {
            System.err.println("Error parsing PDF: " + e.getMessage());
            throw e;
        }
    }
}