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

### B. Empacotamento de Bibliotecas Nativas (AGP Packaging & libmpv)
- **Colisão de `libc++_shared.so` e Crash do MPV (`cannot locate symbol _ZNSt6__ndk18to_charsEPcS0_f`):**
  - O projeto utiliza `opencv` (para leitor/filtros de mangá via `ppocr-sdk`), `mpv-android-lib` e `ffmpeg-kit` (para anime).
  - `opencv` (v4.5.3.0 de 2021) foi compilado com NDK r21 antigo, cuja `libc++_shared.so` NÃO possui símbolos modernos como `std::__ndk1::to_chars(char*, char*, float)` (`_ZNSt6__ndk18to_charsEPcS0_f`).
  - `libmpv.so` (compilado com NDK moderno r25+) exige `to_chars` em tempo de execução.
  - Ao usar apenas `pickFirsts += listOf("**/libc++_shared.so")`, o AGP selecionou a biblioteca antiga do `opencv`, fazendo com que `MPV.<init>` quebrasse ao abrir a reprodução de vídeo com `UnsatisfiedLinkError: dlopen failed: cannot locate symbol "_ZNSt6__ndk18to_charsEPcS0_f" referenced by ".../libmpv.so"`.
  - **Solução Definitiva:**
    - Extraídas as versões modernas de `libc++_shared.so` (NDK r27 / Clang 18.0.3, com suporte completo a C++20 `to_chars`) de `ffmpeg-kit-1.18` para `app/src/main/jniLibs/{arm64-v8a, armeabi-v7a, x86, x86_64}/`.
    - No Android Gradle Plugin, arquivos em `src/main/jniLibs` têm prioridade máxima sobre quaisquer AARs dependentes, garantindo que o runtime C++ moderno seja sempre empacotado no APK final e compatível com `libmpv`, `libffmpegkit` e `libopencv_java4`.
    - **Atenção (.gitattributes):** Adicionada a regra `*.so binary` em `.gitattributes` para evitar que filtros CRLF/LF do Git corrompam os cabeçalhos ELF (seção `.dynamic`), o que causava `dlopen failed: libc++_shared.so .dynamic section header was not found`.

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
    - `BrowseSourceToolbar.kt`, `SourcePreferencesScreen.kt` e `ExtensionDetailsScreen.kt` receberam suporte explícito a `ConfigurableAnimeSource` sem forçar herança direta em `ConfigurableSource`.

### Q. Correções nos Botões de WebView e Carregamento Infinito de Recentes (Outubro 2026)
- **Problema 1 (Botões "Abrir na WebView" Inoperantes em Animes):** Ao clicar nos botões de WebView no catálogo da fonte ou na tela de detalhes do anime, nada acontecia.
  - **Causa:** O código fazia cast exclusivo para `HttpSource` (`source as? HttpSource`). Fontes de anime implementam `AnimeHttpSource` e fontes de novel implementam `NovelHttpSource`. Além disso, `isHttpSource` na tela de detalhes era `false`, e `WebViewViewModel`/`WebViewActivity` não injetavam os headers da fonte de anime.
  - **Solução:** `BrowseSourceScreen.kt`, `MigrateSourceSearchScreen.kt`, `MangaScreen.kt`, `WebViewViewModel.kt` e `WebViewActivity.kt` foram atualizados para obter a URL e headers de `AnimeHttpSource` e `NovelHttpSource`.
- **Problema 2 (Aba "Recentes" em Carregamento Infinito e Compatibilidade ABI de Extensões):**
  - **Causa:**
    1. Em `RxExtension.kt`, `Observable<T>.awaitSingle()` chamava a si mesma recursivamente por sombra de import (`suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()`).
    2. `SourceLatestPagingSource` não possuía `withTimeout`, travando indefinidamente a corrotina caso a fonte demorasse ou falhasse.
  - **Solução:**
    - Mantida a assinatura ABI de `AnimeHttpSource`, `ParsedAnimeHttpSource` e `AnimeCatalogueSource` 100% idêntica ao Aniyomi.
    - Corrigido `RxExtension.kt` para delegar para `coreAwaitSingle`.
    - Adicionado `withTimeout(30_000L)` em `SourcePagingSource.kt` para busca, populares e recentes.
    - `SourceRepositoryImpl.kt` usa `runCatching { source.supportsLatest }.getOrDefault(false)` de forma segura.

### R. Restauração de ABI em ConfigurableAnimeSource e Blindagem do ExtensionLoader (Outubro 2026)
- **Problema (Extensões de anime não instalam e somem da lista de instaladas):**
  - **Causa Raiz 1:** Em `ConfigurableAnimeSource.kt`, a interface havia sido alterada para estender `ConfigurableSource` (`interface ConfigurableAnimeSource : AnimeSource, ConfigurableSource`). Como ambas as interfaces declaravam métodos padrão `getSourcePreferences()` e assinaturas coincidentes, o ART do Android 15 (Realme SDK 35) falhava na verificação de classes com `IncompatibleClassChangeError` / `VerifyError` ao instanciar qualquer extensão de anime pré-compilada que implementa `ConfigurableAnimeSource` (ex: AnimeFire, BetterAnime, Tomato, AnimesOnlineCloud). Com isso, `ExtensionLoader.loadExtension()` retornava `LoadResult.Error`, descartando as fontes instaladas e abortando a conclusão da instalação.
  - **Causa Raiz 2:** No Android 15, `pkgManager.getInstalledPackages(PACKAGE_FLAGS)` com `GET_SIGNING_CERTIFICATES` pode falhar por estouro do buffer IPC (TransactionTooLargeException) caso o dispositivo possua centenas de pacotes, resultando em lista vazia.
  - **Soluções:**
    1. Revertida a herança de `ConfigurableAnimeSource` para estender única e exclusivamente `AnimeSource`, restaurando 100% da compatibilidade binária (ABI) das extensões Aniyomi.
    2. No `ExtensionLoader.kt`:
       - Adicionado fallback automático para `GET_META_DATA` caso `getInstalledPackages` com `PACKAGE_FLAGS` lance exceção.
       - Em `fixBasePaths`, fixados incondicionalmente `sourceDir` e `publicSourceDir` para o caminho real do arquivo da extensão.
       - Em `loadExtension`, adicionado fallback explícito para resolver o arquivo privado `.ext` caso `ApplicationInfo.sourceDir` venha nulo.

