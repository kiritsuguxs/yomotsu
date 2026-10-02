# Yomotsu Anime Support - Project Memory & Architecture Guide

## 1. Visão Geral do Projeto
- **Repositório:** `kiritsuguxs/yomotsu`
- **Branch de Trabalho:** `add-anime-support`
- **Objetivo:** Adicionar suporte a Anime (player MPV, download de episódios, animesources e extensões de anime) na base moderna do Yomotsu (fork do Mihon), portando recursos do Aniyomi / Anikku.
- **Ambiente de Execução:** Android / PRoot Linux (`/workspace/yomotsu`).

---

## 2. Restrições e Ambiente de Build
- **Build Local:** O ambiente PRoot no Android bloqueia chamadas de segurança do daemon do Gradle (`java.security.SecurityException`), impossibilitando compilação local de APKs.
- **CI / CD:** A compilação e verificação são feitas remotamente via **GitHub Actions** (`.github/workflows/build_push.yml`).
- **Trigger:** Todo `git push origin add-anime-support` dispara a pipeline "Build & Test".
- **Monitoramento:** Por preferência do usuário, não usar timers em segundo plano para monitoramento contínuo; o usuário acompanha no GitHub e avisa, ou pode ser consultado pontualmente via API do GitHub caso solicitado.

---

## 3. Decisões Arquiteturais e Principais Correções Realizadas

### A. Player de Vídeo MPV (`PlayerViewModel` e `PlayerActivity`)
1. **Gerenciador de Download:** O player de vídeo gerencia downloads de anime usando [`AnimeDownloadManager`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadManager.kt) e [`AnimeDownload`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/animedownload/model/AnimeDownload.kt), e NÃO o `DownloadManager` de mangá.
2. **Remoção de Torrents:** O serviço `TorrentServer` e dependências relacionadas a torrent foram removidos para evitar dependências C/Go incompatíveis. Em [`PlayerActivity`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt), os links de vídeo são enviados diretamente ao MPV via `loadFile(parseVideoUrl(video.videoUrl)!!, video.mpvArgs)`.
3. **Propriedades vs Métodos de Preferências:** No Yomotsu/Mihon, preferências são acessadas como propriedades (`pref.get()`). No código herdado do Aniyomi, eram chamadas como métodos (`pref().get()`). Todos os pontos em `PlayerViewModel`, `PlayerSettings`, e `AnimeDownloader` foram corrigidos.
4. **Campos do Modelo de Banco (`Chapter` / `Episode`):** O modelo `Episode` é typealiased para `Chapter`. As extensões criadas em [`Chapter.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/database/models/Chapter.kt) (`seen`, `last_second_seen`, `episode_number`, `fillermark`, `total_seconds`) devem ser explicitamente importadas em qualquer arquivo fora do pacote de modelos.
5. **Constantes de Filtro de Capítulos:** Os filtros estão localizados em `tachiyomi.domain.manga.model.Manga.CHAPTER_SHOW_*` (não em `SManga`).

### B. Empacotamento de Bibliotecas Nativas (AGP Packaging)
- **Colisão de `libc++_shared.so`:**
  O projeto utiliza `opencv` (para leitor/filtros de mangá) e `ffmpeg-kit` (para anime). Ambas as bibliotecas fornecem `lib/<abi>/libc++_shared.so`.
  Para evitar a falha da task `:app:mergeReleaseNativeLibs`, foi adicionado em [`app/build.gradle.kts`](file:///workspace/yomotsu/app/build.gradle.kts):
  ```kotlin
  packaging {
      jniLibs {
          pickFirsts += listOf("**/libc++_shared.so")
          ...
      }
  }
  ```

### C. Downloader de Anime e Fila
- [`AnimeDownloader.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloader.kt): Usa `ffmpeg-kit` (`FFmpegKitConfig.getSafParameter`) para download seguro. Removido código de `TorrentServerService` e simplificado `filterTracks`.
- [`AnimeDownloadManager.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadManager.kt): Fornece aliases de compatibilidade `enqueueChaptersToDelete` e `deletePendingChapters`.
- [`AnimeDownloadNotifier.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadNotifier.kt): Ícones apontados para recursos existentes (`R.mipmap.ic_launcher`) e remoção de chamadas quebradas a cores do tema antigo.
- **Fila de Download:** Removido o obsoleto `AndroidViewColorScheme` de `AnimeDownloadAdapter`, `AnimeDownloadHolder` e `AnimeDownloadQueueScreen`.

