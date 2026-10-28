# Novidades do Yomotsu

Este arquivo contém somente as mudanças importantes para quem usa o aplicativo.
O workflow de lançamento lê automaticamente a seção correspondente à versão publicada.

## 0.21.3-Y1

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Backup e Restauração de Extensões Privadas e Plugins:** O sistema de backup (.tachibk) agora empacota e restaura automaticamente todas as extensões privadas de anime (.ext) e plugins de light novel (.js/.json), sem necessidade de reinstalação manual.
- **Suporte Oficial e Completo a Players Externos (VLC, MPV, MX Player):** Roteamento nativo e inteligente da reprodução de vídeos para reprodutores externos com sincronização bidirecional de posição assistida, progresso e conclusão no histórico do Yomotsu.
- **Detecção Robusta de Players no Android 11+:** Adicionadas regras explícitas de visibilidade de pacotes (<queries>) e resolução direta de aplicativos de mídia instalados, além de contingência (fallback) automática para o seletor nativo do Android.
- **50 Molduras de Avatar por Nível de Perfil:** Sistema de customização de avatar expandido para 50 níveis progressivos de molduras (do Nível 1 ao 1000) com distinções visuais graduais por raridade.
- **Suporte Completo a Formato de Horário (12h / 24h):** Configuração dedicada em Aparência para alternar a exibição de horários nos modos 12 horas (AM/PM), 24 horas ou alinhados ao padrão do sistema.
- **Estabilidade no Carregamento de Capas e Manhwas:** Restauração do motor base estável de carregamento de imagens e interceptadores de rede, sanando a lentidão e travamentos na exibição de capas e páginas.
- **Aprimoramentos no Leitor de Novels:** Estabilização do mecanismo de Leitura em Voz Alta (TTS) e otimização na troca contínua de capítulos.
- **Fila de Downloads e Extensões:** Correção de inconsistências na fila de downloads em segundo plano e validação segura de repositórios.

🇺🇸 **News, Improvements & Fixes (English):**
- **Automated Backup & Restore for Private Extensions and Plugins:** The backup system (.tachibk) now natively packages and restores installed private anime extensions (.ext) and novel plugins (.js/.json), eliminating manual reinstalls.
- **Full External Video Player Integration (VLC, MPV, MX Player):** Seamlessly route video playback to external players with full bi-directional progress and watch history synchronization.
- **Android 11+ Media Player Discovery & Fallback:** Declared explicit package visibility queries (<queries>) to reliably detect third-party players, paired with an automatic fallback to the native system app chooser.
- **50 Avatar Border Progression Tiers:** Expanded profile avatar customization with 50 progressive border tiers (scaling from Level 1 to 1000) reflecting user milestones.
- **Configurable 12h / 24h Time Format:** Added appearance preference to customize timestamps in 12-hour (AM/PM), 24-hour, or system-aligned display.
- **Cover Art & Manhwa Reader Loading Stability:** Restored the core stable baseline for image decoders and network interceptors, resolving slow cover loading and reader stuttering.
- **Novel Reader Engine Polish:** Enhanced Text-to-Speech (TTS) lifecycle stability and seamless chapter transition performance.
- **Download Queue & Extension Fixes:** Fixed background download queue stalling and stabilized repository synchronizations.

## 0.21.0-Y4

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Exportador de Light Novels para EPUB (.epub):** Exporte qualquer Light Novel baixada diretamente para um arquivo `.epub` padronizado e já traduzido em Português (compatível com ReadEra, Google Play Livros, Kindle e Moon+ Reader) pelo menu da obra ou dentro das configurações do leitor.
- **Downloads de Novels na Pasta Oficial do Yomotsu:** Os capítulos baixados e os livros `.epub` exportados de Novels agora são salvos diretamente dentro da pasta oficial `downloads/` do Yomotsu (organizados por Fonte > Obra > Capítulo), mantendo apenas a versão traduzida de forma limpa e compacta.
- **Motores e Modelos de Tradução Independentes (Mangá vs. Novel):** Adicionada configuração de modelo de IA (`Gemini` / `OpenRouter`) exclusiva para Novels, permitindo usar modelos diferentes no Mangá e na Novel sem que um altere o outro, além de suporte ao motor **DeepL** e aplicação automática do **Glossário da Obra** nas traduções de Novels.
- **Tipografia Avançada e Novos Controles no Leitor de Novels:** Adicionados controles de família de fonte (Sem Serifa, Serifa e Monoespaçada), espaçamento de linha, margens laterais, texto justificado, rolagem pelos botões de volume, renderização de ilustrações inline nos capítulos, Timer de Sono e avanço automático de capítulos na narração.
- **Compatibilidade Expandida de Plugins de Novel (LNReader):** Motor QuickJS aprimorado com suporte completo a seletores DOM (`cheerio`), criptografia `CryptoJS` AES-GCM/CBC, `urlencode` e cookies de WebView/Cloudflare.
- **Player de Anime Turbinado (AniSkip, Segurar para 2x e Capítulos):** Integração nativa com **AniSkip** (botões para pular Abertura/Encerramento), gesto de segurar na tela para velocidade **2x**, barra de progresso com capítulos do vídeo e indicador de buffer, botão de **Pular Introdução (+85s)**, lista rápida de episódios dentro do player e seletor de qualidade ao baixar ou assistir.
- **Miniaturas, Sinopses e Selo Filler nos Episódios de Anime:** A lista de episódios agora exibe a miniatura (thumbnail 16:9), o selo **FILLER** e a sinopse expansível de cada episódio quando fornecidos pela extensão.
- **Capa Panorâmica na Tela da Obra:** Toque e segure sobre a capa da obra (ou ative nas configurações de Aparência) para alternar para o modo **Capa Panorâmica**, que preenche todo o quadro superior de ponta a ponta sem cortar a arte.
- **Melhorias no Leitor e Tradutor de Mangás:** Divisão automática de páginas duplas largas (Double-Page Split), toque duplo com zoom focado no ponto tocado, pré-carregamento inteligente de páginas no modo Pager e limpeza das bordas do Inpainter para eliminar linhas brancas em fundos escuros.

