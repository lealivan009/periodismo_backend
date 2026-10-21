package com.authbase.api;

import com.authbase.error.ApiException;
import com.authbase.service.storage.UploadedFile;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;

final class Uploads {

    private Uploads() {
    }

    static UploadedFile read(FileUpload upload) {
        if (upload == null || upload.filePath() == null) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(upload.filePath());
            if (bytes.length == 0) {
                return null;
            }
            return new UploadedFile(upload.fileName(), upload.contentType(), bytes);
        } catch (IOException exception) {
            throw ApiException.badRequest("UPLOAD_FAILED", "No se pudo leer el archivo");
        }
    }
}
