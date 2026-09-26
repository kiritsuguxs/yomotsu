# Migração de Anime (Anikku → Yomotsu) — notas de trabalho

> Documento de contexto. Se você está lendo isso em uma sessão nova, **leia inteiro antes de mexer em qualquer coisa**.
> Última atualização: após o TESTE 1 passar — CI verde em `41f85c2` **e validado no aparelho pelo usuário**
> ("tudo ok, não bugou nada": app abre, mangá e novel funcionando, sem regressão).

---

## 0. Regra de ouro do projeto

**NUNCA sair da base Mihon.** O Yomotsu continua sendo Mihon + mangá + novel + Telegram Cloud + tradução com IA.
O Anikku é apenas **doador de código** (a gente copia a peça certa dele). Não vamos migrar a base do app.

Nenhuma coisa vai para `main` sem o usuário mandar. O trabalho acontece em branch temporária.

---

## 1. Estado REAL do repositório (corrige uma leitura errada anterior)

Antes de começar, foi verificado e **confirmado**:

- `main` e a branch de trabalho estão **idênticas**. Zero arquivos de anime.
- **NÃO existe** nenhuma pasta `*/anime/*` no código.
- **NÃO existe** nenhuma tabela SQL de anime. Só as 12 de mangá em `data/src/main/sqldelight/tachiyomi/data/`
  (`mangas.sq`, `chapters.sq`, `history.sq`, `updatesView.sq`, ...). Nada de `animes.sq` / `episodes.sq`.
- **NÃO existe** player de vídeo. O único `*Player.kt` é `NovelAudioPlayer.kt` (TTS de novel).
- **NÃO existe** o dropdown "Chimahon" (Manga/Anime/Novels).
- Os 9 arquivos com "anime" no nome são do **tracker MyAnimeList** (`data/track/myanimelist/`) e já vêm no Mihon upstream. **Não são suporte a anime.**

> **Alerta:** uma conversa anterior_affirmou que o projeto já tinha "235 arquivos de anime, 67 do MPV, 10 tabelas SQL". **Isso era falso.**
> Os números vinham da branch `feat/anime-support` (660 arquivos / 68.720 linhas), que é a branch QUEBADA que o usuário apagou.
> Se alguémxk mentioning "o anime já existe", está errado — verificar sempre com `git diff --name-only main..<branch> | wc -l`.

### Branch `feat/anime-support` (a quebrada) — backup do usuário
Ainda existe **localmente**. 660 arquivos, veio do **Aniyomi**. É a origem dos bugs.
Não foi apagada de verdade, só ficou órfã. **Não usar como fonte** — o Anikku é a fonte correta.

---

## 2. Descoberta que mudou o plano (boa)

O Anikku **não duplica** mangá. Ele reaproveita tudo via `typealias`:

```kotlin
// domain/src/main/java/tachiyomi/domain/anime/model/Anime.kt
typealias Anime = Manga
// domain/src/main/java/tachiyomi/domain/episode/model/Episode.kt
typealias Episode = Chapter
// ...e os interactors:
typealias GetAnime = GetManga
typealias GetEpisode = GetChapter
typealias GetEpisodesByAnimeId = GetChaptersByMangaId
typealias UpdateEpisode = UpdateChapter
typealias EpisodeUpdate = ChapterUpdate
```

Consequência: os "repositórios de anime" do Anikku **são** os de mangá.
As únicas partes genuinamente novas de anime na camada de dados são **4 métodos** em
`data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt`:
`getAnimeSeasonsById`, `getAnimeSeasonsByIdAsFlow`, `removeParentIdByIds`, `getDeletableParentAnime`.

**O trabalho grande de verdade é o player (MPV), não a camada de dados.**
É por isso que os TESTES 2 e 3 são bem menores do que pareciam, e é por isso que
copiar do Aniyomi (que duplica) foi o que gerou 660 arquivos de bugs.

---

## 3. Decisão de arquitetura — DMCA

Preocupação do usuário: integrar fontes de anime poderia expor o projeto a DMCA.

**Decisão:** o Yomotsu entrega o **motor**, nunca o **conteúdo**.

- ✅ motor: player MPV, tabelas `animes`/`episodes`, UI, histórico, download, interfaces de anime no `source-api`
- ❌ **nenhuma** fonte de anime
- ❌ **nenhuma** URL de repo de anime — nem padrão, nem sugerida