🇺🇸 **News, Improvements & Fixes (English):**
- **Light Novel EPUB Exporter (.epub):** Export any downloaded Light Novel directly into a standardized, translated `.epub` book (compatible with ReadEra, Google Play Books, Kindle, and Moon+ Reader) from the series toolbar or reader settings.
- **Novel Downloads in Official Yomotsu Folder:** Downloaded novel chapters and exported `.epub` books are now saved directly inside Yomotsu's official `downloads/` directory (`Source > Series > Chapter`), keeping only the translated version in a clean, compact structure.
- **Independent Translation Engines & AI Models (Manga vs. Novel):** Added a dedicated LLM model setting (`Gemini` / `OpenRouter`) specifically for Novels so Manga and Novel models never overwrite each other, alongside **DeepL** engine support and automatic **Series Glossary** enforcement in Novel translations.
- **Advanced Typography & Controls in Novel Reader:** Added custom font family selection (Sans-Serif, Serif, Monospace), line height, side margins, text justification, volume-button scrolling, inline chapter illustrations, Sleep Timer, and automatic chapter advance during narration.
- **Expanded Novel Plugin Compatibility (LNReader):** Upgraded QuickJS runtime with full DOM traversal (`cheerio`), `CryptoJS` AES-GCM/CBC decryption, `urlencode`, and WebView/Cloudflare cookie synchronization.
- **Upgraded Anime Player (AniSkip, Hold-for-2x & Video Chapters):** Native **AniSkip** integration (Skip OP/ED buttons), long-press gesture for **2x speed** boost, seekbar with video chapter markers and buffer progress, **+85s Intro Skip**, in-player episode switcher dialog, and stream quality selector for both streaming and downloads.
- **Anime Episode Thumbnails, Summaries & Filler Badges:** Episode lists now render 16:9 episode preview thumbnails, **FILLER** badges, and expandable episode summaries when provided by the source.
- **Panorama Cover Mode:** Long-press the series cover in the details screen (or toggle in Appearance settings) to switch to **Panorama Cover** mode, stretching edge-to-edge without cropping top or bottom artwork.
- **Manga Reader & Translator Enhancements:** Added automatic double-page split for wide scans, focal-point double-tap zoom, smart 3-page preloading in Pager mode, and Inpainter edge clamping to eliminate white border artifacts on dark pages.

## 0.21.0-Y3

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Novo Editor Manual de Balões & Toque Direto:** Toque simples sobre qualquer balão traduzido no leitor para abrir o editor e ajustar o texto na hora.
- **Redimensionamento Fino do Balão:** Adicionados controles de `+` e `-` para expandir ou encolher a largura e altura do balão, garantindo encaixe perfeito do texto.
- **Cor de Fundo Personalizável:** Escolha entre fundo Branco, Preto ou Transparente para cobrir 100% o texto original em qualquer cena.
- **Exclusão Rápida de Balões:** Botão de lixeira no editor para remover facilmente qualquer detecção desnecessária.
- **Visualização do Texto Original:** Exibição do texto original em inglês detectado pelo OCR no topo do diálogo para referência durante a edição.
- **Suporte Aprimorado para Webtoon/Manhwa:** Coordenadas de toque perfeitamente calibradas para leitura contínua vertical e modo página.
- **Prevenção de Cortes por Limite de Tokens:** O envio de páginas para a IA agora é fatiado em lotes otimizados, evitando que capítulos longos fiquem cortados pela metade ou com balões sem traduzir.
- **Filtro de Idiomas das Extensões:** Corrigida a duplicidade na lista de idiomas, separando claramente Português (Brasil) de Português (Portugal).
- **Armazenamento Seguro de Traduções:** Corrigido erro de parsing JSON ao salvar balões manuais em capítulos recém-baixados.

🇺🇸 **News, Improvements & Fixes (English):**
- **Complete Manual Balloon Editor & Tap-to-Edit:** Tap directly on any translated speech bubble in the reader to open the editor dialog immediately.
- **Fine-Grained Balloon Resizing:** Added `+` and `-` controls to adjust balloon width and height for a perfect text fit.
- **Custom Background Colors:** Select between White, Black, or Transparent backgrounds to cleanly mask original text in any scene.
- **Delete Unwanted Balloons:** Dedicated trash bin action in the editor to quickly remove misplaced detections.
- **Original Text Preview:** Displays OCR-detected original text at the top of the editor dialog for quick reference.
- **Full Webtoon/Manhwa Viewer Support:** Touch coordinates and hitboxes are now precisely mapped across both vertical strip and paged reader modes.
- **Token Truncation Prevention:** Comic pages are now processed in optimized batches to prevent long chapters from hitting LLM token limits and cutting off translations.
- **Language Filter Localization:** Fixed duplicate language entries in extension filters, correctly distinguishing Portuguese (Brazil) from Portuguese (Portugal).
- **Translation Storage Fix:** Resolved JSON parsing error when saving manual translations on newly downloaded chapters.


## 0.21.0-Y2

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Prompt de IA Abrasileirado para Quadrinhos:** Novo sistema de tradução com IA adaptado especificamente para português brasileiro (pt-BR). O prompt sintetiza e condensa diálogos usando linguagem coloquial, contrações e expressões naturais de quadrinhos e mangás brasileiros (como *tá*, *pra*, *cara*, *bora*), garantindo que as falas caibam perfeitamente dentro dos balões de fala sem transbordar.
- **Preservação de Arte e Contornos de Balões:** Refinamento completo da geometria de limpeza do leitor. O apagador atua exclusivamente linha por linha com bordas arredondadas e suaves, eliminando qualquer risco de caixas brancas cobrirem o desenho, o cabelo dos personagens ou os contornos dos balões.
- **Detecção de Balões de Grito e Formatos Irregulares:** O analisador de balões agora conta com amostragem direta de luminância interna, identificando com precisão balões pontiagudos de ação/grito que antes eram ignorados pelo OCR.
- **Recuperação Precisa de Diálogos:** Ajustadas as regras de validação para recuperar frases curtas, diálogos pontuados e balões próximos, evitando que falas legítimas sejam descartadas como onomatopeias ou ruídos.

