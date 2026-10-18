package dev.starterkit.ai.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.starterkit.ai.web.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DocumentControllerTest {

    private DocumentService documentService;
    private DocumentCatalog catalog;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        catalog = mock(DocumentCatalog.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentService, catalog))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void uploadReturnsCreatedWithChunkCount() throws Exception {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        when(documentService.ingest(eq("notes.txt"), any())).thenReturn(new DocumentService.IngestResult("notes.txt", 3));

        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "notes.txt", "text/plain", body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("notes.txt"))
                .andExpect(jsonPath("$.chunks").value(3));
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The uploaded file is empty"));
        verifyNoInteractions(documentService);
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        mvc.perform(multipart("/api/documents"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsUnsupportedTypeAs415() throws Exception {
        when(documentService.ingest(eq("photo.png"), any()))
                .thenThrow(new DocumentService.UnsupportedFileTypeException("photo.png"));

        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported file type"));
    }

    @Test
    void reportsDocumentWithoutTextAs422() throws Exception {
        when(documentService.ingest(eq("scan.pdf"), any()))
                .thenThrow(new DocumentService.EmptyDocumentException("scan.pdf"));

        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] {1})))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void askReturnsAnswerWithSources() throws Exception {
        when(documentService.ask("What is covered?"))
                .thenReturn(new DocumentService.Answer("Parts and labour.", List.of("warranty.pdf")));

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"What is covered?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Parts and labour."))
                .andExpect(jsonPath("$.sources[0]").value("warranty.pdf"));
    }

    @Test
    void rejectsRequestThatIsNotMultipart() throws Exception {
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentService);
    }

    @Test
    void listsStoredDocuments() throws Exception {
        when(catalog.list()).thenReturn(List.of(new DocumentCatalog.StoredDocument("faq.txt", 2)));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("faq.txt"))
                .andExpect(jsonPath("$[0].chunks").value(2));
    }

    @Test
    void deletesDocumentByName() throws Exception {
        mvc.perform(delete("/api/documents/{source}", "old policy.pdf"))
                .andExpect(status().isNoContent());
        verify(documentService).delete("old policy.pdf");
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentService);
    }
}
