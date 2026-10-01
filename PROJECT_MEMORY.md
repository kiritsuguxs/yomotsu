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

---

## 4. Estrutura de Arquivos Importantes
- `app/build.gradle.kts`: Declaração de `ffmpeg-kit`, splits de ABI e `pickFirsts` de jniLibs.
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/`:
  - `PlayerActivity.kt`: Activity do reprodutor MPV.
  - `PlayerViewModel.kt`: ViewModel principal do reprodutor.
- `app/src/main/java/eu/kanade/tachiyomi/data/animedownload/`: Toda a lógica de fila, cache e download de animes.
- `domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt`: Preferências de download externo e limites.
