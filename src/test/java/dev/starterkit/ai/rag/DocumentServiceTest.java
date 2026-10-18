package dev.starterkit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.starterkit.ai.chat.ChatAnswer;
import dev.starterkit.ai.chat.ChatService;
import dev.starterkit.ai.chat.TokenUsage;
import dev.starterkit.ai.config.StarterKitProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class DocumentServiceTest {

    private VectorStore vectorStore;
    private ChatService chatService;
    private StarterKitProperties properties;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        chatService = mock(ChatService.class);
        properties = new StarterKitProperties();
        service = new DocumentService(vectorStore, chatService, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsTextFileAndTagsEveryChunkWithItsSource() {
        String text = "Refunds are processed within five working days. ".repeat(300);

        DocumentService.IngestResult result = service.ingest("policy.md", text.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> chunks = captor.getValue();
        assertThat(result.source()).isEqualTo("policy.md");
        assertThat(result.chunks()).isEqualTo(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.getMetadata()).containsEntry(DocumentService.SOURCE_KEY, "policy.md"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsPdf() throws IOException {
        byte[] pdf = pdfWithText("The warranty lasts two years from the date of purchase.");

        DocumentService.IngestResult result = service.ingest("Warranty.PDF", pdf);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(result.chunks()).isPositive();
        assertThat(captor.getValue().get(0).getText()).contains("warranty lasts two years");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reUploadAddsNewVersionThenRemovesOlderVersionsOfSameFile() {
        service.ingest("faq.txt", "Opening hours are 9 to 5.".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Filter.Expression> removed = ArgumentCaptor.forClass(Filter.Expression.class);
        InOrder order = inOrder(vectorStore);
        order.verify(vectorStore).add(added.capture());
        order.verify(vectorStore).delete(removed.capture());

        Object version = added.getValue().get(0).getMetadata().get(DocumentService.VERSION_KEY);
        assertThat(version).isNotNull();
        String filter = removed.getValue().toString();
        assertThat(filter).contains("faq.txt").contains(DocumentService.VERSION_KEY).contains(version.toString())
                .contains("NE").contains("AND");
    }

    @Test
    void deleteRemovesAllChunksOfThatSource() {
        service.delete("faq.txt");

        ArgumentCaptor<Filter.Expression> removed = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(removed.capture());
        assertThat(removed.getValue().toString()).contains(DocumentService.SOURCE_KEY).contains("faq.txt");
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        assertThatThrownBy(() -> service.ingest("photo.png", new byte[] {1, 2, 3}))
                .isInstanceOf(DocumentService.UnsupportedFileTypeException.class)
                .hasMessageContaining("photo.png");
        assertThatThrownBy(() -> service.ingest("noextension", new byte[] {1}))
                .isInstanceOf(DocumentService.UnsupportedFileTypeException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void rejectsDocumentWithNoText() {
        assertThatThrownBy(() -> service.ingest("blank.txt", "   \n\n  ".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentService.EmptyDocumentException.class);
        verify(vectorStore, never()).add(any());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void answersWithoutCallingTheModelWhenNothingMatches() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        DocumentService.Answer answer = service.ask("What is the refund policy?");

        assertThat(answer.answer()).isEqualTo(DocumentService.NO_DOCUMENTS_ANSWER);
        assertThat(answer.sources()).isEmpty();
        verifyNoInteractions(chatService);
    }

    @Test
    void answersFromMatchesAndListsEachSourceOnce() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Refunds take five days.", Map.of(DocumentService.SOURCE_KEY, "policy.md")),
                new Document("Refunds need a receipt.", Map.of(DocumentService.SOURCE_KEY, "policy.md")),
                new Document("Support is open 9 to 5.", Map.of(DocumentService.SOURCE_KEY, "faq.txt"))));
        when(chatService.answerWithContext(anyString(), anyString())).thenReturn(new ChatAnswer("Five days, with a receipt.", TokenUsage.NONE));

        DocumentService.Answer answer = service.ask("How long do refunds take?");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(chatService).answerWithContext(org.mockito.ArgumentMatchers.eq("How long do refunds take?"), context.capture());
        assertThat(context.getValue()).contains("Refunds take five days.", "Refunds need a receipt.", "Support is open 9 to 5.");
        assertThat(answer.answer()).isEqualTo("Five days, with a receipt.");
        assertThat(answer.sources()).containsExactly("policy.md", "faq.txt");
    }

    @Test
    void dropsDocumentsScoringFarBelowTheBestMatch() {
        // Real nomic-embed-text scores for "How much is the Pro plan?" against two uploaded documents
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                scored("The Pro plan costs 1499 rupees per month.", "pricing.md", 0.724),
                scored("Returns are accepted within 17 days.", "handbook.pdf", 0.382)));
        when(chatService.answerWithContext(anyString(), anyString())).thenReturn(new ChatAnswer("1499 rupees per month.", TokenUsage.NONE));

        DocumentService.Answer answer = service.ask("How much is the Pro plan?");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(chatService).answerWithContext(anyString(), context.capture());
        assertThat(context.getValue()).isEqualTo("The Pro plan costs 1499 rupees per month.");
        assertThat(answer.sources()).containsExactly("pricing.md");
    }

    @Test
    void keepsEveryDocumentWithinTheScoreGapIncludingTheBoundary() {
        // In doubles 0.77 - 0.2 is 0.5700000000000001, so the boundary needs care
        properties.getRag().setMaxScoreGap(0.2);
        service = new DocumentService(vectorStore, chatService, properties);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                scored("Refunds take five days.", "policy.md", 0.77),
                scored("Refund requests go to support.", "faq.txt", 0.57),
                scored("The office has a cafe.", "office.md", 0.56)));
        when(chatService.answerWithContext(anyString(), anyString())).thenReturn(new ChatAnswer("Five days.", TokenUsage.NONE));

        DocumentService.Answer answer = service.ask("How do refunds work?");

        assertThat(answer.sources()).containsExactly("policy.md", "faq.txt");
    }

    @Test
    void scoreGapOfOneKeepsEveryMatch() {
        properties.getRag().setMaxScoreGap(1.0);
        service = new DocumentService(vectorStore, chatService, properties);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                scored("The Pro plan costs 1499 rupees per month.", "pricing.md", 0.724),
                scored("Returns are accepted within 17 days.", "handbook.pdf", 0.382)));
        when(chatService.answerWithContext(anyString(), anyString())).thenReturn(new ChatAnswer("1499 rupees per month.", TokenUsage.NONE));

        assertThat(service.ask("How much is the Pro plan?").sources()).containsExactly("pricing.md", "handbook.pdf");
    }

    @Test
    void keepsMatchesThatHaveNoScore() {
        assertThat(DocumentService.closeToBestMatch(List.of(
                        scored("a", "a.md", 0.9),
                        new Document("b", Map.of(DocumentService.SOURCE_KEY, "b.md")),
                        scored("c", "c.md", 0.1)), 0.15))
                .extracting(Document::getText)
                .containsExactly("a", "b");
    }

    private static Document scored(String text, String source, double score) {
        return Document.builder().text(text).metadata(Map.of(DocumentService.SOURCE_KEY, source)).score(score).build();
    }

    @Test
    void passesTheModelsTokenUsageThroughForCostTracking() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Refunds take five days.", Map.of(DocumentService.SOURCE_KEY, "policy.md"))));
        when(chatService.answerWithContext(anyString(), anyString()))
                .thenReturn(new ChatAnswer("Five days.", new TokenUsage(120, 30)));

        assertThat(service.ask("How long?").tokens()).isEqualTo(new TokenUsage(120, 30));
    }

    @Test
    void noMatchUsesNoTokens() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.ask("Anything?").tokens()).isEqualTo(TokenUsage.NONE);
    }

    @Test
    void usesConfiguredTopKAndSimilarityThreshold() {
        properties.getRag().setTopK(7);
        properties.getRag().setSimilarityThreshold(0.65);
        service = new DocumentService(vectorStore, chatService, properties);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.ask("anything");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("anything");
        assertThat(captor.getValue().getTopK()).isEqualTo(7);
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.65);
    }

    @Test
    void normalizesLayoutWhitespaceButKeepsParagraphs() {
        assertThat(DocumentService.normalizeWhitespace("  The   warranty\t lasts  \n\n\n\nTwo  years  "))
                .isEqualTo("The warranty lasts\n\nTwo years");
    }

    @Test
    void readsExtensionsCaseInsensitively() {
        assertThat(DocumentService.extensionOf("A.PDF")).isEqualTo("pdf");
        assertThat(DocumentService.extensionOf("notes.v2.md")).isEqualTo("md");
        assertThat(DocumentService.extensionOf(null)).isEmpty();
        assertThat(DocumentService.extensionOf("README")).isEmpty();
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
