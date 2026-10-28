package eu.kanade.tachiyomi.extension.pt.blitztoons

import eu.kanade.tachiyomi.source.model.Filter

open class TagFilter(name: String, private val tagValues: Array<Pair<String, String>>) :
    Filter.Select<String>(name, tagValues.map { it.first }.toTypedArray()) {
    fun toSlug(): String = tagValues[state].second
}

class CategoryFilter : TagFilter(
    "Categoria",
    arrayOf(
        "Todos" to "",
        "Comics" to "comic",
        "Novels" to "novel",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
        "Shoujo" to "shoujo",
    )
)

class SortFilter : TagFilter(
    "Ordenar por",
    arrayOf(
        "Recentes" to "recentes",
        "Populares" to "populares",
        "Melhor nota" to "nota",
        "A–Z" to "alfabetica",
    )
)

class StatusFilter : TagFilter(
    "Status",
    arrayOf(
        "Todos" to "",
        "Em lançamento" to "ongoing",
        "Completo" to "completed",
        "Hiato" to "hiatus",
    )
)

class GenreFilter : TagFilter(
    "Gênero",
    arrayOf(
        "Todos" to "",
        "+18" to "mais-18",
        "Absolute Cinema" to "absolute-cinema",
        "Academia" to "academia",
        "Ação" to "acao",
        "Aconchegante" to "aconchegante",
        "Adaptação" to "adaptacao",
        "Adulto" to "adulto",
        "Adultério" to "adulterio",
        "Agentes" to "agentes",
        "Alta Fantasia" to "alta-fantasia",
        "Amadurecimento" to "amadurecimento",
        "Animais" to "animais",
        "Anti-Herói" to "anti-heroi",
        "Apocalipse" to "apocalipse",
        "Arte Marcial" to "artes-marciais",
        "Aventura" to "aventura",
        "Bullying" to "bullying",
        "Colorido" to "colorido",
        "Comédia" to "comedia",
        "Construção" to "construcao",
        "Cotidiano" to "cotidiano",
        "Crime" to "crime",
        "Culinária" to "culinaria",
        "Cultivo" to "cultivo",
        "Demônio" to "demonio",
        "Drama" to "drama",
        "Dungeon" to "dungeon",
        "Ecchi" to "ecchi",
        "Escolar" to "escolar",
        "Esporte" to "esporte",
        "Família" to "familia",
        "Fantasia" to "fantasia",
        "Fantasia Sombria" to "fantasia-sombria",
        "Ficção Científica" to "ficcao-cientifica",
        "Game Elements" to "game-elements",
        "Gore" to "gore",
        "Guerra" to "guerra",
        "Harém" to "harem",
        "Histórico" to "historico",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Jogos" to "jogos",
        "Josei" to "josei",
        "Luta" to "luta",
        "Maduro" to "maduro",
        "Máfia" to "mafia",
        "Magia" to "magia",
        "Manhua" to "manhua",
        "Manhwa" to "manhwa",
        "Masmorra" to "masmorra",
        "Mecha" to "mecha",
        "Medieval" to "medieval",
        "Militar" to "militar",
        "Mistério" to "misterio",
        "Monstro" to "monstro",
        "Murim" to "murim",
        "Necromante" to "necromante",
        "One-shot" to "one-shot",
        "Overpowered" to "overpower",
        "Psicológico" to "psicologico",
        "Realidade Virtual" to "realidade-virtual",
        "Reencarnação" to "reencarnacao",
        "Regressão" to "regressao",
        "Romance" to "romance",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Sistema de Jogo" to "sistema-de-jogo",
        "Slice of Life" to "slice-of-life",
        "Sobrenatural" to "sobrenatural",
        "Sobrevivência" to "sobrevivencia",
        "Superpoder" to "super-poderes",
        "Suspense" to "suspense",
        "Terror" to "terror",
        "Tragédia" to "tragedia",
        "Transmigração" to "transmigracao",
        "Viagem no Tempo" to "viagem-no-tempo",
        "Vingança" to "vinganca",
        "Zumbi" to "zumbi",
    )
)