🇺🇸 **News, Improvements & Fixes (English):**
- **Brazilian Portuguese Comic AI Prompt:** Introduced a specialized colloquial pt-BR translation prompt for LLM translators. Condenses dialogues using natural Brazilian phrasing, idioms, and contractions to ensure translated text fits comfortably inside manga and comic speech bubbles.
- **Art & Balloon Contour Preservation:** Complete overhaul of reader text cleanup geometry. The eraser operates strictly on a line-by-line basis with soft rounded corners matching balloon curvature, preventing white rectangular patches from erasing character art, hair, or balloon borders.
- **Shout & Jagged Bubble Detection:** Speech bubble analyzer upgraded with interior luminance sampling, reliably recognizing jagged, spiky shout bubbles that previously failed detection.
- **Enhanced Dialogue Recovery:** Fine-tuned heuristic gates to capture short sentences, punctuated lines, and tightly grouped speech blocks, preventing valid dialogue from being misidentified as sound effects.


## 0.21.0-Y1

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Migração Completa para Novels:** O sistema de migração agora detecta, lista e permite buscar diretamente em todas as fontes e extensões de Novels instaladas, incluindo a exibição correta dos seus ícones.
- **Sistema de Raridades de Títulos RPG:** Os títulos de perfil agora possuem categorias de raridade (*Comum*, *Raro*, *Épico*, *Lendário*, *Mítico* e *Divino*) com filtros dinâmicos e cores temáticas.
- **Novo Diálogo Compacto de Títulos:** Interface de seleção de títulos redesenhada, compacta e quadrada, com filtros diretos por raridade e prévia de títulos bloqueados exibindo o nível necessário para desbloquear.
- **Internacionalização Bilíngue (PT-BR / EN):** Todos os 101 títulos RPG e suas raridades possuem tradução oficial para inglês e português.
- **Novo Rastreador Comick:** Suporte nativo ao rastreador Comick integrado ao ecossistema de rastreamento de obras.
- **Confirmação ao Desfavoritar:** Nova opção de confirmação ao remover obras dos favoritos para evitar remoções acidentais da biblioteca.
- **Novo Indicador Wavy de Downloads:** Indicador moderno com barra ondulada do Material 3 nos downloads em andamento.
- **Otimizações no Leitor e Downloader:** Cache de páginas aprimorado para carregamento mais ágil, menor uso de memória RAM e melhor gestão de disco.
- **Novas Opções de Controle:** Opção para economizar dados móveis no leitor e controle de hash de capítulos em disco.
- 💡 **Dica de Configurações:**
  - Em **Mais > Configurações > Leitor**, ative *"Carregar com antecedência apenas por Wi-Fi"* para economizar seus dados móveis.
  - Em **Mais > Configurações > Avançado**, você pode alternar *"Ativar hash do nome do capítulo"* para acelerar a indexação de downloads.

🇺🇸 **News, Improvements & Fixes (English):**
- **Full Novel Migration Support:** Migration system now discovers and allows searching across all installed novel extensions and sources, with accurate plugin icon rendering.
- **RPG Title Rarity System:** Profile titles now feature distinct rarity tiers (*Common*, *Rare*, *Epic*, *Legendary*, *Mythic*, and *Divine*) with dynamic filter chips.
- **Compact Titles Modal:** Redesigned square modal with clean layout, quick rarity navigation, and locked titles preview displaying required unlock levels.
- **Bilingual Localization (PT-BR / EN):** All 101 RPG titles and their rarities fully translated to English and Portuguese.
- **New Comick Tracker:** Native Comick tracker integration into the app tracking ecosystem.
- **Confirm Unfavorite:** Added confirmation prompt when removing entries from library favorites.
- **Material 3 Wavy Download Indicator:** Modern wavy progress indicator for active downloads.
- **Reader & Downloader Optimizations:** Enhanced page cache for faster loading, lower memory overhead, and improved disk performance.
- **New Control Settings:** Option to save mobile data while reading and storage chapter hash toggling.
- 💡 **Settings Tip:**
  - Under **More > Settings > Reader**, toggle *"Load ahead only over Wi-Fi"* to save cellular mobile data.
  - Under **More > Settings > Advanced**, you can toggle *"Enable chapter name hash"* for faster download indexing.


## 0.20.4-Y43.1

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Sistema de RPG Rebalanceado:** O Nível Máximo foi elevado de 500 para **1000**! Mais de 50 novos Títulos Épicos e Celestiais foram adicionados (incluindo *Leitor Onisciente*, *Aquele que Tudo Controla*, *Deus Supremo*). O ganho de XP foi ajustado: Ler (150 XP), Assistir (200 XP) e Baixar (50 XP)! *(💡 Dica: Ler/Assistir algo que já está baixado acumula os bônus!)*
- **Perfil Estiloso:** O seu nome de usuário na tela de perfil agora adota automaticamente as cores do título de RPG que você estiver usando!
- **Recompensa do Nível 1000:** Adicionado o gradiente definitivo em Preto e Branco para a verdadeira divindade que atingir o inalcançável Nível 1000.
- **Melhorias nas Novels:** As abas de estatísticas e o filtro de conquistas para Novels, bem como as categorias na Biblioteca, agora ficam fixas e sempre visíveis, mesmo que a sua biblioteca de Novels ainda esteja vazia.

🇺🇸 **News, Improvements & Fixes (English):**
- **RPG System Rebalanced:** Max Level has been raised from 500 to **1000**! Over 50 new Epic and Celestial Titles have been added (including *Omniscient Reader*, *Supreme God*). Base XP adjusted: Read (150 XP), Watch (200 XP), Download (50 XP)! *(💡 Tip: Reading/Watching downloaded content stacks the bonuses!)*
- **Stylish Profile:** Your username on the profile screen now automatically matches the gradient colors of your currently equipped RPG Title!
- **Level 1000 Reward:** Added the ultimate Black and White gradient for the true deity who reaches the unreachable Level 1000.
- **Novel UI Improvements:** Novel stats and achievement filters, as well as Library categories, are now permanently visible, even if your Novel library is empty.


##


## 0.20.4-Y43