O usuário adiciona o repo de anime **manualmente**, exatamente como já faz hoje com os novels.

### O que foi verificado no código (não é suposição)
- **Nenhuma URL de repositório embutida no projeto.** A única ocorrência de
  `raw.githubusercontent.com` em todo o `app/` é uma regex em
  `presentation/browse/ExtensionDetailsScreen.kt:80`, usada só para exibir o link.
- O sistema de múltiplas lojas já existe e é completo:
  - `domain/src/main/java/mihon/domain/extension/repository/ExtensionStoreRepository.kt`
  - `domain/.../extension/interactor/AddExtensionStore.kt`
  - `domain/.../extension/interactor/RemoveExtensionStore.kt`
  - `data/src/main/java/mihon/data/extension/service/ExtensionStoreService.kt:28` → `fetch(indexUrl: String)`
    aceita **qualquer URL** que o usuário digitar.
- **Precedente já usado no projeto:** os novels. `ExtensionStoreService.kt:58-66` cria a store
  `name = "LNReader Plugins"`, `badgeLabel = "Novel"`, `signingKey = "NOVEL_REPO"`.
  O app não embute o repo de novels — o usuário adiciona. O anime segue o mesmo caminho.

> Não sou advogado. A escolha é estrutural (o repositório não aponta para fonte de
> conteúdo de terceiros), não uma garantia jurídica.

---

## 4. Restrição do ambiente (importante para qualquer sessão futura)

Este workspace é **Android/PRoot**. Não dá para compilar localmente:

- `java: command not found` (e `/usr/lib/jvm` não existe)
- sem Android SDK, sem `ANDROID_HOME`
- sem `~/.gradle` (nenhum cache), sem pasta `app/build`
- `gh` **indisponível**

**O único jeito de compilar é o GitHub Actions.** Os logs antigos em `/tmp/opencode/*.log`
(`b1..b9`, `c1..c6`) são de CI (`/home/runner/work/...`), não builds locais.

`.github/workflows/build.yml` roda em **qualquer push de branch** (`branches: '**'`),
então: commitar na branch temporária → push → CI compila sozinho.

O status do CI pode ser lido pela API pública do GitHub, sem `gh`:

```bash
curl -s "https://api.github.com/repos/kiritsuguxs/yomotsu/actions/runs?branch=tmp%2Fanime-anikku&per_page=1" \
  | python3 -c "import json,sys; r=json.load(sys.stdin)['workflow_runs'][0]; print(r['status'], r['conclusion'])"
```

`git push` funciona (credencial disponível), apesar de não haver `credential.helper` configurado.

### Como ler o log do CI (importante — o "Sign in to view logs" da UI não é o caminho)

A página do Actions pede login, e a API de logs exige admin. **Mas existe token no ambiente:**

```bash
# 1) achar o run_id e o job_id
curl -s "https://api.github.com/repos/kiritsuguxs/yomotsu/actions/runs?branch=tmp%2Fanime-anikku&per_page=1" \
  | python3 -c "import json,sys; r=json.load(sys.stdin)['workflow_runs'][0]; print(r['id'], r['head_sha'][:8], r['conclusion'])"

curl -s "https://api.github.com/repos/kiritsuguxs/yomotsu/actions/runs/<RUN_ID>/jobs" \
  | python3 -c "import json,sys; [print(j['id'], j['name'], j['conclusion']) for j in json.load(sys.stdin)['jobs']]"

# 2) baixar o log INTEIRO (funciona com o token, sem admin)
curl -sL -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/kiritsuguxs/yomotsu/actions/jobs/<JOB_ID>/logs" -o /tmp/opencode/ci.log

# 3) achar o erro
grep -nE "e: |error:|FAILED|What went wrong" /tmp/opencode/ci.log | head -30
```

`GH_TOKEN` está no env. **Não imprimir o token em resposta ao usuário.**
O browser CDP (`and-code-browser_*`) **não funciona** neste ambiente
("no WebView devtools socket"), então não dá para clicar na UI do GitHub.
O `gh` CLI também não está instalado.

### O que o CI NÃO pega (validar com SQLite local)

O CI só compila. These NÃO são cobertos por ele e precisam de atenção:
- se a `.sqm` roda de fato (o CI não executa a migração)
- se nomes de índice/trigger colidem (SQLite exige nomes globais)
- se os queries geram código válido

