package com.voxumgrau.app

import java.text.Normalizer

/**
 * Pontuacao local em portugues brasileiro.
 *
 * Espelha a logica madura do fix_punctuation da bridge (jarvis_bridge.py -> QU_PALAVRAS,
 * SAUDACOES_FIX, QUEBRA_CLAUSULA) para que a transcricao do Android STT ja apareca
 * pontuada ("?" em perguntas, virgula em saudacoes, ponto em afirmacoes) no proprio
 * chat do app, sem depender do round-trip "corrigido" da bridge.
 *
 * Como o SpeechRecognizer entrega apenas texto corrido (sem prosodia), pergunta vs
 * afirmacao e inferida por pistas linguisticas:
 *  - Pergunta: palavras interrogativas iniciais (qual, onde, como...) e verbos/
 *    auxiliares (tem como, posso, pode, existe, voce, sera que...).
 *  - Afirmacao: todo o resto -> "." final.
 */
object VoxPontuacao {

    // Registro de abertura de pergunta em PT-BR (sem acentos, como na bridge)
    private val QU_PALAVRAS = Regex(
        "^(?:qual|quais|quem|onde|quando|como|por que|porque|pra que|para que|pq|oq|o que|" +
            "que horas|que dia|que hora|que nem|que que|quem que|onde que|quanto|quantos|quantas|" +
            "qto|qtos|qtas|sera que|sera|ta bom|tudo bem|tudo certo|vai|vamos|tem como|tem|" +
            "da pra|da para|existe|posso|pode|poderia|podemos|queria saber|queria|gostaria|" +
            "quero saber|preciso saber|me diga|me diz|me fale|me fala|me conta|conte|explica|" +
            "explique|sabe me dizer|sabe dizer|sabe se|sabe que|conhece|consegue|voce|voces|" +
            "e voce|quer que|quer saber|e possivel|e verdade|e mesmo|esta certo|ta certo|" +
            "esta tudo|quanto custa|vale a pena|funciona|precisa|devo|seria|por acaso|nao e)\\b",
        RegexOption.IGNORE_CASE
    )

    private val SAUDACOES_FIX = Regex(
        "^(oi|olá|ola|opa|e aí|e ai|e ae|fala|salve|hey|hei|bom dia|boa tarde|boa noite|bom te ver)\\b",
        RegexOption.IGNORE_CASE
    )

    private val QUEBRA_CLAUSULA = Regex(
        "\\b(tudo bem|tá bom|ta bom|tudo certo|ok|beleza|certo|entendi|entendeu|e você|e voce)\\b[,\\s]+",
        RegexOption.IGNORE_CASE
    )

    // Conjunções que pedem vírgula antes (aditivo ao da bridge, para clareza de leitura)
    private val CONJUNCOES_VIRGULA = listOf(
        "mas", "porem", "porém", "contudo", "todavia", "portanto",
        "logo", "entao", "então", "pois", "no entanto", "assim"
    )

    /**
     * Corrige pontuacao e capitalizacao do texto.
     * Devolve o texto ja pontuado, ou o original se vazio.
     */
    fun pontuar(texto: String): String {
        var t = texto.trim()
        if (t.isEmpty()) return t

        // Normaliza espacos duplos e pontuacao grudada
        t = t.replace(Regex("\\s+"), " ")
        t = t.replace(Regex("\\s+([,.;:?!])"), "$1")
        t = t.replace(Regex("([,.;:?!])(?=\\S)"), "$1 ")

        // Saudacao inicial vira abertura com virgula: "oi," "bom dia," ...
        val saude = SAUDACOES_FIX.find(t)
        if (saude != null) {
            val resto = t.substring(saude.range.last + 1).trimStart(' ', ',', ';')
            if (resto.isNotEmpty()) {
                t = t.substring(0, saude.range.last + 1).trimEnd() + ", " + resto
            }
        }

        // Marcas de assentimento/pausa quebram a clausula e viram sentenca
        t = QUEBRA_CLAUSULA.replace(t) { mo -> mo.groupValues[1] + ". " }

        // Primeira letra maiuscula; tambem depois de ".", "?" e "!"
        t = t.replace(Regex("(^|[.!?]\\s+)(\\w)")) { mo ->
            mo.groupValues[1] + mo.groupValues[2].uppercase()
        }

        fun pontuarParte(s: String): String {
            var p = s.trim(' ', ',', ';')
            if (p.isEmpty()) return ""
            if (p.endsWith("?") || p.endsWith("!") || p.endsWith(".")) return p
            return p + if (ehPergunta(p)) "?" else "."
        }

        t = t.split(Regex("(?<=[.!?])\\s+"))
            .map(::pontuarParte)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        // Virgula antes de conjunções adversativas/conclusivas (não no início)
        for (c in CONJUNCOES_VIRGULA) {
            val m = Regex("\\b" + Regex.escape(c) + "\\b").find(t)
            if (m != null && m.range.first > 0) {
                val antes = t.substring(0, m.range.first).trimEnd()
                val depois = t.substring(m.range.last + 1)
                if (!antes.endsWith(",") && !antes.endsWith(";") && depois.isNotBlank()) {
                    t = antes.trimEnd() + ", " + c + depois
                }
            }
        }

        return t.trim()
    }

    private fun ehPergunta(frase: String): Boolean {
        var s = semAcentos(frase)
        val mm = Regex(
            "^(?:oi|ola|opa|e ai|e ae|fala|salve|hey|hei|bom dia|boa tarde|boa noite|bom te ver)[,.]\\s+",
            RegexOption.IGNORE_CASE
        ).find(s)
        if (mm != null) s = s.substring(mm.range.last + 1)
        return QU_PALAVRAS.find(s) != null
    }

    private fun semAcentos(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}