### S. Correção de Fechamento Silencioso do Player e Reprodução de Vídeos (Outubro 2026)
- **Problema (Player fecha sozinho sem mostrar log de erro ao tentar assistir):**
  - **Causa Raiz 1:** Em `PlayerActivity.kt`, `Thread.setDefaultUncaughtExceptionHandler` sobrescrevia o `GlobalExceptionHandler` do app, capturando qualquer exceção não tratada, emitindo apenas um Toast e chamando `finish()`. Como a activity era finalizada instantaneamente, o `CrashActivity` com o diálogo de cópia do log nunca era aberto.
  - **Causa Raiz 2:** Em `PlayerViewModel.kt`, `isEpisodeOnline()` checava apenas `source is HttpSource` (interface de manga), retornando `false` para todas as fontes de anime (`AnimeHttpSource`). Em consequência, `setHttpOptions()` não repassava os headers HTTP (`User-Agent`, `Referer`, cookies) para o mpv, causando erro HTTP 403 Forbidden em servidores de vídeo que exigem referer.
  - **Causa Raiz 3:** O arquivo de certificados raiz `cacert.pem` estava ausente em `app/src/main/assets/`. `AniyomiMPVView.kt` passava incondicionalmente `tls-ca-file` apontando para um caminho inexistente, fazendo a verificação TLS falhar em conexões HTTPS no mpv.
  - **Causa Raiz 4:** Em `PlayerActivity.kt` (`onNewIntent`), caso `viewModel.init()` falhasse, o código executava `setInitialEpisodeError(exception)` mas não dava `return`, continuando a execução até `viewModel.loadHosters()` com `source = viewModel.currentSource.value!!`, disparando `NullPointerException`.
  - **Causa Raiz 5:** Ao ocorrer qualquer erro em `setInitialEpisodeError` ou falha de reprodução (`MPV_EVENT_END_FILE`), o código chamava `finish()` imediatamente sem diálogo de erro.
- **Soluções:**
  1. **Removido `Thread.setDefaultUncaughtExceptionHandler` de `PlayerActivity`**: Exceções não tratadas agora ativam normalmente o `GlobalExceptionHandler` e a tela padrão de log de crash do Mihon/Yomotsu.
  2. **Diálogo de Erro Visível no Player**: Adicionado `playerErrorDialogState` com `AlertDialog` Compose em `PlayerActivity`. Erros de inicialização ou reprodução agora exibem uma mensagem clara na tela com botão "Fechar", em vez de fechar a janela abruptamente.
  3. **Certificados CA**: Adicionado `cacert.pem` completo em `app/src/main/assets/cacert.pem` e atualizado `AniyomiMPVView.kt` para checar `caFile.exists() && caFile.length() > 0` antes de definir `tls-ca-file`.
  4. **Suporte a `AnimeHttpSource` em Headers**: `isEpisodeOnline()` agora aceita `AnimeHttpSource`, e `setHttpOptions()` extrai e injeta os headers da fonte de anime (`headers`, `user-agent`, `referrer`) no mpv.
  5. **Proteção de Fluxo em `onNewIntent` e `loadHosters`**: Retorno antecipado após falha em `viewModel.init()`, e captura de `Throwable` em `loadHosters` emitindo `SetVideoLoadError(e)`.
  6. **UniFile e Scanlators**: Protegida a criação de `mpv.conf`/`input.conf` com `findFile ?: createFile` e adicionado fallback sem filtro de scanlator em `initEpisodeList`.

### T. Resolução de Erros de Compilação CI (Outubro 2026)
- **Problema 1 (Unresolved reference 'isAnime' em Manga):**
  - O modelo `Manga` não possui propriedade `isAnime` (pertence a `Source.isAnime`).
  - Corrigido em `MangaScreen.kt`, `MangaViewModel.kt`, `NotificationReceiver.kt`, `LibraryTab.kt` e `UpdatesTab.kt` para checar `source is AnimeSource` ou consultar `sourceManager.get(manga.source) is AnimeSource`.
- **Problema 2 (Unresolved reference 'get' em Injekt):**
  - Em `UpdatesTab.kt`, `HistoryTab.kt` e `MainActivity.kt`, chamadas a `Injekt.get()` falhavam por falta de `import uy.kohesive.injekt.api.get`.
- **Problema 3 (Argument type mismatch em AniyomiMPVView.kt):**
  - `logcat(LogPriority.ERROR, e) { ... }` causava erro de tipo porque o segundo parâmetro da função top-level `logcat` é `tag: String?`. Corrigido para `logcat(LogPriority.ERROR) { "Failed to init AniyomiMPVView: ${e.message}" }`.
- **Problema 4 (Import ausente MigrateMangaDialog em HistoryTab.kt):**
  - Restaurado `import mihon.feature.migration.dialog.MigrateMangaDialog` que havia sido removido durante o ajuste de imports.

### U. Prevenção de Crash Nativo (JNI/R8) e Correções no Download de Animes (Outubro 2026)
- **Problema 1 (Player fecha sozinho sem log em builds Release):**
  - **Causa Raiz:** O GitHub Actions compila com `assembleRelease`, que ativa otimização e minificação R8 (`isMinifyEnabled = true`, `proguard-android-optimize.txt`). A biblioteca `is.xyz.mpv` (`mpv-android-lib`) e callbacks do `PlayerObserver` NÃO possuíam regras de ProGuard no `app/proguard-rules.pro`. A biblioteca nativa `libplayer.so` faz chamadas JNI por reflexão para classes, métodos e campos (`nativeHandle`, `MPVNode`, `eventProperty`, `event`, `logMessage`). Ao serem ofuscados ou removidos pelo R8, o runtime do Android (ART) abortava imediatamente o processo com `SIGSEGV` / `SIGABRT` no JNI, sem passar pelo manipulador de exceções da JVM (fechamento instantâneo sem diálogo de erro).
  - **Solução:**
    - Adicionadas regras `-keep` completas no `app/proguard-rules.pro` para `is.xyz.mpv.**`, `PlayerObserver`, `AniyomiMPVView`, callbacks da `PlayerActivity`, `native <methods>` e `com.arthenica.ffmpegkit.**`.