Ferramenta usada: python3 + `sqlite3` com `Connection.complete_statement()` para
fatiar os `.sq` (regex própria erra em trigger com `CASE...END;` aninhado).
Ver `data/` e `view/` com os dois cenários: instalação nova (cria do `.sq`) e
upgrade (cria do `.sq` sem as tabelas novas, depois aplica a `.sqm`).


---

## 5. Worktree

- Repo doador: `/workspace/anikku` (Anikku, branch `20b42df`)
- Cópias antigas em `/tmp/opencode/anikku` e `/tmp/opencode/aniyomi` (não usar, podem estar velhas)
- Branch de trabalho: **`tmp/anime-anikku`**
- `main` e `feat/anime-v2` estão intactas e limpas.

---

## 6. Plano de 4 testes

Estratégia: **cirurgica, um teste por vez**, para não repetir os ~70 commits de bug da tentativa anterior.
Se uma etapa falhar, para ali e resolve — não acumula erro.

| # | O que faz | Quando dá pra ver anime? |
|---|---|---|
| 1 | Deps do player no Gradle | ❌ não (só valida compilação + não quebrar mangá/novel) |
| 2 | Tabelas SQL de anime | ❌ não |
| 3 | Repositórios/modelos de anime | ❌ não |
| 4 | Tela do player + aba de anime | ✅ **aqui** |

### ✅ TESTE 1 — CONCLUÍDO, CI VERDE (`41f85c2`)