🇧🇷 **Novidades, Melhorias e Correções (Português):**
- **Separação Nativa de Extensões:** Todo o sistema de extensões foi reescrito. Agora extensões de Animes, Mangás e Novels não entram mais em conflito de atualizações e possuem identificações separadas.
- **Opção de Qualidade Melhorada no Player:** O reprodutor de animes agora exibe as faixas de resoluções nativas (como 1080p, 720p) contidas no vídeo original. Elas aparecem perfeitamente integradas no visual oficial, no formato curto, na tela de Qualidades do Player.
- **Preparação para Filtro de Categorias:** Começamos a preparar as telas do aplicativo para filtrar corretamente as opções dependendo se a obra é anime, mangá ou novel, garantindo que tudo fique organizado na sua respectiva categoria.
- **Correções do Sistema:** Código limpo, erros de formatação de ações automáticas corrigidos, e melhor compatibilidade com as extensões originais do Aniyomi para que tudo rode de maneira mais leve e estável.

🇺🇸 **News, Improvements & Fixes (English):**
- **Native Extension Separation:** The entire extension system has been rewritten. Anime, Manga, and Novel extensions no longer conflict during updates and have properly separated identities.
- **Improved Player Quality Selection:** The anime player now reads and displays native video resolution tracks (such as 1080p, 720p) directly from the stream. These are perfectly integrated into the official UI with simplified names in the Quality Sheet.
- **Smart Categorization UI (WIP):** We've started laying the groundwork for library dialogs to properly filter categories depending on the media type (anime, manga, or novel) so things stay organized in their respective boundaries.
- **System Fixes:** Cleaned up code formatting, fixed automated action build errors, and improved compatibility with original Aniyomi extensions for a smoother and more stable experience.

## 0.20.4-Y42

🇧🇷 **Novidades e Correções (Português):**
- **Migração de Animes e Novels Independente:** Corrigido o erro crítico onde a tela de migração misturava as extensões. Agora o aplicativo filtra corretamente e exibe apenas extensões de animes na migração de animes, e extensões de mangás na migração de mangás (tanto na tela de seleção inicial quanto na tela de destino).
- **Navegação Inteligente no Explorar:** A aba "Explorar" agora altera seu título de forma dinâmica (ex: "Explorar Animes" ou "Explorar Mangás") dependendo da mídia selecionada no menu principal. As sub-abas também foram renomeadas e padronizadas para melhor usabilidade.
- **Correção no Carregamento de Fontes de Anime:** Ajuste no motor interno que impedia o reconhecimento de fontes de animes e novels como fontes online nativas. Isso resolve listas vazias e estabiliza o cache de downloads e migração.
- 💡 **Dica:** Para alternar entre as telas de Mangás, Animes e Novels, basta tocar no símbolo/ícone localizado no canto superior esquerdo do aplicativo!

🇺🇸 **Highlights & Fixes (English):**
- **Independent Anime & Novel Migration:** Fixed a critical bug where the migration screen mixed extensions. The app now correctly filters and displays only anime extensions for anime migration, and manga extensions for manga migration (both in the source selection and target selection screens).
- **Smart Explore Navigation:** The "Explore" tab now dynamically updates its title (e.g., "Explore Anime" or "Explore Manga") based on the media selected in the main menu. Sub-tabs were also unified and standardized for a smoother experience.
- **Anime Source Loading Fix:** Fixed an internal engine issue that prevented anime and novel sources from being recognized as native online sources. This resolves empty lists and stabilizes the download cache and migration target features.
- 💡 **Tip:** To switch between Manga, Anime, and Novel screens, simply tap the symbol/icon located in the top-left corner of the app!

## 0.20.4-Y41

🇧🇷 **Novidades e Aprimoramentos (Português):**
- **Suporte Nativo a Animes (Player MPV Integrado):** O Yomotsu agora é uma central completa de mangás, novels e animes! Assista a episódios com o reprodutor nativo MPV de alto desempenho com aceleração por hardware, suporte a múltiplas qualidades de vídeo, faixas de áudio e legendas.
- **Gerenciador de Downloads de Animes:** Baixe episódios completos diretamente pelo aplicativo para assistir offline onde estiver, com controle dedicado de fila e notificações de progresso.
- **Extensões de Animes Privadas:** Aba exclusiva de Fontes e Extensões para Animes no Explorar (base Anikku/Aniyomi), com instalação interna e privada sem necessidade de permissões perigosas no Android.
- **Perfil Yomotsu Repaginado e Melhorado:**
  - **20 Novos Avatares de Personagens de Animes:** Galeria de fotos de perfil com retratos oficiais em alta resolução (512x512) perfeitamente enquadrados para círculos (Sung Jin-woo, Gojo, Sukuna, Luffy, Zoro, Tanjiro, Nezuko, Naruto, Ichigo, Saitama, Eren, Levi, Frieren, Anya, Killua, Goku, Guts, Rimuru, Megumin, Alucard e Yomotsu Original).
  - **20 Banners Exclusivos (12 Cenários Autênticos + 8 Gradientes):** 12 paisagens cinematográficas de anime fiéis e de tirar o fôlego (Santuário Malevolente de Sukuna, Castelo Infinito de Demon Slayer, Thousand Sunny em mar aberto, Vale do Fim com as estátuas colossais, Vila da Folha com os rostos dos Hokages, Cometa Tiamat de Your Name, Céu de Tóquio, Portão das Sombras de Solo Leveling, Colina das Flores de Frieren, Muralha de Shinganshina, Trilhos no Mar de Chihiro e Jardim Secreto de Howl), além de 8 gradientes abstratos minimalistas.
  - **Bordas Dinâmicas de Rank:** Molduras de avatar exclusivas, incluindo o Rank Dinâmico que evolui e reflete a cor da sua patente e nível de leitura no Yomotsu.
  - **Métricas Completas de Leitura e Trackers:** Painel com estatísticas de capítulos lidos, episódios assistidos, tempo total dedicado (lendo e assistindo), mangás e animes salvos offline, obras concluídas e integração com serviços de rastreamento (AniList, MyAnimeList, Kitsu, etc.) exibindo itens monitorados e nota média universal (0-10 estrelas).
  - **42 Conquistas de Anime:** Desafios e conquistas temáticas exclusivas para desbloquear enquanto você consome seus mangás, novels e animes favoritos.