- **Problema 2 (Arquivos CA cert e fontes não eram copiados antes do MPV iniciar):**
  - **Causa Raiz:** Em `PlayerActivity.onCreate()`, `mpvConfig.onPlayerCreated()` incrementava `playerSessions` para 1 antes de `setupPlayerMPV()`. Como `MpvConfig.copyFiles()` abortava imediatamente quando `playerSessions > 0`, os arquivos `cacert.pem` e `fonts.conf` nunca eram copiados para `context.filesDir/mpv/` na primeira inicialização.
  - **Solução:**
    - Invertida a ordem em `PlayerActivity.onCreate()` para rodar `setupPlayerMPV()` antes de `mpvConfig.onPlayerCreated()`.
    - No `MpvConfig.kt`, tornado o método `awaitCopy()` capaz de forçar a cópia inicial caso ainda não concluída, e protegida a criação de arquivos com `findFile ?: createFile`.
- **Problema 3 (Botão de download não iniciava o download de episódios de anime):**
  - **Causa Raiz:**
    1. No `AnimeDownloader.kt`, `AnimeDownloadJob.start(context)` só era chamado se `wasEmpty == true`. Caso a fila não estivesse vazia ou o worker estivesse pausado/parado, novos episódios não disparavam o download.
    2. R8 minificava classes e callbacks do `com.arthenica.ffmpegkit.**`, impedindo o funcionamento do `FFmpegKit.executeWithArgumentsAsync`.
    3. Resolução da fonte no `AnimeDownloader.kt`, `AnimeDownloadStore.kt` e `AnimeDownload.fromChapterId` usava apenas `sourceManager.get(anime.source)` sem fallback para `getOrStub`.
  - **Solução:**
    - Ajustado `queueEpisodes` para sempre iniciar o `AnimeDownloadJob` se `!isRunning` e `autoStart == true`.
    - Adicionadas regras `-keep` completas para `com.arthenica.ffmpegkit.**` e `animedownload.**`.
- **Problema 4 (Crash SerializationException: Serializer for class 'ChapterNode' is not found ao abrir o player):**
  - **Causa Raiz:** Em `PlayerModels.kt`, a classe `ChapterNode` não possuía a anotação `@Serializable`. Ao inicializar o player, o flow `mpv.propFlow<MPVNode>("chapter-list")` decodifica a lista de capítulos via `toObject<List<ChapterNode>>(json)`. Sem a anotação `@Serializable`, o plugin do kotlinx.serialization não gerava o serializer `ChapterNode$$serializer`, causando exceção fatal capturada pelo handler da UI.
  - **Solução:**
    - Adicionada a anotação `@Serializable` na classe `ChapterNode` em `PlayerModels.kt`.
    - Adicionadas regras `-keep` no `app/proguard-rules.pro` para `ChapterNode`, `TrackNode` e seus serializers correspondentes.

### V. Correções de Exibição de Qualidades de Vídeo e Falha no Download com FFmpegKitConfig (Outubro 2026)
- **Problema 1 (Notificação de erro com texto 'com.arthenica.ffmpegkit.FFmpegKitConfig' ao baixar anime):**
  - **Causa Raiz:** O pacote `com.github.jmir1:ffmpeg-kit:1.18` (JitPack) foi publicado com POM vazio, omitindo a dependência transitiva obrigatória de runtime `com.arthenica:smart-exception-java` (`com.arthenica.smartexception`). `libs.arthenica.smartexceptions` não estava incluído em `app/build.gradle.kts`, e o ProGuard protegia apenas `com.arthenica.ffmpegkit.**`. Em tempo de execução, ao tentar inicializar `FFmpegKitConfig`, o ART do Android falhava na verificação da classe com `NoClassDefFoundError: com.arthenica.ffmpegkit.FFmpegKitConfig`.
  - **Solução:**
    - Adicionado `implementation(libs.arthenica.smartexceptions)` no `app/build.gradle.kts`.
    - Atualizada a regra do ProGuard em `app/proguard-rules.pro` para cobrir todo o pacote `-keep class com.arthenica.** { *; }`.
    - Em `AnimeDownloader.kt`, corrigido o bug em `getDuration` que usava `toFFmpegString` (SAF para URIs `content://`) em arquivos de cache locais (`file://`), substituindo por `durationFile.absolutePath` com fallback resiliente.
    - Adicionadas flags de reconexão HLS no FFmpeg (`-reconnect 1 -reconnect_at_eof 1 -reconnect_streamed 1 -reconnect_delay_max 5`) para evitar interrupções de stream em downloads.
    - Substituída a mensagem genérica `"Error in ffmpeg!"` pela mensagem com `it.output` real.
- **Problema 2 (Qualidades não aparecem na folha 'Qualidades' para todas as extensões):**
  - **Causa Raiz:**
    1. Em `EpisodeLoader.kt`, se `checkHasHosters` retornava verdadeiro mas a extensão falhava ao invocar `getHosterList(episode)` (por incompatibilidade ABI ou retorno vazio), o código abortava sem tentar o método universal `getVideoList(episode)`.
    2. Em `AnimeHttpSource.kt`, `getVideoList(hoster)` e `fetchVideoList(episode)` não possuíam blindagem nem fallbacks reflexivos para variações de assinatura de extensões compiladas para versões anteriores da biblioteca. Além disso, `hoster.hosterUrl` relativas causavam crash por falta de prefixo de URL (`baseUrl`).
    3. Em `QualitySheet.kt`, `expandedState.getOrNull(hosterIdx)` caía em `false` por padrão quando a lista de estados ainda não estava sincronizada, fazendo com que as listas de qualidades dos hosters ficassem colapsadas e invisíveis.
  - **Solução:**
    - Em `EpisodeLoader.kt`: implementado fallback automático para `source.getVideoList(episode)` caso `getHosterList` lance exceção ou retorne lista vazia.
    - Em `AnimeHttpSource.kt`: adicionadas proteções completas em `getVideoList(hoster)` (com prefixação de URL relativa e fallback para `videoListParse(response)` de 1 parâmetro), `Observable.defer` e métodos de reflexão em `fetchVideoList`, e proteção contra falhas em `sortVideos()`.
    - Em `QualitySheet.kt`: definido `isExpanded` como `true` por padrão no `hosterContent`, garantindo que as qualidades de vídeo apareçam abertas imediatamente para todas as extensões.
    - Em `PlayerViewModel.kt`: blindadas as operações `onHosterClicked` e `updateAt` contra `IndexOutOfBoundsException`.

