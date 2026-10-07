package com.bombus.colmeia.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Locks the membership predicate in [MeliponarioAccessSql].
 * Adapters must reuse the fragments; they must not paste owner or join-table SQL.
 */
class MeliponarioAccessSqlDriftTest {

    @Test
    fun `adapters reuse membership fragments and do not paste access SQL`() {
        val fragmentSource = stripComments(readMain("MeliponarioAccessSql.kt"))
        val countSource = stripComments(readMain("ColmeiaCountAdapter.kt"))
        val ownedSource = stripComments(readMain("OwnedColmeiaAdapter.kt"))

        assertThat(fragmentSource).contains("meliponario_membro")
        assertThat(fragmentSource).contains("EXISTS")
        assertThat(fragmentSource).doesNotContain("owner_id")

        assertThat(countSource).contains("ACCESSIBLE_COLMEIA")
        assertThat(ownedSource).contains("ACCESSIBLE_COLMEIA")
        assertThat(ownedSource).contains("ACCESSIBLE_MELIPONARIO")

        assertThat(countSource).doesNotContain("owner_id")
        assertThat(ownedSource).doesNotContain("owner_id")
        assertThat(countSource).doesNotContain("meliponario_membro")
        assertThat(ownedSource).doesNotContain("meliponario_membro")
    }

    private fun readMain(fileName: String): String {
        val path = Path.of(
            "src/main/kotlin/com/bombus/colmeia/adapter/outbound/persistence",
            fileName,
        )
        assertThat(Files.exists(path)).describedAs("$path must exist from the Maven module root").isTrue()
        return Files.readString(path)
    }

    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//.*"""), " ")
}