### D. Telas de Configurações do Player e Componentes
- **Editor Removido:** Excluído o pacote quebrado `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/` (9 arquivos tentando compilar um editor de código/mpv.conf dependente de bibliotecas não instaladas).
- **Substituição de Telas:** Nas telas de Áudio, Legenda e Avançado, widgets customizados (`EditTextInfoPreference`, `MPVConfPreference`) foram substituídos por `EditTextPreference` nativo.
- **Recursos i18n:** Adicionadas strings ausentes em [`i18n-aniyomi/.../strings.xml`](file:///workspace/yomotsu/i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml): `player_pref_subtitle_black_bars`, `player_pref_subtitle_black_bars_summary`, `player_pref_subtitle_system_fonts`, `player_pref_switch_on_failure`, `player_sheets_deband_grain`, `pref_skip_seen_episodes`, `pref_skip_filtered_episodes`, `pref_skip_dupe_episodes`, `pref_create_folder_per_anime`.
- **Mapeamento AMR -> AYMR:** No Yomotsu, recursos de anime devem referenciar `AYMR.strings.*` (`tachiyomi.i18n.aniyomi.AYMR`), e não referências órfãs a `AMR`.
- **Vertical Slider:** Em [`SliderItem.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/presentation/player/components/SliderItem.kt), `BaseVerticalSliderItem` utiliza explicitamente `tachiyomi.presentation.core.components.material.Slider` para suportar `Int` e `IntProgression`.
- **DownloadManager Cache Checks:** `DownloadManager.isChapterDownloaded` no Yomotsu requer 5 parâmetros: `chapterName`, `chapterScanlator`, `chapterUrl`, `mangaTitle`, `sourceId`.
- **Util de Capítulos:** Em `PlayerViewModel.kt`, importar `eu.kanade.tachiyomi.util.chapter.removeDuplicates`.

### E. Trackers (Rastreadores)
- Restaurados arquivos originais de `MyAnimeList`, `AniList`, `Kitsu`, e `Shikimori`, deletando DTOs duplicados de anime para evitar conflitos de tipos e duplicidade de serialização.

### F. Registro de Injeção de Dependências (Injekt / DI)
- **DomainModule (`app/src/main/java/eu/kanade/domain/DomainModule.kt`):** Registrados `GetAnimeExtensionsByType`, `SetAnimeViewerFlags` e `TrackSelect`.
- **AppModule (`app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`):** Registrados `MpvConfig`, `AudioManager` e `BrightnessManager`.
- **PreferenceModule (`app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt`):** Registrados `PlayerPreferences`, `GesturePreferences`, `DecoderPreferences`, `SubtitlePreferences`, `AudioPreferences` e `AdvancedPlayerPreferences`.

### G. Suporte a Fontes e Extensões de Anime na Aba Browse
- **Identificação de Extensões de Anime:**
  - Repositórios e pacotes de extensões de anime usam o namespace `eu.kanade.tachiyomi.animeextension.*`.
  - Em `NetworkExtensionStore.kt` e `NetworkLegacyExtension.kt`, `isAnime` é avaliado como verdadeiro quando `signingKey == "ANIME_REPO"`, `badgeLabel == "Anime"`, ou `packageName.contains("animeextension")`.
  - Em `ExtensionLoader.kt`:
    - Extensões instaladas com `packageName.contains("animeextension")` ou feature `tachiyomi.animeextension` são carregadas com metadados `tachiyomi.animeextension.class` / `tachiyomi.animeextension.factory`, com flag `isAnime = true`.
    - Versão de lib (`libVersion`): Extensões de anime do ecossistema Aniyomi utilizam versões `12.0..18.0` (ex: Tomato 14.12 tem lib 14.0), enquanto mangá usa `1.4` e `1.6`. O validador aceita `12.0..18.0` se `isAnime = true`.
    - Nome da extensão: Remove prefixo `"Aniyomi: "` além de `"Tachiyomi: "`.
    - Fallback de ClassLoader: Tenta `ChildFirstPathClassLoader` e fallback com `PathClassLoader` para fontes de anime.
    - `ExtensionInstallReceiver`: Registrado com `RECEIVER_EXPORTED` para receber os intents de instalação do sistema (`ACTION_PACKAGE_ADDED`/`REPLACED`) no Android 14/15.
  - Em `TrustExtension.kt`: Adicionada a assinatura oficial do repositório Aniyomi (`cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905`) como confiável por padrão.
  - Em `AnimeSource.kt` e `AnimeSourceFactory.kt`: Implementado `SourceFactory` e provido valor default (`emptyList()`) para `getSeasonList` para compatibilidade com extensões compiladas para libs anteriores.
- **Filtros de Extensões por Tipo:**
  - `GetExtensionsByType`: exclui expressamente extensões onde `it.isAnime || it.pkgName.contains("animeextension")`.
  - `GetAnimeExtensionsByType`: inclui apenas extensões onde `it.isAnime || it.pkgName.contains("animeextension")`.
- **Separação de Fontes e Interactors:**
  - `Source.kt` possui propriedade `isAnime: Boolean = false`.
  - `SourceRepositoryImpl.kt` mapeia `isAnime = source is AnimeSource`.
  - `GetEnabledSources`: filtra apenas fontes de mangá (`!it.isNovel && !it.isAnime`).
  - `GetEnabledAnimeSources`: interactor dedicado que filtra fontes com `it.isAnime`. Registrado em `DomainModule.kt`.
- **Organização das Abas em `BrowseTab.kt`:**
  - Ordem das abas:
    1. Fontes (`sourcesTab()`)
    2. Extensões (`extensionsTab(extensionsViewModel)`)
    3. Fontes de Anime (`animeSourcesTab()`)
    4. Extensões de Anime (`animeExtensionsTab(animeExtensionsViewModel)`)
    5. Fontes LN (`novelSourcesTab()`)
    6. Extensões LN (`novelsTab(novelsViewModel)`)
    7. Migrar (`migrateSourceTab()`)
  - A busca (`searchQuery` e `onChangeSearchQuery`) é mapeada para os índices corretos das abas de extensões (1 para Mangá, 3 para Anime, 5 para Novels).

### H. Resolução de Dependências Gradle e JitPack
- O repositório JitPack sofre com erros intermitentes de `401 Unauthorized` / rate limiting do Cloudflare quando acessado pelos runners do GitHub Actions.
- Em [`settings.gradle.kts`](file:///workspace/yomotsu/settings.gradle.kts), o repositório JitPack foi configurado com `content { includeGroupByRegex("com\\.github\\..*") }` tanto em `pluginManagement` quanto em `dependencyResolutionManagement`. Isso impede que o Gradle tente buscar pacotes externos como `io.github.secozzi` ou `cafe.adriel.voyager` no JitPack, eliminando falhas 401.
- `voyager-screenmodel` deve estar presente no bundle `voyager` em [`gradle/libs.versions.toml`](file:///workspace/yomotsu/gradle/libs.versions.toml) para prover `ScreenModel`, `rememberScreenModel` e `screenModelScope` (usados em `EpisodeOptionsDialogScreen` e `AnimeDownloadQueueScreenModel`).

### I. Instalação In-App (Privada) de Extensões de Anime
- **Motivação:** No Android 14 e 15, o instalador do sistema (`PackageInstaller`) exige confirmação manual do usuário, permissão de fontes desconhecidas e frequentemente bloqueia extensões ou causa dessincronia de estado.
- **Funcionamento:**
  - Extensões de anime agora utilizam instalação privada (`installApkPrivately`) diretamente para a pasta interna (`context.filesDir/exts/<pkgName>.ext`), da mesma forma prática que as extensões de novel funcionam sem depender do instalador do Android.
  - O instalador padrão do aplicativo em `ExtensionInstallerPreference.kt` foi definido como `PRIVATE`.
  - Em `ExtensionLoader.kt`, foi implementado `getPackageArchiveInfoCompat` para compatibilidade com Android 13/14/15 (`PackageInfoFlags`), permitindo leitura e execução direta das classes via `PathClassLoader` sem root e sem instalação a nível de SO.
  - Correção em `getSignatures`: extração segura das assinaturas evitando `NullPointerException` quando `signingInfo` for nulo no Android 15.

### J. Prevenção de Falhas de Inicialização e Melhorias no Carregamento de Extensões
- **Crash no Android 15 (SecurityException):**
  - No Android 14/15 (SDK 34/35), registrar receivers para broadcasts protegidos do sistema (`ACTION_PACKAGE_ADDED`, etc.) com `RECEIVER_EXPORTED` causa `SecurityException` fatal na inicialização (`App.onCreate()`).
  - `ExtensionInstallReceiver` e `PackageInstallerInstaller` foram revertidos para `RECEIVER_NOT_EXPORTED` e envolvidos em blocos `try-catch`.
- **Proteção Completa no Carregamento:**
  - `ExtensionLoader.loadExtensions()`, `loadExtension()` e `initExtensions()` foram blindados contra falhas de recursos, ícones ausentes ou erros de dex/classes não encontradas.
  - `getPackageArchiveInfoCompat` foi protegido com fallback e `try-catch` para Android 15.
- **Detecção de Atualizações e Classes de Extensão de Anime:**
  - `updateExists` em `ExtensionManager` ajustado para verificar apenas `versionCode` em extensões de anime (evitando falsos positivos de "Atualizar" devido a divergência entre versões da biblioteca de mangá vs anime).
  - Suporte completo a metadados de extensão tanto com chave de classe quanto de fábrica (`tachiyomi.animeextension.class`, `aniyomi.animeextension.class`, `tachiyomi.animeextension.factory`, `aniyomi.animeextension.factory`).
  - Registro seguro de stub sources no `AndroidSourceManager` com tratamento de exceções.

### K. Correção de NPE no Leitor ao Resolver Pacotes de Extensão
- **Sintoma:** Ao abrir um capítulo de manhwa/mangá no leitor, ocorria crash com `NullPointerException` em `AnimeHttpSource.getId() -> ExtensionManager.getExtensionPackage() -> GetIncognitoState.await()`.
- **Causa:** `GetIncognitoState` chama `extensionManager.getExtensionPackage(sourceId)` para checar modo anônimo, iterando por todas as fontes instaladas. Ao passar pelas fontes de extensões de anime instaladas, a propriedade delegada `id by lazy` em `AnimeHttpSource` causava `NullPointerException`.
- **Solução:**
  - `AnimeHttpSource.kt`: `id` reescrito com getter seguro e cache (`_id`) usando `runCatching` para evitar qualquer NPE em `generateId()`.
  - `ExtensionManager.kt`: consulta `it.id == sourceId` protegida com `try-catch` em `getExtensionPackage` e `getExtensionPackageAsFlow`. Protegido também `getAppIconForSource`.
  - `GetIncognitoState.kt`: consultas protegidas contra exceções.

### L. Correções de Fontes/Extensões de Anime, Pesquisa e Filtros de Idioma Independentes
- **Erro 1 (Crash ao pesquisar extensões):**
  - Em `AnimeExtensionsViewModel` e `ExtensionsViewModel`, a verificação de `source.id` só é acionada quando o termo digitado for numérico e encapsulada em `runCatching`.
  - `AnimeHttpSource.id` agora utiliza primitivo `_cachedId: Long = 0L` com `runCatching`, eliminando qualquer risco de autoboxing `NullPointerException`.
  - `StubSource.from` blindado contra exceções de leitura de propriedades.
- **Erro 2 (3 pontinhos e Pesquisa na aba Fontes Anime):**
  - Criadas as classes `GlobalAnimeSearchViewModel` e `GlobalAnimeSearchScreen` dedicadas para busca global de animes.
  - `AnimeSourcesTab` atualizada para acionar `GlobalAnimeSearchScreen()` em vez da busca de mangás, e removido o filtro de fontes de mangá.
- **Erro 3 (Fontes de anime baixadas não apareciam na aba "Fontes"):**
  - `ChildFirstPathClassLoader.kt`: configurada delegação prioritária ao class loader pai para pacotes de framework/host (`eu.kanade.tachiyomi.source.`, `eu.kanade.tachiyomi.animesource.`, `tachiyomi.`, etc.). Isso impede que o APK externo sobrescreva classes do app com bytecode antigo/incompatível, garantindo que `source is AnimeSource` funcione entre ClassLoaders.
  - `SourceRepositoryImpl.kt`: implementada checagem hierárquica `isAnimeSource` e `isNovelSource` por introspecção e interfaces, prevenindo falso negativo em instâncias de fontes de anime.
  - `AnimeCatalogueSource.kt`: sobrescritos `getPopularManga` e `getSearchManga` delegando para `getPopularAnime` e `getSearchAnime`.
  - `SourcePagingSource.kt`: suporte explícito a `AnimeCatalogueSource`.
  - `consumer-proguard.pro` e `app/proguard-rules.pro`: adicionadas regras `-keep` completas para `eu.kanade.tachiyomi.animesource.**`.
- **Erro 4 & Filtros de Idioma Independentes (Manga, Anime e Novel):**
  - Separados os contadores de atualizações em `ExtensionsViewModel` e `AnimeExtensionsViewModel` para exibirem apenas as atualizações da sua respectiva categoria.
  - `ExtensionManager.updatePendingUpdatesCount` agora contabiliza apenas mangás na contagem global de atualizações.
  - Criadas preferências independentes em `SourcePreferences`: `enabledLanguages` (mangá), `enabledAnimeLanguages` (anime) e `enabledNovelLanguages` (novel).
  - Atualizado `ExtensionFilterScreen` e `ExtensionFilterViewModel` com `ExtensionFilterType` (MANGA, ANIME, NOVEL) e títulos correspondentes, permitindo que a filtragem de idiomas em uma aba não interfira nas demais abas.

### M. Resolução de AbstractMethodError em Extensões de Anime (AnimeFilterList & AnimesPage)
- **Sintoma:** Ao pesquisar animes nas fontes instaladas (AnimesOnlineCloud, Anikatsu, AnimeFire, Tomato), o app quebrava com:
  `java.lang.AbstractMethodError: abstract method "okhttp3.Request AnimeHttpSource.searchAnimeRequest(int, String, FilterList)" on receiver Class<...AnimesOnlineCloud>`
- **Causa Raiz:** No commit `b3b75a7c382e7667dd0a175e6759fc279e283530`, `AnimeFilterList`, `AnimeFilter` e `AnimesPage` foram transformados em `typealias` apontando para `FilterList`, `Filter` e `MangasPage`. Como `typealias` não tem identidade de classe no bytecode da JVM, os métodos abstratos compilados em `source-api` usavam `eu.kanade.tachiyomi.source.model.FilterList`. As extensões externas pré-compiladas do Aniyomi foram compiladas contra `eu.kanade.tachiyomi.animesource.model.AnimeFilterList` e `AnimesPage`, gerando incompatibilidade binária (ABI) em tempo de execução no ART do Android.
- **Solução:**
  - Restauradas as classes reais:
    - `Filter.kt`: tornado `open class Filter<T>`.
    - `FilterList.kt`: tornado `open class FilterList`.
    - `MangasPage.kt`: tornado `open class MangasPage`.
    - `AnimeFilter.kt`: restaurada como classe selada real estendendo `Filter<T>` com todas as subclasses (`Header`, `Separator`, `Select`, `Text`, `CheckBox`, `TriState`, `Group`, `Sort`, `AutoComplete`).
    - `AnimeFilterList.kt`: restaurada como classe real estendendo `FilterList(list)`.
    - `AnimesPage.kt`: restaurada como classe real estendendo `MangasPage(animes, hasNextPage)`.
  - `AnimeCatalogueSource.kt`: `getSearchManga` converte e passa com segurança `AnimeFilterList` para `getSearchAnime`.
  - `AnimeHttpSource.kt`: adicionado fallback defensivo por reflexão em `fetchSearchAnime` para invocar métodos de extensões mesmo em caso de pequenas variações de assinatura.
  - `SourcePagingSource.kt`: conversão e passagem segura de `AnimeFilterList` em `SourceSearchPagingSource`, e captura de `Throwable` em vez de `Exception` para evitar crash fatal do app por `Error` de extensão.
  - `SearchViewModel.kt`: captura de `Throwable` no bloco de busca em vez de apenas `Exception`.
  - `SourceFilterDialog.kt` e `BrowseSourceViewModel.kt`: suporte nativo a renderização e manipulação de filtros `AnimeFilter.*`.
  - Proguard: regras `-keep class eu.kanade.tachiyomi.animesource.** { *; }` fortalecidas em `consumer-proguard.pro` e `app/proguard-rules.pro`.

### O. Correções de Idiomas, Atualizações Falsas, Carregamento Infinito de Recentes e Detalhes de Anime (Outubro 2026)
- **Problema 1 (Vazamento de Idiomas):** Mesmo com apenas português selecionado, extensões de anime em inglês, russo, japonês, etc. apareciam na lista de extensões disponíveis.
  - **Causa:** Em `GetAnimeExtensionsByType.kt`, havia um fallback `if (filteredSources.isEmpty()) listOf(ext)`, retornando a extensão inteira mesmo quando nenhum idioma batia.
  - **Solução:** Corrigido para retornar `emptyList()` se o idioma não estiver ativado nas preferências. `ExtensionFilterViewModel` e `SourcePreferences` mantêm preferências separadas (`enabledLanguages`, `enabledAnimeLanguages`, `enabledNovelLanguages`).
- **Problema 2 (Crash e 0 Capítulos ao abrir Anime):** Abrir anime dava `UnsupportedOperationException: null` e mostrava "0 capítulo".
  - **Causa:** `UpdateMangaFromRemote.kt` invoca `source.getMangaUpdate(...)`. Em `AnimeSource.kt`, esse método lançava `UnsupportedOperationException()`.
  - **Solução:** Implementado `getMangaUpdate` em `AnimeSource.kt` usando coroutines (`supervisorScope`), chamando `getAnimeDetails(sAnime)` e `getEpisodeList(sAnime)` e retornando `SMangaUpdate`.
- **Problema 3 (Notificação Falsa de Atualizações de Anime na aba de Mangá):** Toda vez que o app abria, aparecia notificação "3 atualizações disponíveis: AnimesOnlineCloud, Anime Fire, Anikatsu" que abria a aba de mangá.
  - **Causa:** `ExtensionApi.checkForUpdates` comparava `libVersion` (incompatível com versionamento de extensões de anime) e não filtrava extensões de anime na notificação de mangá.
  - **Solução:** Em `ExtensionApi.kt`, extensões de anime são excluídas da notificação de mangá. Em `ExtensionManager.kt`, extensões de anime comparam estritamente `versionCode` (sem `libVersion`), e não são somadas no contador do badge de mangá.
- **Problema 4 ("Recentes" Carregando Infinitamente):** Ao clicar na aba Recentes de fontes de anime, o app ficava em carregamento infinito.
  - **Causa:** Se `latestUpdatesRequest` ou `latestUpdatesParse` lançasse `AbstractMethodError` ou `LinkageError`, o RxJava 1 tratava o erro como fatal (`Exceptions.throwIfFatal`), nunca chamando `subscriber.onError(...)`. Consequentemente, `awaitSingle()` / `awaitOne()` ficava suspenso indefinidamente. Além disso, `OkHttpExtensions.asObservable()` capturava apenas `Exception` (deixando `Error` vazar sem notificar o assinante).
  - **Solução:**
    - Em `AnimeHttpSource.kt`: `fetchPopularAnime`, `fetchLatestUpdates`, `fetchSearchAnime`, `fetchAnimeDetails` e `fetchEpisodeList` foram encapsulados com `Observable.defer`, fallbacks defensivos por reflexão para invocar os métodos da subclasse mesmo com discrepâncias de assinatura, e conversão de erros em `RuntimeException`.
    - Em `OkHttpExtensions.kt`: alterado `catch (e: Exception)` para `catch (e: Throwable)` para sempre propagar erros ao `subscriber.onError`.
    - Em `RxCoroutineBridge.kt`: envolvido `subscribe()` em `try-catch (e: Throwable)` para evitar suspensão órfã em caso de falha síncrona.

### P. Registro de PlayerActivity e Configurações de Fontes de Anime (Outubro 2026)
- **Problema 1 (Crash ao Iniciar Reprodução de Episódio):** Ao clicar para assistir a um episódio, o app crashava com `android.content.ActivityNotFoundException: Unable to find explicit activity class {app.mihon.tachiyomiat/eu.kanade.tachiyomi.ui.player.PlayerActivity}`.
  - **Causa:** `PlayerActivity` não estava declarada no `AndroidManifest.xml`.
  - **Solução:** Adicionada a tag `<activity android:name=".ui.player.PlayerActivity" ...>` com `configChanges`, `supportsPictureInPicture="true"`, e filtros S-Pen no `AndroidManifest.xml`.
- **Problema 2 (Menu dos 3 Pontinhos sem Configurações para Fontes de Anime):** Em fontes como Tomato que requerem login ou configurações, o menu de 3 pontinhos não mostrava "Configurações".
  - **Causa:** `BrowseSourceToolbar.kt` verificava apenas `source is ConfigurableSource`. Fontes de anime implementam `ConfigurableAnimeSource`. Além disso, `SourcePreferencesScreen.kt` só populava telas para `ConfigurableSource`, e `ExtensionDetailsScreen.kt` só mostrava o ícone de engrenagem para `ConfigurableSource`.
  - **Solução:**
    - `ConfigurableAnimeSource` passou a estender `ConfigurableSource`.

### Q. Correções nos Botões de WebView e Carregamento Infinito de Recentes (Outubro 2026)
- **Problema 1 (Botões "Abrir na WebView" Inoperantes em Animes):** Ao clicar nos botões de WebView no catálogo da fonte ou na tela de detalhes do anime, nada acontecia.
  - **Causa:** O código fazia cast exclusivo para `HttpSource` (`source as? HttpSource`). Fontes de anime implementam `AnimeHttpSource` e fontes de novel implementam `NovelHttpSource`. Além disso, `isHttpSource` na tela de detalhes era `false`, e `WebViewViewModel`/`WebViewActivity` não injetavam os headers da fonte de anime.
  - **Solução:** `BrowseSourceScreen.kt`, `MigrateSourceSearchScreen.kt`, `MangaScreen.kt`, `WebViewViewModel.kt` e `WebViewActivity.kt` foram atualizados para obter a URL e headers de `AnimeHttpSource` e `NovelHttpSource`.
- **Problema 2 (Aba "Recentes" em Carregamento Infinito e Compatibilidade ABI de Extensões):**
  - **Causa Real:**
    1. Em `RxExtension.kt`, `Observable<T>.awaitSingle()` chamava a si mesma recursivamente por sombra de import (`suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()`).
    2. `SourceLatestPagingSource` não possuía `withTimeout`, travando indefinidamente a corrotina caso a fonte demorasse ou falhasse.
  - **Regra Crítica de Compatibilidade ABI (Congelamento de `source-api`):**
    - Extensões de anime de terceiros (Aniyomi / Keiyoushi) são APKs pré-compilados contra a `extensions-lib` do Aniyomi.
    - Alterar métodos abstratos (`popularAnimeRequest`, `popularAnimeParse`, `latestUpdatesRequest`, `latestUpdatesParse`, `latestUpdatesSelector`, `latestUpdatesFromElement`, `latestUpdatesNextPageSelector`) para `open` ou adicionar propriedades/métodos concretos como `supportsLatest` em `AnimeHttpSource` quebra a verificação de classes do Android ART (`VerifyError` / `IncompatibleClassChangeError`). Isso faz com que `ExtensionLoader.kt` falhe em carregar as extensões (sumindo fontes instaladas e impedindo novas instalações).
    - **Solução Definitiva:**
      - Mantida a assinatura ABI de `AnimeHttpSource`, `ParsedAnimeHttpSource` e `AnimeCatalogueSource` 100% idêntica ao Aniyomi.
      - Corrigido `RxExtension.kt` para delegar para `coreAwaitSingle`.
      - Adicionado `withTimeout(30_000L)` em `SourcePagingSource.kt` para busca, populares e recentes.
      - `SourceRepositoryImpl.kt` usa `runCatching { source.supportsLatest }.getOrDefault(false)` de forma segura.