### W. Filtro de Idiomas das Extensões de Light Novel (LN) e Resolução de Crash em Extensões (Outubro 2026)
- **Problema 1 (Filtro de Extensões LN exibe todos os idiomas mesmo selecionando apenas um, ex: Inglês):**
  - **Causa Raiz:** O conjunto padrão de preferências `enabledNovelLanguages` era inicializado com `LocaleHelper.getDefaultEnabledLanguages()`, que inclui a flag especial `"all"`. Em `NovelsViewModel.kt`, a verificação continha `if (enabledLanguages.isEmpty() || "all" in enabledLanguages) return true`. Como `"all"` não é um idioma real de extensões de novel, ele nunca aparecia na lista de seleção para o usuário desmarcar. O código mantinha `"all"` ativo em segundo plano, fazendo com que todos os idiomas fossem exibidos.
  - **Solução:**
    - Em `SourcePreferences.kt`, removido `"all"` do valor padrão de `enabledNovelLanguages`.
    - Em `ExtensionFilterViewModel.kt`, sanitizado `enabledNovelLanguages` para remover `"all"` caso presente de instalações anteriores.
    - Em `NovelsViewModel.kt`, reformulado `matchesLanguage` para limpar `"all"` e validar estritamente apenas os idiomas ativados pelo usuário (`en`, `English`, `pt`, `pt-BR`, `es`, `pl`, etc.).
- **Problema 2 (Crash com error_capture_html_script_not_found ao abrir extensão):**
  - **Causa Raiz:** Algumas extensões tentam carregar scripts JavaScript para captura de HTML em WebViews via `getResourceAsStream("capture_html.js")` ou via `assets.open()`. Como as extensões são carregadas dinamicamente sem instalação de pacote no sistema, recursos dentro da pasta `assets/` do APK da extensão não eram localizados pelo `ChildFirstPathClassLoader.findResource()`. Além disso, blocos `executor.execute` em `CloudflareInterceptor.kt` executavam sem `try-catch` na thread principal, propagando exceções que derrubavam a aplicação.
  - **Solução:**
    - Em `ChildFirstPathClassLoader.kt`, adicionado fallback de resolução de recursos que inspeciona diretamente os arquivos ZIP/APK da extensão (`assets/`, `res/raw/`).
    - Adicionados os scripts `capture_html.js`, `capture_html` e `scripts/capture_html.js` em `app/src/main/assets/` como salvaguarda no host.
    - Protegidos os blocos `executor.execute` em `CloudflareInterceptor.kt` com `try-catch` e liberação de `CountDownLatch`.

### X. Correção dos Nomes de Idioma em Branco na Tela de Filtro de Extensões LN (Outubro 2026)
- **Problema (Linhas em branco / sem nome na tela 'Extensões LN'):**
  - Na tela de filtro de extensões de Light Novel ("Extensões LN"), apenas "English", "Multi" e "Polski" mostravam o nome ao lado do interruptor. As demais opções (Português, Espanhol, Francês, Russo, Árabe, Chinês, Japonês, Coreano, etc.) apareciam como linhas totalmente pretas / em branco, sem texto.
  - **Causa Raiz:**
    1. Os plugins de novel (vindos do ecossistema LNReader) possuem o campo `plugin.lang` preenchido com nomes completos ou em alfabetos próprios (ex: `"Português"`, `"Español"`, `"Bahasa Indonesia"`, `"Русский"`, `"\u200eالعربية"`, `"中文, 汉语, 漢語"`, `"한국어"`).
    2. A tela `ExtensionFilterScreen` chama `LocaleHelper.getSourceDisplayName(language, context)` -> `getLocalizedDisplayName(language)`.
    3. `getLocalizedDisplayName` chamava `Locale.forLanguageTag(lang)`. No padrão BCP-47 do Java/Android, tags de idioma só aceitam letras ASCII básicas (`[a-zA-Z]`). Nomes com acentos (`Português`, `Español`), espaços (`Bahasa Indonesia`, `Tiếng Việt`) ou caracteres não-latinos (Círilico, Árabe, Hangul, CJK) eram rejeitados por erro de sintaxe, fazendo o Android retornar uma instância vazia `Locale("")` cujo `getDisplayName()` resultava em string vazia `""`.
    4. Apenas strings curtas puramente ASCII (`English`, `Multi`, `Polski`) não geravam erro de sintaxe de tag primária e tinham nomes exibidos.
  - **Solução:**
    - Em `LocaleHelper.kt`:
      - Implementado `mapNovelLangToCode(rawLang: String): String` para mapear todas as strings de plugins e repositórios de novel para seus códigos canônicos BCP-47 / ISO (`pt-BR`, `en`, `es`, `fr`, `id`, `pl`, `vi`, `tr`, `ru`, `uk`, `th`, `ar`, `zh-Hans`, `ja`, `ko`, `all`, etc.).
      - Atualizado `getSourceDisplayName`: redireciona `all` / `multi` para o recurso de texto `MR.strings.multi_lang` ("Múltiplos").
      - Atualizado `getLocalizedDisplayName`: normaliza via `mapNovelLangToCode` antes de consultar `Locale.forLanguageTag`, e adicionado fallback que remove marcadores Unicode (`\u200e`) e garante que nenhuma opção fique com nome em branco.
    - Em `ExtensionFilterViewModel.kt`:
      - No ramo `ExtensionFilterType.NOVEL`, mapeadas as linguagens das extensões disponíveis e instaladas para códigos canônicos através de `mapNovelLangToCode`.
      - Migradas automaticamente preferências salvas pré-existentes em `enabledNovelLanguages` (ex: `"English"`, `"Polski"`) para os códigos canônicos (`"en"`, `"pl"`).
    - Em `NovelsViewModel.kt`:
      - Atualizada a função `matchesLanguage` para utilizar `mapNovelLangToCode` de forma simétrica e confiável, filtrando rigorosamente apenas as extensões ativadas pelo usuário.
    - Em `NovelsScreen.kt`:
      - Atualizada a exibição de idioma nos cartões de extensão e no diálogo de detalhes para utilizar `LocaleHelper.getSourceDisplayName`, padronizando a interface.