- **Internacionalização e Estabilidade:** Localização completa em Português do Brasil, Inglês e Espanhol, total compatibilidade com Android 15 e correções de inicialização e bibliotecas nativas C++.

🇺🇸 **Highlights & Improvements (English):**
- **Native Anime Support (Integrated MPV Player):** Yomotsu is now an all-in-one hub for manga, light novels, and anime! Watch episodes with high-performance MPV hardware acceleration, video quality selectors, multiple audio tracks, and subtitles.
- **Anime Download Manager:** Download full episodes directly within the app for offline viewing anywhere, featuring dedicated queue controls and background progress notifications.
- **Private Anime Extensions:** Dedicated Anime Sources and Extensions tab in Explore (powered by Anikku/Aniyomi ecosystem), featuring private internal installation without requiring invasive Android package installer permissions.
- **Revamped & Enhanced Yomotsu Profile:**
  - **20 New Anime Character Avatars:** High-definition character portraits (512x512) framed perfectly for circular profile pictures (Sung Jin-woo, Gojo, Sukuna, Luffy, Zoro, Tanjiro, Nezuko, Naruto, Ichigo, Saitama, Eren, Levi, Frieren, Anya, Killua, Goku, Guts, Rimuru, Megumin, Alucard, and Yomotsu Original).
  - **20 Exclusive Banners (12 Authentic Anime Landscapes + 8 Gradients):** 12 breathtaking widescreen anime landscapes (Sukuna's Malevolent Shrine, Demon Slayer's Infinity Castle, Thousand Sunny sailing the grand ocean, Valley of the End monumental statues, Hidden Leaf Village Hokage rock, Your Name's Tiamat Comet, Tokyo Sky, Solo Leveling's Shadow Gate, Frieren's Flower Hill, Wall Maria Shinganshina, Spirited Away Sea Train, and Howl's Secret Garden), alongside 8 minimalist high-contrast gradients.
  - **Dynamic Rank Borders:** Exclusive avatar borders, including a Dynamic Rank border that automatically shifts colors reflecting your current reading rank and progression in Yomotsu.
  - **Full Reading Metrics & Tracker Integration:** Comprehensive profile dashboard showing chapters read, episodes watched, total time spent reading & watching, offline downloaded titles, completed library entries, and full tracker sync (AniList, MyAnimeList, Kitsu, etc.) with tracked title counts and universal average score (0-10 stars).
  - **42 Anime Achievements:** Themed challenges and achievements to unlock as you read manga/novels and watch anime.
- **Localization & Stability:** Full multilingual localization across Brazilian Portuguese, English, and Spanish, Android 15 compatibility enhancements, and native C++ runtime fixes.

## 0.20.4-Y40

🇧🇷 **Novidades e Aprimoramentos (Português):**
- **Narração Neural de Novels (Edge TTS Instantâneo):** Adicionada narração com vozes neurais ultra-realistas (Francisca, Thalita e Antonio). Inclui inicialização instantânea da fala em menos de meio segundo, reprodução contínua em segundo plano (com tela apagada) e seletor rápido de voz e velocidade diretamente no leitor.
- **Limpeza Inteligente de Narração:** O sanitizador de texto remove automaticamente notas de tradução (`[NT: ...]`), divisores decorativos e símbolos repetidos para uma leitura fluida e natural.
- **Inpainting Neural de Alta Precisão (AOT-GAN):** Integração do motor AOT-GAN com aceleração nativa via NCNN C++, permitindo reconstrução de fundo sem rebarbas ou borrões em páginas de mangás e manhwas.
- **Tradução Mais Rápida e Resiliente:** Processamento assíncrono em pipeline para capítulos longos, reparo automático de respostas JSON de LLMs e correção pós-edição para português do Brasil em traduções via Gemini e OpenRouter.

🇺🇸 **Highlights & Improvements (English):**
- **Neural Novel Narration (Instant Edge TTS):** Added ultra-realistic studio voices (Francisca, Thalita, and Antonio) with sub-second instant speech start, seamless background playback with screen off, and a quick voice/speed selector in the novel reader bar.
- **Smart Speech Sanitizer:** Automatically filters translator notes (`[NT: ...]`), decorative dividers, and repeated punctuation marks for an uninterrupted storytelling experience.
- **High-Precision Neural Inpainting (AOT-GAN):** Integrated offline AOT-GAN neural reconstruction powered by native NCNN C++, delivering clean background restores behind erased speech bubbles without artifacts.
- **Faster, Resilient Translation:** Async pipeline dispatch for long chapters, automated LLM JSON syntax repair, and natural Brazilian Portuguese post-editing for Gemini and OpenRouter engines.

## 0.20.4-Y39

🇧🇷 **Novidades e Correções (Português):**
- **Tradução em Lotes Inteligentes (OpenRouter):** Para evitar congelamentos e erros de tempo limite (timeout) em capítulos gigantes, o OpenRouter agora processa o capítulo traduzindo em lotes rápidos de 10 páginas.
- **Encaixe Perfeito de Texto:** A hifenização automática (quebra de sílabas) foi ativada e a IA recebeu regras rígidas de concisão. Textos longos agora cabem melhor nos balões e usam fontes muito maiores e agradáveis de ler.
- **Limpeza de Balões Definitiva:** Aumentamos milimetricamente a margem da borracha de limpeza. Agora o aplicativo engole as últimas sombras do inglês sem morder as bordas dos balões.
- **Fim da Mescla de Balões Separados:** O agrupador do PaddleOCR foi ajustado para parar de "colar" textos de balões diferentes. As frases não vão mais aparecer vazando no espaço vazio entre dois balões.
- **Leitor de Novels Turbo & Cache:** A tradução das Novels (via Google Translate) agora agrupa blocos de 3.000 letras por vez (eliminando lentidão) e salva offline no celular! Fechar e abrir o app depois terá carregamento instantâneo.
- **Novels agora geram Histórico e Tracking:** O Leitor de Novels agora salva a leitura no "Continuar Lendo" e sincroniza o seu progresso automaticamente com o MyAnimeList/Anilist!
- **Leitor de Novel Preciso:** Corrigido o bug do Scroll Infinito. O capítulo seguinte só será marcado como lido quando seu título realmente chegar no topo da tela.

