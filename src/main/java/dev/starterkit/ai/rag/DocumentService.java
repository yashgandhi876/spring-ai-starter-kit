package dev.starterkit.ai.rag;

import dev.starterkit.ai.chat.ChatService;
import dev.starterkit.ai.config.StarterKitProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Loads documents into the vector store and answers questions from them, with cited sources. */
@Service
public class DocumentService {

    public static final String SOURCE_KEY = "source";
    static final String VERSION_KEY = "ingest_version";
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "txt", "md");
    static final String NO_DOCUMENTS_ANSWER =
            "I couldn't find anything relevant in your documents. Upload a document first, or rephrase the question.";

    public record IngestResult(String source, int chunks) {}

    public record Answer(String answer, List<String> sources) {}

    /** The file type is not one we can read. */
    public static class UnsupportedFileTypeException extends RuntimeException {
        public UnsupportedFileTypeException(String filename) {
            super("Unsupported file type: " + filename + ". Supported types: " + SUPPORTED_EXTENSIONS);
        }
    }

    /** The file was readable but had no text in it (for example a scanned PDF). */
    public static class EmptyDocumentException extends RuntimeException {
        public EmptyDocumentException(String filename) {
            super("No text found in " + filename + ". Scanned PDFs need OCR before upload.");
        }
    }

    private final VectorStore vectorStore;
    private final ChatService chatService;
    private final StarterKitProperties.Rag rag;
    private final TokenTextSplitter splitter;

    public DocumentService(VectorStore vectorStore, ChatService chatService, StarterKitProperties properties) {
        this.vectorStore = vectorStore;
        this.chatService = chatService;
        this.rag = properties.getRag();
        this.splitter = TokenTextSplitter.builder().withChunkSize(rag.getChunkSize()).build();
    }

    public IngestResult ingest(String filename, byte[] content) {
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileTypeException(filename);
        }
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        List<Document> pages = "pdf".equals(extension)
                ? new PagePdfDocumentReader(resource).get()
                : new TextReader(resource).get();

        List<Document> cleaned = pages.stream()
                .filter(page -> page.getText() != null)
                .map(page -> new Document(normalizeWhitespace(page.getText()), page.getMetadata()))
                .toList();

        String version = UUID.randomUUID().toString();
        List<Document> chunks = splitter.apply(cleaned).stream()
                .filter(chunk -> chunk.getText() != null && !chunk.getText().isBlank())
                .map(chunk -> {
                    chunk.getMetadata().put(SOURCE_KEY, filename);
                    chunk.getMetadata().put(VERSION_KEY, version);
                    return chunk;
                })
                .toList();
        if (chunks.isEmpty()) {
            throw new EmptyDocumentException(filename);
        }
        // Add the new version first, then remove older ones, so re-uploading a file replaces it
        // without duplicate chunks and without a moment where the document is missing.
        vectorStore.add(chunks);
        var b = new FilterExpressionBuilder();
        vectorStore.delete(b.and(b.eq(SOURCE_KEY, filename), b.ne(VERSION_KEY, version)).build());
        return new IngestResult(filename, chunks.size());
    }

    /** Removes every chunk of a document. Deleting a document that isn't there is not an error. */
    public void delete(String source) {
        vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_KEY, source).build());
    }

    public Answer ask(String question) {
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(rag.getTopK())
                .similarityThreshold(rag.getSimilarityThreshold())
                .build());
        if (matches == null || matches.isEmpty()) {
            return new Answer(NO_DOCUMENTS_ANSWER, List.of());
        }
        String context = matches.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
        List<String> sources = List.copyOf(matches.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault(SOURCE_KEY, "unknown")))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return new Answer(chatService.answerWithContext(question, context), sources);
    }

    /**
     * PDF text keeps the page's layout spacing ("The   warranty    lasts"), which wastes tokens
     * and weakens search. Collapse runs of spaces and tabs, trim each line, and keep at most one
     * blank line so paragraphs stay apart.
     */
    static String normalizeWhitespace(String text) {
        return text.replaceAll("[ \\t\\x0B\\f\\u00A0]+", " ")
                .replaceAll("(?m)^ +| +$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