### Y. Correção de Download Infinito e Arquivos .tmp com 0 Bytes (Outubro 2026)
- **Problema (Download infinito em 'Fazendo download...' e arquivos de anime com tamanho 0B em `.tmp`):**
  - **Causa Raiz 1:** Em `AnimeDownloader.kt`, o arquivo temporário `$filename.tmp` era pré-criado com 0 bytes antes da invocação do FFmpeg. O comando FFmpeg montado colocava a flag `-y` (sobrescrever) no final da linha após o nome do arquivo (`"\"$ffmpegFilename\" -y"`). No parser CLI nativo do FFmpeg (`ffmpeg.c`), opções colocadas após o arquivo de saída não são aplicadas a ele, sendo interpretadas como opções para um próximo arquivo. Sem a flag de overwrite válida antes do arquivo, o FFmpeg detectava que o arquivo de destino já existia no disco e executava `read_yesno()`, chamando `getchar()` / `read(0, ...)` bloqueando a thread nativa indefinidamente no `stdin` esperando resposta do usuário.
  - **Causa Raiz 2:** A opção `-nostdin` estava ausente. Em processos em segundo plano no Android, sem `-nostdin`, qualquer questionamento interativo do FFmpeg trava a execução.
  - **Causa Raiz 3:** Em `getDuration()`, o comando ffprobe passava `-o "$durationFilePath"` (opção inexistente no ffprobe) e executava via `FFprobeKit.executeWithArgumentsAsync` sem timeout, fazendo streams de rede HLS suspenderem a corrotina indefinidamente antes mesmo do FFmpeg iniciar.
  - **Causa Raiz 4:** Em streams HLS (`.m3u8`), opções `-reconnect 1` antes de `-i` causavam falha imediata por incompatibilidade com o demuxer HLS do FFmpeg (`Option reconnect not found`).
  - **Causa Raiz 5:** `toFFmpegString` não diferenciava URIs `content://` (SAF) de arquivos locais `file://`, gerando caminhos virtuais inválidos em armazenamentos sem SAF.
