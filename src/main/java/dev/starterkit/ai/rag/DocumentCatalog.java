package dev.starterkit.ai.rag;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lists the documents in the pgvector table. The VectorStore API can search, add and delete,
 * but cannot list what it holds, so this reads the table directly.
 */
@Component
public class DocumentCatalog {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    public record StoredDocument(String source, int chunks) {}

    private final JdbcTemplate jdbc;
    private final String table;

    public DocumentCatalog(JdbcTemplate jdbc,
                           @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schema,
                           @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String table) {
        this.jdbc = jdbc;
        // Identifiers can't be bind parameters, so only plain names are allowed into the SQL
        this.table = identifier(schema) + "." + identifier(table);
    }

    public List<StoredDocument> list() {
        return jdbc.query(
                "SELECT metadata->>'" + DocumentService.SOURCE_KEY + "' AS source, count(*) AS chunks FROM " + table
                        + " GROUP BY 1 ORDER BY 1",
                (rs, i) -> new StoredDocument(rs.getString("source"), rs.getInt("chunks")));
    }

    static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid schema or table name: " + name);
        }
        return name;
    }
}