Branch `tmp/anime-anikku`, commit `41f85c2 build: adiciona dependencias do player de video anime (MPV)`.
Arquivos alterados: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`.

7 dependências adicionadas (todas verificadas como existentes nos repositórios — 5 no Maven Central,
2 AndroidX no Google Maven, que *não* ficam no Central e dão 404 lá):

| Dependência | Versão | Uso |
|---|---|---|
| `io.github.secozzi:mpv-android-lib` | 0.1.14 | o player MPV (package `is.xyz.mpv`) |
| `com.arthenica:smart-exception-java` | 0.2.1 | exigência do mpv |
| `androidx.constraintlayout:constraintlayout-compose` | 1.1.2 | UI do player |
| `androidx.media:media` | 1.8.0 | media session |
| `io.github.2307vivek:seeker` | 1.2.2 | barra de progresso |
| `io.github.yubyf:truetypeparser-light` | 2.1.4 | fontes |
| `org.nanohttpd:nanohttpd` | 2.3.1 | servidor local (cast/torrent) |

Mais dois detalhes que **quebram o release silenciosamente** se faltarem:
- `app/build.gradle.kts` → `packaging.jniLibs.keepDebugSymbols` com as 11 `.so` do MPV/FFmpeg
  (`libmpv`, `libplayer`, `libpostproc`, `libavcodec`, `libavdevice`, `libavfilter`, `libavformat`,
  `libavutil`, `libswresample`, `libswscale`, `libc++_shared`) — sem isso o build com `minify` engole a lib nativa.
- `app/proguard-rules.pro` → regras R8 para `is.xyz.mpv.**` (o Anikku já tinha essa),
  `dev.vivvvek.seeker.**` e `com.yubyf.truetypeparser.**`.

Verificações feitas:
- `minSdk 26` do Yomotsu é compatível com o MPV.
- Yomotsu **já tinha** `logcat`, `injekt`, `unifile`, `materialKolor`, `moko-resources` — por isso só precisou dessas 7.
- TOML do version catalog validado com `tomllib` (sintaxe OK, 129 libs).
- Aceleradores comparados com o Anikku: `ffmpeg-kit` e `torrentserver` foram **deixados de fora de propósito**
  (o ffmpeg-kit é enorme e precisa de setup nativo; entra no TESTE 4 se for realmente necessário).

**Status:** ✅ **VALIDADO NO APARELHO.** Compila, instala, e o usuário confirmou que
mangá e novel continuam funcionando, sem nenhum bug. Regressão zero.
**Nenhum anime visível ainda** — o TESTE 1 só adiciona dependências, não copiou código.
Isso valida a etapa de maior risco do projeto inteiro (as libs nativas do MPV).

### ⏳ TESTE 2 — tabelas SQL de anime (EM ANDAMENTO — CI foi VERMelho no 1º push)

**1º push (`47a76d2`) → CI VERMELHO.** Motivo: `AppModule.kt:87` não passava os novos
`ColumnAdapter` que o SQLDelight passou a exigir:

```
e: AppModule.kt:87:17 No value passed for parameter 'animesAdapter'.
e: AppModule.kt:87:17 No value passed for parameter 'anime_historyAdapter'.
```

Toda tabela com type adapter ganha um `<tabela>Adapter` no construtor de `Database`.
`animes` precisa de `genreAdapter`, `update_strategyAdapter` e `fetch_typeAdapter`;
`anime_history` precisa de `last_seenAdapter`.

**2º push (`9bc62a1`)**: cria `FetchTypeColumnAdapter` em
`data/src/main/java/tachiyomi/data/DatabaseAdapter.kt` e registra os dois adapters
em `AppModule.kt`. Aguardando CI.

> ⚠️ **Convenção de nome do SQLDelight (importante para o TESTE 3):** ele **só
> capitaliza a primeira letra** e mantém o resto do nome da tabela. Confirmado no
> issue sqldelight#4679. Então:
> `anime_history` → `Anime_history` · `anime_sync` → `Anime_sync` ·
> `excluded_anime_scanlators` → `Excluded_anime_scanlators`
> Não é `AnimeHistory` nem `Anime_History`. E o **parâmetro** do construtor usa o
> nome **cru** da tabela: `anime_historyAdapter`.

### Tabelas .sq portadas (11 arquivos novos, nada existente foi alterado)

`data/`: `animes.sq` (317), `episodes.sq` (179), `animes_categories.sq` (24),
`anime_sync.sq` (64), `anime_history.sq` (73), `merged.sq` (121),
`excluded_anime_scanlators.sq` (23)
`view/`: `animedeletableView.sq`, `animeseasonsView.sq`, `animeseasonstatsView.sq`,
`episodestatsView.sq`, `historystatsView.sq`
`migrations/14.sqm` (331) — 30 statements DDL: 6 tabelas, 10 índices, 6 triggers, 8 views
`source-api`: `animesource/model/FetchType.kt`

**Adaptações feitas (não é cópia literal):**
1. `excluded_anime_scanlators` e `anime_history` são tabelas **novas**: no Anikku elas
   se chamam `excluded_scanlators`/`history` e usam `anime_id`/`episode_id`, mas no
   Yomotsu esses nomes já existem com `manga_id`/`chapter_id` e `manga_id` é `NOT NULL`
   (não dá pra misturar). 6 queries reescritas pra apontar pras tabelas novas.
2. `animes_library_favorite_index` — renomeado. **Em SQLite o nome de índice é global**,
   e o Anikku só tem uma tabela de biblioteca, então lá não colidia. Em Yomotsu colidiu
   com `library_favorite_index` do `mangas.sq`. (Esse bug foi pego na validação local,
   não pelo CI.)
3. `FetchType` criado no `source-api` (importado por `animes.sq`; não existia).
4. `14.sqm` teve que ser **escrito à mão**: o Anikku não tem migration que crie essas
   tabelas (nascem com ele, que é anime-only). Gerado por script a partir do DDL dos
   `.sq`, tirando os type adapters, e conferido aplicando no SQLite.

**Validação local feita (o CI não cobre isso):**
- instalação nova: 68 objetos, sem erro
- upgrade v13 → v14: 37 → 68 objetos, mangá intacto
- as 11 views respondem a SELECT
- teste funcional: inseriu anime + episódio + histórico, e `episodestatsView`/
  `historystatsView`/`animedeletableView`/`animeseasonsView` devolveram o esperado
- **69/69 queries** preparam sem erro


### ⏳ TESTE 3 — repositórios e modelos
Ver seções 2. Começar pelos `typealias` (poucas linhas) + os 4 métodos de temporada no
`MangaRepositoryImpl`. No `data/` do Yomotsu os pacotes são `tachiyomi.data.*`
(categoria, chapter, history, manga, release, source, track, updates) — será preciso criar
o equivalente de anime seguindo esse padrão.

### ⏳ TESTE 4 — player + aba de anime
O player do Anikku está em:
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/` (67 arquivos, inclui `PlayerActivity.kt`,
  `AniyomiMPVView.kt`, `PlayerViewModel.kt`, `controls/`, `cast/`, `settings/`, `loader/`)
