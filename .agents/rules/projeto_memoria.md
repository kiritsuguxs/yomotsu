# Memória do Projeto Yomotsu & Registro de Evoluções

Este arquivo registra o contexto completo das melhorias implementadas, do estado atual do repositório e das ideias mapeadas no catálogo Miyomi (miyomi.app) para continuidade em novos chats.

---

## 1. Estado Atual do Repositório & Git
- **Branch de Trabalho:** `temp/melhorias-ui` (Commit: `e58d9f0`)
- **Branch Principal (`main`):** Mantida limpa e estável. Todas as melhorias devem ser desenvolvidas e testadas primeiro em branches temporárias antes de qualquer merge na `main`.
- **Ambiente de Execução:** Android / PRoot Linux. Compilações locais via Gradle falham no PRoot devido a restrições de permissão do kernel (`SecurityException`). A verificação e compilação de APKs é realizada remotamente via GitHub Actions CI.

---

## 2. O que Já Foi Implementado (Commit `e58d9f0`)

### A. Correção do Erro HTTP 400 no AniList e Shikimori
- **Problema:** Ao buscar animes/mangás no rastreador do AniList (ex: "Tensei shitara Ken deshita 2"), a API retornava erro `HTTP 400` com sintaxe inválida no GraphQL.
- **Causa Raiz:** O commit `b7bebf0` introduziu strings raw multi-dólar do Kotlin 2.0 (`$$"""..."""`). Nessa sintaxe, variáveis que começam com um único `$` (como `$typeParam` e `$countField`) deixaram de ser interpoladas e eram enviadas literalmente como `"$typeParam"`.
- **Arquivos Corrigidos:**
  - `app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/AnilistApi.kt` (ajustado para `$$typeParam` e `$$countField`)
  - `app/src/main/java/eu/kanade/tachiyomi/data/track/shikimori/ShikimoriApi.kt` (ajustado para `$$rootField`, `$$filterParam` e `$$countField`)

### B. Polimento Visual da Tela de Detalhes (Anime / Mangá)
- **Botão de Monitoramento:** Substituído o texto longo `"Monitoramento"` (que quebrava feio em duas linhas: `Monitorament` / `o`) por `"Monitorar"` (`MR.strings.action_track`), com `maxLines = 1` e `overflow = TextOverflow.Ellipsis`.
- **Chips de Gêneros/Tags:** Estilizados com fundo semitransparente suave (`surfaceVariant` com 45% alpha) e borda sutil translúcida, eliminando o visual opaco e bruto.
- **Backdrop com Capa Panorâmica:** Suavização do gradiente de transição para 3 paradas com desfoque aumentado de `2.dp` para `12.dp` quando `panoramaCover` está ativo, criando um efeito de brilho ambiente moderno.
- **Arquivo Modificado:** `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt`

### C. Integração do Anime4K v4.0.1 no MPV Player
- **Shaders Nativos:** 39 shaders oficiais GLSL do repositório `bloc97/Anime4K` foram adicionados em `app/src/main/assets/shaders/`.
- **Extração e Cópia Automática:** Implementada em `MpvConfig.kt` para alimentar o diretório `${filesDir}/mpv/shaders/` automaticamente em segundo plano.
- **Modos Calibrados para Mobile:**
  - `Off`: Desativado (zero consumo extra).
  - `Modo A (Restauração)`: Para animes 1080p/720p padrão (Clamp + Restore CNN + Upscale x2 + AutoDownscalePre).
  - `Modo B (Suavização)`: Para animes com linhas serrilhadas ou artefatos (Clamp + Restore CNN Soft + Upscale x2).
  - `Modo C (Fidelidade)`: Upscale sutil com denoising limpo.
- **Interface Intuitiva:**
  - Seletor de chips rápidos no player (`MoreSheet`, botão `...`).
  - Opção no menu de configurações globais: *Mais > Configurações > Player > Decodificador*.
  - Arquitetura auto-recuperável com fallback em tempo de execução.
- **Arquivos Criados/Modificados:**
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/Anime4KMode.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt`
  - `app/src/main/java/animiru/feature/mpvfiles/MpvConfig.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt`
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsDecoderScreen.kt`

---

## 3. Ideias Selecionadas do Catálogo Miyomi (miyomi.app)

Da análise de mais de 130 apps e forks (Komikku, ShinKu, Houri, J2K, ReDantotsu), estas são as melhores ideias para o Yomotsu que mantêm o app limpo e não inchado:

1. **Cores Dinâmicas Baseadas na Capa (Dynamic Palette nos Detalhes):**
   - *Origem:* Komikku / Tachiyomi J2K / ShinKu.
   - *Conceito:* A tela de anime/mangá extrai a cor da capa via Android `Palette` e colore levemente botões de ação e gradientes daquela obra.

2. **Mini-Deck "Continuar Assistindo / Lendo" no Topo da Biblioteca:**
   - *Origem:* ShinKu / J2K.
   - *Conceito:* Um carrossel sutil e recolhível no topo da tela inicial com os 2 ou 3 últimos animes/mangás consumidos e barra de progresso.

3. **Notificação de Novos Capítulos/Episódios com a Capa da Obra:**
   - *Origem:* Komikku.
   - *Conceito:* A notificação em segundo plano do Android exibe a thumbnail da capa da obra em vez de um ícone genérico.

4. **Atualizações Agrupadas por Título (Grouped Updates):**
   - *Origem:* J2K / Komikku.
   - *Conceito:* Na aba Atualizações, múltiplos capítulos/episódios da mesma obra são agrupados em um card expansível em vez de poluir a lista com 10 linhas repetidas.

5. **Sincronização Bidirecional com Trackers (Auto 2-Way Sync):**
   - *Origem:* Komikku / ShinKu.
   - *Conceito:* Sincroniza o progresso assistido/lido no AniList/MAL diretamente com a biblioteca do app.

---

## 4. Diretrizes e Preferências do Usuário
- **Design Limpo:** O usuário rejeitou qualquer badge ou poluição visual pesada sobre capas (não quer que o Yomotsu fique poluído ou difícil de mexer como o Kototoro).
- **Trabalho em Branches:** Nunca aplicar código diretamente na `main`. Sempre usar branches temporárias (ex: `temp/melhorias-ui`).