🇺🇸 **New Features & Fixes (English):**
- **Smart Batch Translation (OpenRouter):** To prevent freezing and timeout errors on massive chapters, OpenRouter now translates chapters in lightning-fast 10-page batches.
- **Perfect Text Fitting:** Auto-hyphenation is now enabled, and the AI was strictly prompted for conciseness. Long translated texts now fit beautifully into speech bubbles using much larger, readable fonts.
- **Definitive Bubble Cleanup:** We slightly increased the eraser's padding margin. The app now swallows those last lingering shadows of original English text without chewing into the bubble borders.
- **No More Merged Bubbles:** The PaddleOCR grouper was tweaked so it no longer wrongly glues text from separate bubbles. Translated dialogue will no longer float weirdly in the empty space between bubbles.
- **Turbo Novel Reader & Offline Caching:** Novel translation (via Google Translate) now processes massive 3,000-character chunks at once (fixing sluggishness) and saves them to local storage! Reopening the app later will load the novel instantly offline.
- **Novel History & Tracking Integration:** The Novel Reader finally registers read chapters in your History tab and automatically syncs your progress with MyAnimeList/Anilist!
- **Precise Mark-as-Read for Novels:** Fixed the Infinite Scroll bug. The next chapter will only be marked as read when its title actually touches the top of the screen.

## 0.20.4-Y36
**🚀 Mega Atualização de Performance e Tradução:**
- **PaddleOCR Turbinado e Sem Fechamentos (Otimização de RAM):** O motor de leitura agora consome METADE da memória RAM! Ler páginas longas de Webtoons não vai mais fazer o aplicativo fechar sozinho "do nada". O PaddleOCR é oficialmente o Leitor definitivo do app.
- **Leitura Lisa como Manteiga (Otimização de Zoom):** Transferimos a renderização dos balões traduzidos para a Placa de Vídeo (GPU) do celular. Agora você pode dar zoom-in, rolar e navegar nas páginas sem o aplicativo dar aquelas engasgadas chatas.
- **Fim das Rebarbas em Inglês:** A máscara (borracha) de limpeza do app ficou mais inteligente. Ela agora usa dilatação dinâmica para "engolir" completamente as sujeirinhas e bordas do texto original que antes ficavam vazando por trás da tradução.
- **Diagnóstico Transparente:** Antes, se a IA se recusasse a traduzir por causa de linguagem pesada, o app apenas dizia "Resposta Inválida". Agora nós escancaramos o erro mostrando o texto exato do bloqueio, revelando a verdadeira causa da falha.
- **Reconexão Automática Blindada:** A internet oscilou ou o servidor engasgou? O Yomotsu agora possui um sistema de *Retrys* que tenta reconectar sozinho, garantindo que nenhum balão fique para trás.

**💡 Dica da Comunidade:**
- Para aproveitar o PaddleOCR ao máximo e ler conteúdos com ação pesada ou conteúdo sensível (+18) sem tomar bloqueio do Google, recomendamos alterar o Motor de Tradução para o **OpenRouter** e usar IAs sem filtros (como Llama 3.3 ou Qwen 2.5)!

## 0.20.4-Y35
**✨ Novos Recursos:**
- **Sistema de Insígnias Transparente:** Substituição dos antigos rótulos de texto opacos por insígnias minimalistas com fundo transparente. O design foi redesenhado para garantir total destaque às capas dos mangás.
- **Identificação Gráfica Inteligente:** Implementação de um novo sistema automático que exibe o ícone nativo da extensão em alta resolução, acompanhado dinamicamente da bandeira do idioma correspondente em Emoji. (Ativado por padrão para todos os usuários).
- **Expansão do Catálogo de Temas:** Lançamento de 5 novos perfis de cores exclusivos e minimalistas para a interface do aplicativo (Onyx, Neon, Twilight, Orchid, Autumn).

**🛠️ Correções e Aprimoramentos:**
- **Refinamento Visual:** Ajuste na tabela de cores e contraste dos temas clássicos do aplicativo, alinhando-os perfeitamente com a nova diretriz de design.
- **Preto AMOLED:** Fundo da tela de carregamento (Splash Screen) em modo noturno agora utiliza a cor preta absoluta (pixels desligados) para telas OLED.
- **Estabilidade:** Aprimoramento no carregamento da interface da biblioteca e correções relacionadas à busca de metadados de fontes.

## 0.20.4-Y34
- **Repaginada Minimalista:** Atualização visual completa focada em leveza e imersão.
- **Lineless UI:** Remoção das linhas separadoras (dividers) nos menus, painéis e configurações, oferecendo uma rolagem contínua e imersiva para o tema escuro.
- **Abas "Pílula":** As abas tradicionais da biblioteca e de categorias foram substituídas por um indicador oval moderno no estilo Material You, removendo as linhas sublinhadas.
- **Grid Otimizado:** Capas na biblioteca agora possuem bordas arredondadas (12.dp) e os crachás de capítulos novos ganharam formato suave, com maior respiro entre as obras.
- **Navegação Premium:** Remoção dos textos de categorias inativas na barra de navegação inferior.
- **Ícone Adaptável:** Fim do fundo branco no ícone do aplicativo! O ícone agora é 100% nativo e se adapta à tela inicial preenchendo a forma com o fundo escuro do próprio logo.

## 0.20.4-Y29
- Correção definitiva do backup manual gerando arquivo vazio (0B): o botão "Criar" agora salva o backup compactado (.tachibk completo) diretamente na pasta padrão do aplicativo (/Yomotsu/autobackup/), usando a mesma rota confiável do backup automático.
- Adicionado botão de pasta no topo para exportação opcional com suporte unificado a diretórios e documentos SAF.
- Validação tolerante a falhas que previne exclusão indevida de backups já gravados.

## 0.20.4-Y28
- Adicionada barra de busca e pesquisa por título no Gerenciador da Nuvem Telegram para localizar obras rapidamente.
- Corrigido o erro na criação de backup manual que gerava arquivos zerados (0B) em aparelhos Samsung e outras marcas devido a restrições de permissão do sistema (SAF).
- Gravação direta e descarga forçada de dados no armazenamento ao gerar backups.

