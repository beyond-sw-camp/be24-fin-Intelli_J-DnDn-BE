package org.example.dndndocumentmanagement.service;

import org.example.dndndocumentmanagement.dto.DocumentPreviewData;
import org.springframework.stereotype.Service;

@Service
public class DocumentPreviewService {

    public DocumentPreviewData previewData(String documentId) {
        return DocumentPreviewData.empty(documentId);
    }
}
