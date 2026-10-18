package dev.starterkit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentCatalogTest {

    @Test
    void acceptsPlainIdentifiers() {
        assertThat(DocumentCatalog.identifier("vector_store")).isEqualTo("vector_store");
        assertThat(DocumentCatalog.identifier("public")).isEqualTo("public");
    }

    @Test
    void rejectsAnythingThatCouldInjectSql() {
        assertThatThrownBy(() -> DocumentCatalog.identifier("vector_store; DROP TABLE x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentCatalog.identifier("a\"b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentCatalog.identifier("1table")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentCatalog.identifier(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