## 0.20.4-Y27
- Sincronização inteligente não destrutiva da Nuvem do Telegram, removendo apenas capítulos efetivamente apagados no grupo.
- Eliminação de deadlock de índice e prevenção de vazamento de arquivos temporários de cache.

## 0.20.4-Y23
- Suporte oficial completo a Light Novels com motor QuickJS e plugins LNReader.
- Nova aba "Fontes LN" na tela Explorar para navegar, instalar e atualizar extensões de novel.
- Leitor exclusivo de novels com rolagem infinita contínua (estilo manhwa) e leitura imersiva.
- Temas de leitura personalizados para novels: Sistema, Claro, Sépia e Preto Puro (AMOLED), além de ajuste de tamanho de fonte e formatação limpa de texto.
- Tradução de capítulos de novel em tempo real com o motor Google Tradutor, totalmente independente da tradução de mangás e manhwas.
- Download de capítulos de novel para leitura offline integrado diretamente à tela de detalhes da obra.
- Retomada inteligente de leitura da novel exatamente no primeiro capítulo não lido e marcação de leitura automática conforme a rolagem.
- Interface limpa e refinada: as ferramentas de OCR e tradução de balões de imagens foram restritas exclusivamente a mangás e manhwas, deixando a experiência de leitura de novels fluida e natural.

## 0.20.4-Y19
- Adicionado o detector experimental DBNet como opção desativada por padrão para aparelhos ARM64 e textos em inglês.
- O fluxo híbrido usa o DBNet para detectar e agrupar linhas, o ML Kit para reconhecer a página inteira uma vez e a associação geométrica para formar regiões coerentes antes da tradução.
- A máscara DBNet limpa somente as regiões associadas e traduzidas, com recorte e refinamento para remover melhor o texto sem preencher áreas sólidas indevidas.
- Falhas, resultados inválidos ou associações ambíguas retornam a página inteira com segurança ao OCR selecionado, sem misturar resultados parciais.
- O detector roda em processo separado para proteger o leitor contra falhas nativas.
- O modelo DBNet de aproximadamente 153 MB permanece fora do APK, é validado por SHA-256 e só exibe o aviso de download quando algum arquivo realmente precisa ser baixado.

## 0.20.4-Y17
- Melhorado o encaixe das traduções dentro da região segura dos balões, incluindo formatos ovais e arredondados.
- Melhorada a separação entre balões vizinhos para evitar a união indevida e a sobreposição de falas.
- Corrigido o agrupamento de parágrafos com várias linhas e fragmentos curtos da mesma fala.
- Melhorada a limpeza do texto original, preservando os contornos dos balões e os desenhos ao redor.
- Corrigida a escolha da cor de limpeza que podia criar faixas pretas dentro de balões claros em páginas escuras.
- Ajuste automático do tamanho da fonte mais consistente, aproveitando o espaço útil de cada balão.
- Gemini com comunicação REST, limite de 30 segundos e mensagens de erro HTTP mais claras, sem repetição automática de requisições.

## 0.20.4-Y16
- Renovadas as opções de fonte da tradução, mantendo Anime Ace como padrão e com migração automática da opção antiga.
- Adicionado auto-scroll ajustável nos modos Webtoon e Vertical contínuo.
- Ao ativar o auto-scroll, os controles do leitor são ocultados automaticamente; ao tocar na tela, a rolagem é pausada e os controles podem voltar a aparecer normalmente.

## 0.20.4-Y16-test1
- Renovadas as opções de fonte da tradução, com Anime Ace como padrão e migração automática da opção antiga.
- Adicionado auto-scroll ajustável nos modos Webtoon e Vertical contínuo, pausado ao primeiro toque.
- A tela do mangá e as barras do leitor agora usam uma cor de destaque extraída da capa, sem alterar as páginas ou os balões traduzidos.

## 0.20.4-Y15
- Adicionada tradução automática configurável individualmente em cada obra.
- Adicionado botão para traduzir manualmente os capítulos já baixados.
- Adicionado motor de tradução reserva quando o principal não consegue concluir a tradução.
- Downloads e arquivos temporários da tradução não aparecem mais na Galeria do celular.

## 0.20.4-Y14
- Corrigidos espaços invisíveis que permaneciam entre trechos traduzidos pelo PaddleOCR.
- Frases completas separadas pelo OCR agora podem compartilhar o mesmo balão quando a geometria da imagem confirma que pertencem à mesma fala.
- Balões próximos continuam separados para evitar a união indevida de diálogos diferentes.

## 0.20.4-Y13.1
- PaddleOCR usa o modelo oficial inglês PP-OCRv5 mobile e agora limita o detector a uma resolução adequada para celular, acelerando bastante a tradução de capítulos.
- Falas de larguras parecidas continuam agrupadas internamente, com a ordem original restaurada antes da tradução.
- ML Kit, limpeza de balões, agrupamento, renderização, memória, cache, glossário e Google Tradutor permanecem iguais à Y13.

## 0.20.4-Y13
- Adicionado o PaddleOCR como alternativa ao ML Kit, mantendo o Google Tradutor, o cache, a memória e as opções existentes.
- Melhorado o agrupamento de linhas próximas de uma mesma fala no PaddleOCR.
- Removida a hifenização automática: as linhas agora quebram apenas entre palavras.
- A limpeza dos balões usa cantos arredondados para evitar abas brancas laterais.
- Corrigida a detecção de balões circulares grandes com pouco texto, comum em pensamentos e narrações; painéis largos continuam excluídos.

## 0.20.4-Y13-test8
- Corrigida a detecção de balões circulares grandes com pouco texto, comum em pensamentos e narrações.
- Painéis largos continuam fora da detecção para não receber tradução como se fossem balões.

## 0.20.4-Y13-test3
- PaddleOCR volta a reunir linhas próximas da mesma fala quando a primeira linha ainda não terminou a frase.
- O texto traduzido não recebe mais hifenização automática dentro dos balões.
- A limpeza de balões detectados usa cantos arredondados para evitar abas brancas laterais.