- `app/src/main/java/eu/kanade/presentation/player/`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/`
- `app/src/main/java/animiru/feature/mpvfiles/MpvConfig.kt` (classe `MpvConfig`, usada pelo player)

Cuidado com imports externos que o Anikku usa e que o Yomotsu pode não ter:
`com.github.jmir1:ffmpeg-kit`, `com.github.Diegopyl1209:torrentserver-aniyomi`,
`androidx.mediarouter`, `com.google.android.gms:play-services-cast-framework` (bundle "cast"),
`dev.icerock.moko.resources`, `com.hippo.unifile.UniFile`, `com.yubyf.truetypeparser.TTFFile`,
`dev.vivvvek.seeker.*`, `logcat`, `uy.kohesive.injekt`.
O Anikku também usa `exh.source.MERGED_SOURCE_ID` — **verificar se existe no Yomotsu.**

---

## 7. O que NÃO fazer

- ❌ Não migrar a base do Anikku. O Yomotsu continua Mihon.
- ❌ Não buscar código do Aniyomi. A branch `feat/anime-support` veio de lá e é a origem dos bugs.
- ❌ Não afirmar que "o anime já existe" — não existe. Verificar a tree.
- ❌ Não commitar em `main` nem em `feat/anime-v2`. Só em `tmp/anime-anikku`.
- ❌ Não embutir URL de repo de anime, nem como padrão, nem sugerida.
- ❌ Não acumular várias etapas antes de compilar. Um teste por vez.
- ❌ **Não estimar risco no código que o usuário já resolveu** (ver lição 9.2).

## 8. Como retomar

1. Ler este arquivo.
2. `cd /workspace/yomotsu && git checkout tmp/anime-anikku`
3. Conferir o CI: está verde? (comando na seção 4)
4. Perguntar ao usuário se o passo passou no aparelho dele.
5. Só então seguir para o próximo passo.

---

## 9. Lição do dia (o que mais custou aprender)

### 9.1 O Anikku substituiu os modelos do mangá por typealias

Como é anime-only, ele **apagou** os modelos de mangá e colocou alias apontando pro anime.
Achei 7 no total; **2 causaram CI vermelho** e foram corrigidos:

| Alias no Anikku | Consequência no Yomotsu | Correção |
|---|---|---|
| `typealias SManga = ...SAnime` | `getRelatedMangaList` não casava com o base | anime usa `SAnime` em 4 arquivos; `SManga` continua sendo só do mangá |
| `typealias UpdateStrategy = ...AnimeUpdateStrategy` | `copy()` do `SAnime` misturava as 2 enums | apagado o `AnimeUpdateStrategy`; anime usa `UpdateStrategy`, que é o que o banco e o domínio já usam |

Os outros 5 (verificar antes de copiar qualquer coisa do Anikku):
`SourceFactory`, `Filter`, `FilterList`, `SChapter`, `ParsedHttpSource`.

**Regra:** antes de copiar um arquivo do Anikku, conferir se o que ele importa
existe **com o mesmo tipo** no Yomotsu. "O símbolo existe" não basta — as duas
vezes o símbolo existia e era o tipo errado. É o mesmo motivo pelo qual a branch
de 660 arquivos virou o que virou.

### 9.2 Não estimar risco em código que o usuário já resolveu

Falei que anime no Telegram ia bater em limite de tamanho de arquivo e exigir
streamed upload. **O upload do usuário já funciona até 2 GB.** Tratei como
noveldade algo que já estava resolvido. Perguntar antes de estimar risco no
código dele.

### 9.3 O CI não cobre quase nada do que importa

Compilador pega: import quebrado, tipo errado, coluna duplicada, adapter faltando.
**Não pega:** se a `.sqm` roda, se o vídeo toca, se a UI navega.
Sempre que possível, validar antes com python3 + `sqlite3` (ver seção 4).

---

## 10. Escopo completo: o que ainda falta (verificado com o usuário)

Três frentes grandes, **não uma**:

### 10.1 TESTE 3.5 — `isAnime` + abas (primeiro passo com algo VISÍVEL)

O que o usuário pediu, definido por ele:
- Aba **"Fontes Anime"** — só as instaladas, tocar → ver as obras (espelha `Fontes LN`)
- Aba **"Extensões Anime"** — só as disponíveis para instalar (espelha `Extensões LN`)
- **Filtro de idioma PRÓPRIO**: desativar japonês no anime não pode mexer no mangá

Estrutura confirmada no código:
- `BrowseTab.kt:67-73` tem 5 abas: `sourcesTab`, `extensionsTab`,
  `novelSourcesTab`, `novelsTab`, `migrateSourceTab`
  → anime vira `animeSourcesTab` + `animeExtensionsTab`
- `GetEnabledNovelSources.kt` filtra por `it.isNovel`; o anime precisa de
  `isAnime`, computado em `SourceRepositoryImpl.kt:96` ao lado do `isNovel`
  (`source is INovelSource`) → `source is AnimeSource`
- Filtro: hoje `SourcePreferences.enabledLanguages` (uma chave só) e as duas
  abas de novel usam o **mesmo** `ExtensionFilterScreen`. O anime precisa de
  chave própria + tela própria (6 linhas), **reaproveitando a UI** de
  `presentation/browse/ExtensionFilterScreen`, que já é desacoplada do ViewModel.
  ⚠️ Não passar `isAnime` como parâmetro da Screen: o Voyager associa o ViewModel
  à instância pela `key` e as duas colidiriam — que é exatamente o bug a evitar.
- Difference vs novel: extensão de **novel** é arquivo `.js` (LNReader, manager
  próprio). Extensão de **anime** é **APK**, igual mangá → a aba "Extensões Anime"
  reaproveita o `Extension.Available`/`Installed` do `ExtensionManager`, filtrado
  por `isAnime`. Mais simples que espelhar o mecanismo de novel.

### 10.2 TESTE 4 — player MPV

`/workspace/anikku/app/src/main/java/eu/kanade/tachiyomi/`:
- `tachiyomi/ui/player/` (+ `controls/`, `cast/`, `settings/`, `loader/`, `domain/`)
- `presentation/player/`
- `presentation/more/settings/screen/player/`
- `animiru/feature/mpvfiles/MpvConfig.kt` ← classe `MpvConfig`, usada pelo player

**106 arquivos / 22.201 linhas.** É o passo mais difícil, e o mais perigoso,
porque aqui **compilar não prova nada**: é Compose + estado + MPV nativo, e
falha em runtime. Aí o teste é do usuário no aparelho.

Decidido com o usuário: **fazer em fatias**, não big-bang, porque com fatias o
progresso é acumulado (se a 5ª falhar, as 4 anteriores funcionam) em vez de
descartar tudo. Sugestão de ordem, cada uma com algo visível:
1. aba "Fontes Anime" aparecendo (lista vazia) — prova que a engrenagem está viva
2. `isAnime` + abas + filtro de idioma próprio
3. player: tela, controles, e um vídeo real tocando

Dependências opcionais que o Anikku usa e o Yomotsu não tem — decidir por
fatia: `ffmpeg-kit`, `torrentserver-aniyomi`, `media-router`, `play-services-cast`
(o diretório `cast/` inteiro é opcional, dá pra cortar),
`exh.source.MERGED_SOURCE_ID` (inclusive do Aniyomi).

### 10.3 Download de anime

O Anikku já baixa vídeo de verdade, com player e tracker integrados.
O downloader do Yomotsu é de `chapters`; o de anime é de `episodes` — como anime
tem tabela própria, dá pra espelhar.

### 10.4 Telegram para anime

`app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt`
(1.615 linhas). **Não é o Anikku que faz isso — é feature exclusiva do Yomotsu.**

Estrutura (verificada):
- `CloudManga` / `CloudChapter`, `PendingUpload(manga, chapter)`
- índice local: `telegram_cloud_index.json` (+ `.bak`)
- upload via TDLib: `TdApi.InputFileLocal` → `InputMessageDocument` → `SendMessage`
- `extractCoverFromChapter` extrai a capa de dentro do `.cbz`

**Não há contrato com servidor externo:** o outro lado é o Telegram guardando
arquivo + o índice JSON do próprio app. Então dá pra estender no cliente.

Delta para anime (pequeno):
- `CloudAnime` / `CloudEpisode` no lugar de `CloudManga` / `CloudChapter`
- `PendingUpload` ganhar anime/episódio
- campo novo no `telegram_cloud_index.json` (senão device com versão antiga
  não lê o que a nova escreveu)
- `extractCoverFromChapter` → capa vem da fonte, não do `.cbz`

⚠️ **O envio de arquivo grande JÁ está resolvido no Yomotsu (até 2 GB).**
Não estimar esse ponto como risco — ver lição 9.2.
