package dev.starterkit.ai.rag;

import dev.starterkit.ai.usage.UsageService;
import dev.starterkit.ai.usage.UsageSubjects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DocumentController {

    public record AskRequest(@NotBlank @Size(max = 2000) String question) {}

    public record AskResponse(String answer, List<String> sources, UsageService.AnswerUsage usage) {}

    private final DocumentService documentService;
    private final DocumentCatalog catalog;
    private final UsageService usage;

    public DocumentController(DocumentService documentService, DocumentCatalog catalog, UsageService usage) {
        this.documentService = documentService;
        this.catalog = catalog;
        this.usage = usage;
    }

    /** Uploads a document. Uploading a file with the same name again replaces the old version. */
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentService.IngestResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        return documentService.ingest(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/documents")
    public List<DocumentCatalog.StoredDocument> list() {
        return catalog.list();
    }

    @DeleteMapping("/documents/{source}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String source) {
        documentService.delete(source);
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request, HttpServletRequest http) {
        String subject = UsageSubjects.of(http);
        usage.reserve(subject);
        DocumentService.Answer answer = documentService.ask(request.question());
        return new AskResponse(answer.answer(), answer.sources(), usage.record(subject, answer.tokens()));
    }
}