- **Solução (Portado e aprimorado a partir do Anikku):**
  - Criado [`FFmpegUtils.kt`](file:///workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/util/storage/FFmpegUtils.kt) com extensões `toFFmpegString` seguras para `Uri` e `UniFile` suportando tanto SAF (`content://`) quanto caminhos de sistema de arquivos direto (`filePath`).
  - Em `getFFmpegOptions`:
    - Adicionado `-y -nostdin` no início global do comando e `-y` explicitamente antes de `"$ffmpegFilename"`, garantindo que o FFmpeg nunca pause ou espere em `stdin`.
    - `-reconnect` ativado apenas para streams HTTP que não sejam playlists `.m3u8`.
    - `headerOptions` sanitizado para não injetar `-headers ''` quando vazio.
  - Em `getDuration`:
    - Execução do FFprobe com timeout defensivo de 5s (`withTimeoutOrNull(5000L)`), lendo a duração diretamente de `session.allLogsAsString` sem escrita em arquivos temporários desnecessários.
  - Em `ffmpegDownload`:
    - Execução nativa direta via `FFmpegKitConfig.ffmpegExecute(session)` no despachante `Dispatchers.IO` sincronizada com `isFFmpegRunning`.
    - Parse de `Duration:` e progresso contínuo integrados em `LogCallback` e `StatisticsCallback`.
    - Cancelamento de sessões ativas do FFmpeg em `cancelDownloaderJob()`.

### Z. Desacoplamento de Tradução, Progresso de Episódios em Minutos e Botão Assistir (Outubro 2026)
- **Problema 1 (Recursos de Tradução de Mangá vazando na tela de Anime):**
  - O menu overflow da tela de detalhes de anime exibia "Glossário de tradução" e "Ativar tradução automática para esta obra".
  - Episódios de anime baixados podiam acionar fluxo de tradução do TachiyomiAT.
  - **Solução:**
    - Em `MangaToolbar.kt`: Adicionado parâmetro `isAnime` e condicionadas as opções de tradução ("Glossário de tradução", toggle de tradução automática, traduzir baixados) e sincronização Telegram ("Puxar da Nuvem") a `!isAnime`.
    - Em `ui/manga/MangaScreen.kt`: `onTranslationChapter`, `onTranslateDownloadedClicked` e `onToggleAutomaticTranslation` foram estritos com `.takeIf { !isNovel && !isAnime }`.
    - Em `MangaViewModel.kt`: `hasDownloadedChaptersToTranslate` verifica `!isAnime`, `observeTranslations()` ignora animes, e `toChapterListItems()` define `translationState = NOT_TRANSLATED` sem consultar o gerenciador de tradução.
    - Em `TranslationManager.kt`: Métodos `translateChapters`, `getChapterTranslationStatus` e `isChapterTranslated` verificam explicitamente se a fonte é `AnimeSource` (ou `INovelSource`), retornando imediatamente.
- **Problema 2 (Progresso de Episódios marcando em Páginas ao invés de Minutos):**
  - `lastPageRead` é reaproveitado pelo player de vídeo para armazenar os milissegundos assistidos. Na lista de capítulos/episódios, o app formatava com `MR.strings.chapter_progress` ("Pág. %1$d"), exibindo valores absurdos como "Pág. 300001" após pausar o vídeo.
  - **Solução:**
    - Em `presentation/manga/MangaScreen.kt`: Implementada a função `formatTime(milliseconds: Long)` (formatação `mm:ss` ou `hh:mm:ss`) e atualizada a renderização de `readProgress`: para `isAnime`, formata como `AYMR.strings.episode_progress` (`%1$s/%2$s`) quando há duração total conhecida, ou `AYMR.strings.episode_progress_no_total` (`%1$s`) apenas com o tempo decorrido.
    - Em `domain/.../Chapter.kt` e `data/.../Chapter.kt`: `totalSeconds` agora é extraído e persistido de forma segura dentro de `memo["total_seconds"]`.
    - Em `PlayerViewModel.kt`: Importada e atribuída a extensão `total_seconds`, e `saveEpisodeProgress` repassa `episode.memo` ao `EpisodeUpdate` para salvar a duração no banco de dados.
- **Problema 3 (Nome do Botão Principal na Tela de Anime):**
  - O botão flutuante (FAB) na tela de anime exibia "Continuar" / "Iniciar" (recursos de mangá). O usuário solicitou que fosse nomeado "Assistir".
    - Em `presentation/manga/MangaScreen.kt` (layouts padrão e tablet), o FAB exibe `stringResource(AYMR.strings.action_watch)` quando `state.isAnime` for verdadeiro.

### AA. Diferenciação de Nomenclatura Episódio vs Capítulo (Histórico, Updates e Detalhes) (Outubro 2026)
- **Histórico (`HistoryItem.kt`):** Identifica se o item é anime via `SourceManager.get(sourceId) is AnimeSource` e exibe `AYMR.strings.recent_anime_time` (`Ep. %1$s - %2$s`) em vez de `MR.strings.recent_manga_time` (`Cap. %1$s - %2$s`).
- **Detalhes da Obra (`ChapterHeader.kt`, `MangaScreen.kt`):** Cabeçalho exibe `AYMR.plurals.anime_num_episodes` ("X episódios") e título em modo numérico exibe `AYMR.strings.display_mode_episode` ("Episódio X").
- **Configurações de Exibição (`ChapterSettingsDialog.kt`):** Exibe "Número do episódio", "Configurações do episódio" e "Também se aplica a todos os animes da minha biblioteca" quando a obra for anime.
- **Diálogos de Exclusão (`MangaDialogs.kt`, `UpdatesDeleteConfirmationDialog.kt`):** Pergunta se deseja excluir episódios selecionados (`AYMR.strings.confirm_delete_episodes`) para animes.
- **Atualizações Recentes (`UpdatesUiItem.kt`):** Progresso de reprodução formatado com tempo decorrido `formatTime(it)` (`AYMR.strings.episode_progress_no_total`) para animes em vez de páginas de mangá.

### AB. Perfil Yomotsu, Conquistas de Anime, Avatares Crunchyroll e Correção do Backup (Outubro 2026)
- **Correção Definitiva do Backup (Nome, Título, Avatar e Banner):**
  - O perfil original utilizava um arquivo isolado `yomotsu_profile_prefs` que não era capturado pelo criador de backup (`PreferenceBackupCreator.kt`). Além disso, as imagens eram apontadas apenas por caminho de arquivo local no armazenamento do app.
  - `ProfilePreferences.kt` foi reescrito para utilizar diretamente o `PreferenceStore` injetado pelo app (`AndroidPreferenceStore`), com migração automática dos dados de `yomotsu_profile_prefs` no primeiro carregamento.
  - Para imagens customizadas (avatar e banner), o app compacta e salva uma versão em Base64 no `PreferenceStore`. O arquivo `.tachibk` (compactado em GZip) carrega essas imagens dentro de si e, na restauração ou abertura do app em qualquer aparelho, `ProfilePreferences` recria os arquivos no diretório `profile_images/` automaticamente se o arquivo local estiver ausente.
- **Seletor de Avatares e Banners estilo Crunchyroll / Netflix (`ProfilePresets.kt`):**
  - Criada galeria com 16 avatares pré-definidos temáticos (Monarca das Sombras, Caçador Rank S, Ceifador de Almas, Protagonista Shonen, Maratonista Noturno, etc.) e 8 banners gradientes (Abismo, Monarca, Dragão, Cyberpunk, etc.).
  - Ao clicar no avatar ou banner, abre um diálogo que permite escolher entre os presets prontos com um toque ou selecionar uma imagem da galeria (`[ 📷 Escolher da Galeria ]`).
  - Presets não consomem espaço no backup, armazenando apenas o identificador do preset.
- **42 Novas Conquistas de Anime (`YomotsuAchievementManager.kt`):**
  - Adicionada a categoria `AchievementCategory.ANIME` com ícone `Icons.Outlined.PlayCircleOutline`.
  - Criado o modelo `AchievementStats` suportando: `episodesWatched`, `animesInLibrary`, `animeDownloads`, `chaptersRead`, `mangasInLibrary`, `downloads`.
  - 20 conquistas de Maratonista (de 1 a 25.000 episódios assistidos, tiers Bronze a Rubi).
  - 12 conquistas de Colecionador Otaku (de 1 a 1.500 animes na biblioteca).
  - 10 conquistas de Fansubber (de 1 a 5.000 episódios baixados via `AnimeDownloadManager`).
- **Sala de Troféus e Estatísticas Integradas (`UserProfileScreen.kt`):**
  - Adicionados chips de filtro no topo da Sala de Troféus: `[ Todas ]`, `[ 🍿 Anime ]`, `[ 📖 Mangá ]`, `[ 📚 Coleção ]`, `[ 📥 Downloads ]`.
  - Grade 2x2 com estatísticas separadas: Capítulos Lidos, Episódios Assistidos, Mangás e Animes na Biblioteca.
  - XP e Níveis 1 ao 200 agora somam tanto leitura de mangás quanto episódios de anime assistidos (`XP_PER_EPISODE_WATCHED = 90`) e baixados (`XP_PER_EPISODE_DOWNLOAD = 90`).
- **Disparo de Conquistas em Tempo Real:**
  - `ProfileChecker.checkAchievements(...)` é acionado pelo `PlayerViewModel.kt` ao assistir episódios e pelo `AnimeDownloader.kt` ao concluir downloads de episódios.

### AC. Mini Card na Aba "Mais", Molduras de Avatar Customizáveis e Tempo Assistido/Lido (Outubro 2026)
- **Mini Hunter Profile Card na Aba "Mais" (`MoreScreen.kt`, `MoreTab.kt`):**
  - Adicionado card estilizado logo abaixo do cabeçalho da Logo na aba "Mais".
  - Exibe o avatar do usuário com a moldura selecionada, nome, patente/nível atual com badge ("Nv. X"), título equipado com gradiente, barra de progresso suave de XP e chevron indicativo.
  - Ao clicar no card, abre diretamente o Perfil Yomotsu (`YomotsuProfileScreen`).
  - Carregamento reativo em `MoreViewModel` via `HunterProfileSummary`, atualizando os dados ao entrar na aba.
- **Molduras e Bordas de Avatar Customizáveis (`ProfilePresets.kt`, `ProfilePreferences.kt`, `UserProfileScreen.kt`):**
  - Suporte completo a molduras personalizáveis pelo usuário:
    - **Branco Minimalista (`border_white`):** Borda limpa e elegante em branco puro com acabamento refinado.
    - **Preto Obsidiana (`border_black`):** Estilo minimalista moderno em ônix escuro e obsidiana.
    - **Rank Dinâmico (`border_auto`):** Adapta-se automaticamente à patente do caçador (Ferro, Bronze, Prata, Ciano, Rubi, Ouro e Monarca Supremo).
    - **Gradientes de Elite:** Monarca das Sombras (púrpura/neon), Aura Dourada (ouro celestial), Chama Carmesim (rubi fogo), Cyberpunk Neón, Pulso Elétrico (ciano), Esmeralda Ancestral e Sem Borda.
  - O seletor de molduras pode ser acessado pelo botão dedicado na foto de perfil (ícone de escudo no canto inferior) ou pelo diálogo de avatar (`[ 🛡️ Moldura / Borda ]`).
  - Cada item na lista exibe uma prévia circular em tempo real da aura da moldura com `sweepGradient`.
  - Persistência automática no `PreferenceStore` (`yomotsu_profile_avatar_border`), incluída nos backups.
- **Estatísticas de Tempo Lido e Tempo Assistido (`UserProfileViewModel.kt`, `UserProfileScreen.kt`):**
  - Terceira linha adicionada na grade de estatísticas do perfil:
    - **Tempo Lido:** Injeção de `GetTotalReadDuration.await()` formatado com `Duration.toDurationString` ("X dias Y horas Z min").
    - **Tempo Assistido:** Estimativa precisa baseada nos episódios concluídos (`watchedEpisodesCount * 23L * 60 * 1000L`) formatada com `Duration.toDurationString`.
- **Correção de Compatibilidade de Fontes de Anime:**
  - Filtros em `UserProfileViewModel` e `ProfileChecker` usam verificação segura: `source is AnimeSource || source?.javaClass?.name?.contains("anime", ignoreCase = true) == true`.

### AD. Estatísticas Clássicas do Mihon Integradas ao Perfil Yomotsu (Outubro 2026)
- **Integração Completa das Métricas do Mihon no Perfil do Caçador (`UserProfileViewModel.kt`, `UserProfileScreen.kt`):**
  - O perfil do usuário agora conta com todas as métricas detalhadas de acervo presentes nas estatísticas originais do Mihon/Tachiyomi, harmonizadas com as novidades de anime:
    - **Progresso de Mangá:** Exibe `totalChaptersRead / totalChapters` (ex: `150 / 840`) e subtítulo com a quantidade de obras adicionadas (`X mangás na biblioteca`). Atualiza dinamicamente toda vez que novos capítulos são adicionados à biblioteca.
    - **Progresso de Anime:** Exibe `totalEpisodesWatched / totalEpisodes` (ex: `85 / 240`) e subtítulo com a quantidade de animes (`Y animes na biblioteca`). Atualiza dinamicamente conforme chegam novos episódios.
    - **Dias Lidos (Mihon):** Injeção de `GetTotalReadDuration.await()` formatado com `Duration.toDurationString(context)` detalhando dias, horas e minutos investidos na leitura.
    - **Duração de Anime:** Estimativa em dias e horas investidos em episódios assistidos.
    - **Armazenamento Offline Disponível:** Exibe o total geral de downloads do dispositivo e subtítulo descritivo discriminando downloads de mangá e anime (`X caps • Y eps`).
    - **Obras Concluídas:** Total de mangás e animes 100% lidos/assistidos (`status == SManga.COMPLETED && unreadCount == 0L`), com subtítulo detalhando (`X mangás • Y animes`).
  - **Design Responsivo e Simétrico:**
    - Refatoração do componente `StatBox` para suportar subtítulos informativos e distribuição equitativa (`Modifier.weight(1f)`) em 3 linhas com 2 cartões cada, garantindo encaixe perfeito em qualquer tamanho de tela sem cortes ou quebras de texto.
  - **Internacionalização Completa:**
    - Strings adicionadas em `base`, `pt-rBR` e `pt` (`profile_stat_time_watched_sub`, `profile_stat_offline_sub`, `profile_stat_completed_sub`).

### AE. Galeria de Avatares com Personagens Reais de Anime estilo Crunchyroll (Outubro 2026)
- **Substituição dos Ícones Genéricos por 20 Personagens Icônicos de Animes (`ProfilePresets.kt`, `UserProfileScreen.kt`):**
  - Removidos os ícones genéricos e substituídos por avatares modernos em alta definição (512x512 WebP, ~20-50KB cada) em `res/drawable-nodpi/`, com enquadramento perfeito (PFP bust / close-up centralizado) para que a moldura circular (`CircleShape`) exiba o rosto, cabelos e ombros sem cortes bruscos:
    1. **Sung Jin-woo** (*Solo Leveling* - Arte oficial do manhwa com adaga e olhos azuis brilhantes)
    2. **Satoru Gojo** (*Jujutsu Kaisen* - Gojo sem venda com os Seis Olhos azuis elétricos em destaque)
    3. **Ryomen Sukuna** (*Jujutsu Kaisen* - Sukuna com marcas amaldiçoadas, flecha de fogo e sorriso sinistro)
    4. **Monkey D. Luffy** (*One Piece* - Gear 5 Joy Boy rindo com cabelos brancos em nuvens)
    5. **Roronoa Zoro** (*One Piece* - Zoro com sorriso confiante, cicatriz no olho, brincos e espadas)
    6. **Naruto Uzumaki** (*Naruto* - Naruto Shippuden com kunai, colar de Tsunade e olhos azuis)
    7. **Tanjiro Kamado** (*Demon Slayer* - Tanjiro com haori verde/preto, brincos Hanafuda e espada Nichirin)
    8. **Nezuko Kamado** (*Demon Slayer* - Nezuko com bocal de bambu, olhos rosados e quimono)
    9. **Ichigo Kurosaki** (*Bleach: TYBW* - Ichigo Thousand-Year Blood War com Zangetsu dupla e lua crescente)
    10. **Saitama** (*One Punch Man* - Saitama em Modo Sério com sombreamento dramático e capa branca)
    11. **Eren Yeager** (*Attack on Titan* - Eren Yeager Final Season com coque manbun e marcas de titã)
    12. **Levi Ackerman** (*Attack on Titan* - Capitão Levi empunhando lâmina com capa de Exploração)
    13. **Frieren** (*Sousou no Frieren* - Frieren com chiquinhas brancas, orelhas de elfo e cajado mágico)
    14. **Anya Forger** (*Spy x Family* - Anya com olhos brilhantes de estrela "Waku Waku")
    15. **Killua Zoldyck** (*Hunter x Hunter* - Killua Godspeed com relâmpagos púrpuras)
    16. **Son Goku** (*Dragon Ball Super* - Goku Instinto Superior com aura prateada divina)
    17. **Guts** (*Berserk* - Guts com Armadura Berserker e olho vermelho incandescente)
    18. **Rimuru Tempest** (*Slime Isekai* - Lorde Demônio Rimuru com máscara anti-magia e slime azul)
    19. **Megumin** (*KonoSuba* - Megumin segurando chapéu de bruxa com olhos carmesim)
    20. **Alucard** (*Hellsing Ultimate* - Alucard mirando arma com sobretudo vermelho e sorriso vampírico)
    21. **Yomotsu Original** (*Logo clássico Yomotsu*)
  - **Renderização e Layout:**
    - `PresetAvatarDisplay` utiliza `ContentScale.Crop` com preenchimento total circular (`Modifier.fillMaxSize()`) para personagens, integrando perfeitamente com todas as molduras de avatar selecionáveis.
    - Diálogo seletor estilo Crunchyroll exibindo o nome do personagem em destaque e a obra de origem logo abaixo.
    - Compatibilidade retroativa garantida em `getPresetAvatar` mapeando IDs legados para os novos personagens sem quebrar preferências salvas.

### AF. Banners Temáticos com Paisagens e Cenários Autênticos de Anime (`ProfilePresets.kt`, `UserProfileScreen.kt`) (Outubro 2026)
- **12 Banners de Cenários Reais e Icônicos de Franquias de Anime:**
  - Substituídos os cenários genéricos por ilustrações panorâmicas oficiais e consagradas da animação japonesa, otimizadas em WebP widescreen 2:1 (~20-70KB cada) em `res/drawable-nodpi/`:
    1. **Lago Itomori** (`banner_your_name` - *Your Name / Kimi no Na wa*: Cometa Tiamat cruzando o céu crepuscular sobre a cratera do Lago Itomori)
    2. **Céu de Tóquio** (`banner_weathering` - *Weathering With You / Tenki no Ko*: Feixes de luz solar dourada rompendo as nuvens sobre o horizonte de Tóquio)
    3. **Portão das Sombras** (`banner_solo_leveling` - *Solo Leveling*: Portal azul brilhante da Dungeon e o Exército das Sombras de Sung Jinwoo)
    4. **Santuário Malevolente** (`banner_jujutsu_kaisen` - *Jujutsu Kaisen*: Expansão de Domínio de Ryomen Sukuna durante o clímax de Shibuya)
    5. **Monte das Glicínias** (`banner_demon_slayer_wisteria` - *Demon Slayer*: Monte Fujikasane florido com glicínias roxas iluminadas à noite)
    6. **Vila dos Ferreiros** (`banner_demon_slayer_village` - *Demon Slayer*: O vilarejo secreto dos ferreiros envolto pelas montanhas ao entardecer)
    7. **Vila da Folha** (`banner_naruto_konoha` - *Naruto*: Panorama de Konohagakure vista do topo do Monumento dos Hokages)
    8. **Vale do Fim** (`banner_naruto_valley` - *Naruto*: A lendária cachoeira e as estátuas colossais de Madara Uchiha e Hashirama Senju)
    9. **País de Wano** (`banner_one_piece_wano` - *One Piece*: A florada de cerejeiras em frente à capital das flores e o Monte Fuji de Wano)
    10. **Muralha de Shinganshina** (`banner_aot_shinganshina` - *Attack on Titan*: O Distrito de Shinganshina sob a imponência da Muralha Maria)
    11. **Colina das Flores** (`banner_frieren_meadow` - *Sousou no Frieren*: A colina florida mágica de Frieren e Himmel com o vilarejo crepuscular ao fundo)
    12. **Trilhos no Mar** (`banner_ghibli_spirited` - *Studio Ghibli / A Viagem de Chihiro*: Os trilhos aquáticos do trem misterioso sobre o oceano infinito)
  - Mantidos também os 8 banners gradientes abstratos minimalistas para personalização limpa.
- **Seletor de Banners Estilizado:**
  - Grade 2 colunas com cartões com altura aprimorada (`74.dp`) e cantos arredondados (`10.dp`) exibindo a imagem do banner em `ContentScale.Crop`, gradiente escuro de alto contraste para leitura e identificação legível com nome do cenário e franquia de anime.
  - Compatibilidade garantida em `getPresetBanner` com mapeamento suave de qualquer preferência antiga salva pelo usuário para os novos banners temáticos.

### AG. Integração de Métricas de Rastreamento e Nota Média (AniList / Trackers) no Perfil Yomotsu (Outubro 2026)
- **Estatísticas de Monitoramento Clássicas do Mihon no Perfil do Caçador (`UserProfileViewModel.kt`, `UserProfileScreen.kt`, `YomotsuProfileScreen.kt`):**
  - Adicionada a 4ª linha na grade de estatísticas do perfil Yomotsu integrando os serviços de rastreamento (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, MangaUpdates, etc.):
    - **Itens Monitorados (`trackedTitleCount`):** Contabiliza exatamente quantas obras da biblioteca estão associadas e sincronizadas com os rastreadores logados pelo usuário, exibindo o(s) serviço(s) ativo(s) no subtítulo (ex: `AniList` ou `AniList • MyAnimeList`).
    - **Avaliação Média (`meanScore`):** Calcula a média aritmética das notas concedidas pelo usuário às obras rastreadas, normalizada para a escala universal de 10.0 estrelas (ex: `8.50 ★` ou `N/A` caso ainda não haja notas atribuídas).
    - **Tratamento de Estado Desconectado:** Caso o usuário não tenha nenhum rastreador configurado, os cartões informam amigavelmente `0` / `Nenhum rastreador` e `N/A` sem quebras de layout.
  - **Internacionalização e Strings:**
    - Utiliza `MR.strings.label_tracked_titles` e `MR.strings.label_mean_score` do Mihon, com suporte multilíngue em `base`, `pt-rBR` e `pt` (`profile_stat_no_trackers`, `profile_stat_mean_score_sub`).