## 0.20.4-Y12
- O glossário agora aceita vários termos de uma vez e diferencia termos, nomes, títulos e técnicas.
- Nomes e outros itens protegidos são preservados também no ML Kit e no Google Tradutor.
- Correções manuais passam a ser aprendidas por obra e reaproveitadas nos capítulos seguintes.
- Glossário, memória e cache usam uma estrutura versionada, preparada para futura importação, exportação e backup.
- A tradução funciona em primeiro plano e mostra capítulo, página e etapa atual na notificação.
- Filas grandes continuam capítulo a capítulo, inclusive quando muitos downloads são solicitados de uma vez.
- A exportação experimental de CBZ traduzido foi removida; a tradução no leitor e o JSON editável continuam disponíveis.

## 0.20.4-Y11
- Reforçada a segurança da atualização por cima da Y10.1, preservando pacote e assinatura do aplicativo.
- Removido o último funding herdado do Mihon e isolado o atualizador nos canais do Yomotsu.
- Adicionados testes obrigatórios de regressão e validação da assinatura antes de disponibilizar novos APKs.
- Falas que claramente continuarem em inglês recebem uma segunda tentativa automática; se ainda falharem, o original permanece visível para correção manual.
- Traduções longas passam a usar um modo de encaixe seguro, com mais espaço útil e redução adicional da fonte somente quando necessário.

## 0.20.4-Y10
- Melhorada a detecção e a organização das falas para traduzir mais textos sem separar frases relacionadas.
- Ajustados o tamanho das páginas, o encaixe, a centralização e o tamanho das traduções nos balões.
- Adicionada edição por toque longo: traduções prontas podem ser corrigidas e textos restantes em inglês podem ser traduzidos manualmente.
- O texto original em inglês só é coberto depois que a tradução manual é salva.
- Corrigidos os botões Salvar e Retraduzir do editor e publicados os links de ajuda e privacidade.

## 0.20.4-Y9.1
- Adicionada uma margem interna segura para manter a tradução dentro dos balões arredondados.
- Reduzido o tamanho do texto quando necessário, sem cortar linhas que não cabem.
- Removida a hifenização automática que separava palavras no meio.
- Capítulos já traduzidos pela primeira Y9 recebem o encaixe seguro sem apagar a tradução.

## 0.20.4-Y9
- Limpeza ampliada e sem rotação para apagar melhor o texto original dos balões.
- Detecção do espaço claro ao redor do texto para aproveitar melhor o interior do balão.
- Tamanho, quebra de linha, alinhamento e centralização da tradução ajustados automaticamente.
- Compatibilidade mantida com capítulos traduzidos em versões anteriores.

## 0.20.4-Y8
- Adicionada continuidade entre os diálogos durante a tradução.
- Gemini e OpenRouter agora consideram o contexto das falas anteriores.
- Melhorada a consistência de nomes, termos e pronomes ao longo do capítulo.
- Cache e glossário continuam sendo respeitados durante a tradução contextual.

## 0.20.4-Y7
- Adicionado cache inteligente e persistente de traduções por obra e idioma.
- Traduções já conhecidas podem ser reutilizadas sem traduzir novamente.
- Cache integrado ao DeepL, Google Translate, ML Kit e Gemini com preservação de contexto.
- Melhorada a prioridade do glossário para respeitar termos salvos mesmo com diferenças de maiúsculas e pontuação.
- Mantida a tradução contextual quando há conteúdo novo para evitar perda de qualidade.

## 0.20.4-Y6
- Adicionada memória de tradução persistente.
- Adicionado glossário de tradução por obra.
- Glossário integrado aos tradutores disponíveis no Yomotsu.
- Melhorada a consistência das traduções de nomes e termos recorrentes.

## 0.20.4-Y5
- Adicionado o DeepL como opção de tradução.
- Incluído suporte às APIs Free e Pro por chave configurada pelo usuário.

<!-- Adicione novas versões acima desta linha no formato:
## 0.20.4-Y10
- Primeira novidade.
- Segunda novidade.
-->

## 0.20.4-Y33
- **Novo Domínio Oficial:** Todos os links internos do aplicativo (Ajuda, Privacidade, Guias) foram atualizados para apontar para o novo site profissional: yomotsu-app.github.io.

## 0.20.4-Y32
- **Correção de Links:** Corrigido o erro 404 ao tentar abrir a Política de Privacidade e a Ajuda de dentro do aplicativo (links agora apontam para o novo repositório Yomotsu-Oficial).
- **Sistema de Progressão:** (Herdado da Y31) Adicionado Perfil interativo com Sistema de Níveis, XP (ganho ao ler e baixar capítulos), 100 conquistas únicas, títulos equipáveis e cache permanente para Avatares e Banners.

## 0.20.4-Y31
- **Sistema de Progressão:** Adicionado um Perfil interativo com Sistema de Níveis e XP (ganho ao ler e baixar capítulos).
- **Conquistas e Troféus:** 100 conquistas únicas categorizadas em Bronze, Prata, Ouro e Rubi baseadas no histórico do leitor.
- **Títulos Equipáveis:** Desbloqueie títulos sombrios e equipe-os no seu perfil conforme sobe de nível.
- **Identidade e Imagens:** Troque seu Nickname, escolha um Avatar e edite o Banner do perfil com cache permanente no app.
- **Notificações:** Alertas e notificações nativas do Android ao desbloquear conquistas durante leituras ou em segundo plano.

## 0.20.4-Y30
- **Nova Funcionalidade:** Introdução da **Biblioteca Telegram**, permitindo que o Yomotsu use a Nuvem do Telegram como fonte ilimitada, privada e gratuita para armazenamento e leitura de obras, integrando com o Leitor Contínuo.
- **Melhoria:** Correção da navegação inter-capítulos da Nuvem Telegram, separando o cache de descompressão utilizando hashes individuais, resolvendo bugs de mesclagem de páginas e imagens.
- **Integração:** Adicionado botão de Sincronização na interface que varre o canal automaticamente detectando exclusões feitas fora do Yomotsu.
- **Fix:** Oculta nome de pacotes (`eu.kanade...`) na tela de detalhes da Biblioteca Telegram tornando a apresentação idêntica às Extensões Nativas.
